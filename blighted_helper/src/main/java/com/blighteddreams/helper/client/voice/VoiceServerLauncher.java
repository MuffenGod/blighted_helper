package com.blighteddreams.helper.client.voice;

import com.blighteddreams.helper.BlightedHelper;
import com.blighteddreams.helper.VoiceConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatically starts the local Qwen3-TTS voice server (design/tools/voice-server/server.py) in the background
 * when Minecraft starts or when the player joins a world, and stops the child process when the game closes.
 */
@EventBusSubscriber(modid = BlightedHelper.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VoiceServerLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger("blighted_enhancements/voice-launcher");
    private static final HttpClient PING_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(1200))
            .build();

    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static volatile Process managedProcess;
    private static volatile long startedAtMs;
    private static volatile boolean notifiedReady;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = managedProcess;
            if (p != null && p.isAlive()) {
                LOGGER.info("Stopping auto-started Emily voice server (PID {})", p.pid());
                p.destroy();
                try {
                    if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                        p.destroyForcibly();
                    }
                } catch (InterruptedException ignored) {
                    p.destroyForcibly();
                }
            }
        }, "blighted-voice-shutdown"));
    }

    private VoiceServerLauncher() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(VoiceServerLauncher::ensureRunningAsync);
    }

    /** True if the server process was recently started by us and may still be loading its model / CUDA graphs. */
    public static boolean isWarmingUp() {
        Process p = managedProcess;
        return p != null && p.isAlive() && (System.currentTimeMillis() - startedAtMs) < 90_000L;
    }

    /** Non-blocking check: if voice & autoStart are enabled and the local server is not reachable, spawn it. */
    public static void ensureRunningAsync() {
        if (!VoiceConfig.enabled() || !VoiceConfig.autoStart()) {
            return;
        }
        String url = VoiceConfig.serverUrl();
        if (!isLocalUrl(url)) {
            return;
        }
        if (!CHECKING.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                if (isServerAlive(url)) {
                    return;
                }
                Process existing = managedProcess;
                if (existing != null && existing.isAlive()) {
                    return;
                }
                startProcess(url);
            } catch (Exception e) {
                LOGGER.warn("Auto-start voice server check failed: {}", e.toString());
            } finally {
                CHECKING.set(false);
            }
        });
    }

    private static boolean isLocalUrl(String url) {
        return url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost");
    }

    private static boolean isServerAlive(String baseUrl) {
        try {
            String clean = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            HttpRequest req = HttpRequest.newBuilder(URI.create(clean + "/health"))
                    .timeout(Duration.ofMillis(1200))
                    .GET()
                    .build();
            HttpResponse<String> resp = PING_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static void startProcess(String baseUrl) {
        Path ttsHome = resolveTtsHome();
        Path pythonExe = ttsHome.resolve("venv").resolve("Scripts").resolve("python.exe");
        if (!Files.isRegularFile(pythonExe)) {
            LOGGER.warn("Cannot auto-start Emily voice server: python not found at {}", pythonExe);
            return;
        }
        Path serverScript = resolveServerScript();
        if (serverScript == null || !Files.isRegularFile(serverScript)) {
            LOGGER.warn("Cannot auto-start Emily voice server: server.py not found");
            return;
        }
        int port = extractPort(baseUrl, 8791);
        Path cacheDir = ttsHome.resolve("line_cache");
        Path logFile = FMLPaths.GAMEDIR.get().resolve("logs").resolve("emily-voice-server.log");
        try {
            Files.createDirectories(cacheDir);
            if (logFile.getParent() != null) {
                Files.createDirectories(logFile.getParent());
            }
            ProcessBuilder pb = new ProcessBuilder(
                    pythonExe.toString(),
                    "-I",
                    serverScript.toString(),
                    "--port", String.valueOf(port),
                    "--cache-dir", cacheDir.toString()
            );
            pb.directory(serverScript.getParent().toFile());
            Map<String, String> env = pb.environment();
            env.put("BD_TTS_HOME", ttsHome.toString());
            env.put("HF_HOME", ttsHome.resolve("hf_cache").toString());
            env.put("HF_HUB_OFFLINE", "1");
            env.put("PYTHONIOENCODING", "utf-8");
            if (!VoiceConfig.apiKey().isEmpty()) {
                env.put("VOICE_SERVER_KEY", VoiceConfig.apiKey());
            }
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

            managedProcess = pb.start();
            startedAtMs = System.currentTimeMillis();
            notifiedReady = false;
            LOGGER.info("Auto-started Emily voice server (PID {}) -> {} (log: {})",
                    managedProcess.pid(), baseUrl, logFile);

            // Poll in background to log & notify when ready
            pollUntilReady(baseUrl);
        } catch (Exception e) {
            LOGGER.warn("Failed to launch Emily voice server: {}", e.toString());
        }
    }

    private static void pollUntilReady(String baseUrl) {
        String clean = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        for (int i = 0; i < 45; i++) {
            try {
                Thread.sleep(2000L);
                Process p = managedProcess;
                if (p != null && !p.isAlive()) {
                    LOGGER.warn("Emily voice server exited early with code {}", p.exitValue());
                    return;
                }
                HttpRequest req = HttpRequest.newBuilder(URI.create(clean + "/health"))
                        .timeout(Duration.ofMillis(1500))
                        .GET()
                        .build();
                HttpResponse<String> resp = PING_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200 && resp.body().contains("\"ready\"")) {
                    if (!notifiedReady) {
                        notifiedReady = true;
                        LOGGER.info("Emily voice server is READY at {}", baseUrl);
                        HelperVoice.chat(Component.literal("[Emily Voice] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                                .append(Component.literal("Voice synthesizer warmed up and ready.").withStyle(ChatFormatting.GRAY)));
                    }
                    return;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private static Path resolveTtsHome() {
        String cfg = VoiceConfig.ttsHome();
        if (!cfg.isEmpty()) {
            return Path.of(cfg);
        }
        String env = System.getenv("BD_TTS_HOME");
        if (env != null && !env.isBlank()) {
            return Path.of(env.trim());
        }
        return Path.of(System.getProperty("user.home"), "tts");
    }

    private static Path resolveServerScript() {
        Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                gameDir.resolve("design/tools/voice-server/server.py"),
                gameDir.getParent() != null ? gameDir.getParent().resolve("design/tools/voice-server/server.py") : gameDir,
                // a sibling instance (e.g. "Blighted Dreams Main") uses the server that ships with the main "Blighted Dreams" instance
                gameDir.getParent() != null ? gameDir.getParent().resolve("Blighted Dreams/design/tools/voice-server/server.py") : gameDir
        );
        for (Path c : candidates) {
            if (Files.isRegularFile(c)) {
                return c;
            }
        }
        return null;
    }

    private static int extractPort(String url, int fallback) {
        try {
            URI u = URI.create(url);
            return u.getPort() > 0 ? u.getPort() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}

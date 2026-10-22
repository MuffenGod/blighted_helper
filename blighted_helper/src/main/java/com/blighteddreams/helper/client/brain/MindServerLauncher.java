package com.blighteddreams.helper.client.brain;

import com.blighteddreams.helper.BlightedHelper;
import com.blighteddreams.helper.BrainConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts Emily's local mind (HELPER_BRAIN_PLAN 3b): a llama.cpp {@code llama-server} sidecar running MiniCPM5-2B
 * Q4_K_M on 127.0.0.1:8792, with the flags the mind-eval bake-off used. Only for {@code provider = "local"}.
 * The process is a child of the game and is stopped when the game closes; a crash in it never takes the game down.
 */
@EventBusSubscriber(modid = BlightedHelper.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MindServerLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger("blighted_enhancements/mind-launcher");
    private static final HttpClient PING = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(1000))
            .build();
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static volatile Process managed;
    private static volatile boolean ready;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = managed;
            if (p != null && p.isAlive()) {
                LOGGER.info("Stopping Emily's mind (PID {})", p.pid());
                p.destroy();
                try {
                    if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                        p.destroyForcibly();
                    }
                } catch (InterruptedException ignored) {
                    p.destroyForcibly();
                }
            }
        }, "blighted-mind-shutdown"));
    }

    private MindServerLauncher() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(MindServerLauncher::ensureRunningAsync);
    }

    /** True once the local model answered its health check. */
    public static boolean isReady() {
        Process p = managed;
        if (ready && p != null && !p.isAlive()) {
            // the sidecar died mid-game: report not ready so the next request restarts it
            ready = false;
            LOGGER.warn("Emily's mind process exited (code {}); it will be restarted on the next request", p.exitValue());
        }
        return ready;
    }

    /** Non-blocking: if the local provider is chosen and the sidecar is not answering, start it. */
    public static void ensureRunningAsync() {
        if (!BrainConfig.enabled() || !BrainConfig.mindAutoStart() || !"local".equals(BrainConfig.provider())) {
            return;
        }
        if (!CHECKING.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                if (healthy()) {
                    ready = true;
                    return;
                }
                Process p = managed;
                if (p != null && p.isAlive()) {
                    return;
                }
                start();
            } catch (Exception e) {
                LOGGER.warn("Mind server check failed: {}", e.toString());
            } finally {
                CHECKING.set(false);
            }
        });
    }

    private static String root() {
        String url = BrainConfig.baseUrl().isEmpty() ? BrainConfig.preset("local").baseUrl() : BrainConfig.baseUrl();
        URI u = URI.create(url);
        return u.getScheme() + "://" + u.getHost() + ":" + (u.getPort() > 0 ? u.getPort() : 8792);
    }

    private static boolean healthy() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(root() + "/health")).timeout(Duration.ofMillis(1200)).GET().build();
            return PING.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static void start() throws Exception {
        Path home = Path.of(BrainConfig.mindHome());
        String backend = BrainConfig.mindBackend();
        Path binDir = home.resolve("bin").resolve("cuda".equals(backend) ? "cuda13" : backend).resolve("unpacked");
        Path exe = binDir.resolve("llama-server.exe");
        Path model = Path.of(BrainConfig.mindModel());
        if (!model.isAbsolute()) {
            model = home.resolve("models").resolve(BrainConfig.mindModel());
        }
        if (!Files.isRegularFile(exe) || !Files.isRegularFile(model)) {
            LOGGER.warn("Cannot start Emily's mind: llama-server at {} or model at {} is missing", exe, model);
            return;
        }
        URI u = URI.create(root());
        List<String> args = new ArrayList<>(List.of(exe.toString(), "-m", model.toString(),
                "--host", "127.0.0.1", "--port", String.valueOf(u.getPort()),
                "-c", "8192", "-np", "2", "-fa", "on", "-ctk", "q8_0", "-ctv", "q8_0", "--jinja",
                "--no-webui", "--cache-reuse", "256",
                "--alias", "mind"));
        int layers = BrainConfig.mindGpuLayers();
        if (layers > 0) {
            args.addAll(List.of("-ngl", String.valueOf(layers)));
        } else {
            args.addAll(List.of("-ngl", "0", "-dev", "none", "-t", "4", "-tb", "4"));
        }
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(binDir.toFile());
        // through the environment: Windows argument quoting would strip the JSON's quotes
        pb.environment().put("LLAMA_ARG_CHAT_TEMPLATE_KWARGS", "{\"enable_thinking\": false}");
        if ("cuda".equals(backend)) {
            Path cudart = home.resolve("bin").resolve("cudart13").resolve("unpacked");
            pb.environment().merge("PATH", cudart.toString(), (old, add) -> add + ";" + old);
        }
        Path log = FMLPaths.GAMEDIR.get().resolve("logs").resolve("emily-mind-server.log");
        Files.createDirectories(log.getParent());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(log.toFile()));
        managed = pb.start();
        ready = false;
        LOGGER.info("Started Emily's mind (PID {}): {} on {} (log {})", managed.pid(), model.getFileName(), root(), log);
        for (int i = 0; i < 90 && managed.isAlive(); i++) {
            Thread.sleep(1000L);
            if (healthy()) {
                ready = true;
                LOGGER.info("Emily's mind is ready after {} s", i + 1);
                return;
            }
        }
        LOGGER.warn("Emily's mind did not become ready (alive={}); see {}", managed.isAlive(), log);
    }
}

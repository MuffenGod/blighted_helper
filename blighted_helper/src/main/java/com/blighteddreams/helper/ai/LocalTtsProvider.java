package com.blighteddreams.helper.ai;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Talks to the local voice server (design/tools/voice-server/server.py): an OpenAI-style
 * {@code POST /v1/audio/speech} that streams raw 16-bit mono PCM. Closing the connection cancels the synthesis.
 */
public final class LocalTtsProvider implements TtsProvider {
    private static final int SAMPLE_RATE_FALLBACK = 24000;

    private final String baseUrl;
    private final String apiKey;
    private final int chunkSize;
    private final boolean cacheLines;
    private final HttpClient http;
    private final ExecutorService pool;
    private final Set<Task> active = ConcurrentHashMap.newKeySet();
    private volatile Health health = new Health(false, "not checked yet");

    /**
     * @param baseUrl    for example {@code http://127.0.0.1:8791}
     * @param apiKey     bearer token, or empty for none
     * @param chunkSize  codec steps per streamed chunk (12 = one second); smaller = earlier first sound
     * @param cacheLines let the server keep finished lines on disk and replay repeats without the GPU
     */
    public LocalTtsProvider(String baseUrl, String apiKey, int chunkSize, boolean cacheLines) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.chunkSize = chunkSize;
        this.cacheLines = cacheLines;
        this.pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "blighted-voice-http");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // the server is uvicorn: plain HTTP/1.1, no h2c upgrade
                .connectTimeout(Duration.ofSeconds(2))
                .executor(pool)
                .build();
    }

    @Override
    public String name() {
        return "local Qwen3-TTS at " + baseUrl;
    }

    @Override
    public boolean isAvailable() {
        return health.ready();
    }

    public Health lastHealth() {
        return health;
    }

    @Override
    public void checkHealth(Consumer<Health> callback) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                .timeout(Duration.ofSeconds(2)).GET().build();
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((resp, err) -> {
            Health h;
            if (err != null) {
                h = new Health(false, "cannot reach " + baseUrl + " (" + rootCause(err).getClass().getSimpleName() + ")");
            } else {
                String status = jsonString(resp.body(), "status");
                h = switch (status == null ? "" : status) {
                    case "ready" -> new Health(true, "ready");
                    case "loading" -> new Health(false, "the voice model is still loading");
                    case "error" -> new Health(false, "the voice server failed to start: " + jsonString(resp.body(), "error"));
                    default -> new Health(false, "unexpected answer (HTTP " + resp.statusCode() + ")");
                };
            }
            health = h;
            callback.accept(h);
        });
    }

    @Override
    public Handle speak(Request request, Sink sink) {
        Task task = new Task(request, sink);
        active.add(task);
        task.start();
        return task;
    }

    @Override
    public void close() {
        for (Task t : active) {
            t.cancel();
        }
        pool.shutdownNow();
    }

    private volatile Runnable wakeupHook;

    public void setWakeupHook(Runnable hook) {
        this.wakeupHook = hook;
    }

    private final class Task implements Handle {
        private final Request request;
        private final Sink sink;
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile boolean cancelled;
        private volatile CompletableFuture<HttpResponse<InputStream>> future;
        private volatile InputStream stream;
        private int attempts = 0;

        Task(Request request, Sink sink) {
            this.request = request;
            this.sink = sink;
        }

        void start() {
            attempts++;
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/audio/speech"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body(), StandardCharsets.UTF_8));
            if (!apiKey.isEmpty()) {
                b.header("Authorization", "Bearer " + apiKey);
            }
            CompletableFuture<HttpResponse<InputStream>> f = http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofInputStream());
            future = f;
            // Hop off the HttpClient's own thread before doing blocking reads.
            f.whenComplete((resp, err) -> {
                try {
                    pool.execute(() -> run(resp, err));
                } catch (RuntimeException rejected) { // provider closed
                    finished.set(true);
                    active.remove(this);
                }
            });
        }

        private String body() {
            StringBuilder sb = new StringBuilder(128 + request.text().length());
            sb.append("{\"model\":\"qwen3-tts\",\"response_format\":\"pcm\",\"input\":");
            quote(sb, request.text());
            sb.append(",\"voice\":");
            quote(sb, request.voice());
            if (request.mood() != null && !request.mood().isBlank()) {
                sb.append(",\"mood\":");
                quote(sb, request.mood());
            }
            sb.append(",\"chunk_size\":").append(chunkSize);
            if (cacheLines) {
                sb.append(",\"cache\":true");
            }
            return sb.append('}').toString();
        }

        private void run(HttpResponse<InputStream> resp, Throwable err) {
            boolean retrying = false;
            try {
                if (cancelled) {
                    closeQuietly(resp == null ? null : resp.body());
                    return;
                }
                if (err != null) {
                    Runnable hook = wakeupHook;
                    if (hook != null && attempts == 1) {
                        hook.run();
                    }
                    if (hook != null && attempts <= 22 && !cancelled) {
                        health = new Health(false, "starting local voice server...");
                        retrying = sleepAndRetry(2000L);
                        if (retrying) return;
                    }
                    Throwable cause = rootCause(err);
                    health = new Health(false, "cannot reach " + baseUrl + " (" + cause.getClass().getSimpleName() + ")");
                    fail(cause instanceof Exception e ? e : new IOException(cause));
                    return;
                }
                if (resp.statusCode() != 200) {
                    String detail = readSmall(resp.body());
                    boolean loading = resp.statusCode() == 503;
                    health = new Health(false, loading ? "the voice model is still loading" : "HTTP " + resp.statusCode());
                    if (loading && attempts <= 25 && !cancelled) {
                        retrying = sleepAndRetry(2000L);
                        if (retrying) return;
                    }
                    fail(new IOException("voice server answered HTTP " + resp.statusCode() + ": " + detail));
                    return;
                }
                health = new Health(true, "ready");
                int rate = resp.headers().firstValue("X-Sample-Rate").map(LocalTtsProvider::parseIntOr24k).orElse(SAMPLE_RATE_FALLBACK);
                stream = resp.body();
                if (cancelled) {
                    return;
                }
                sink.onFormat(rate);

                byte[] buf = new byte[8192];
                int carry = 0; // a trailing odd byte is held back so the sink only ever sees whole samples
                int n;
                while (!cancelled && (n = stream.read(buf, carry, buf.length - carry)) >= 0) {
                    int total = carry + n;
                    int usable = total & ~1;
                    if (usable > 0 && !cancelled) {
                        sink.onPcm(buf, 0, usable);
                    }
                    carry = total - usable;
                    if (carry == 1) {
                        buf[0] = buf[usable];
                    }
                }
                if (!cancelled && finished.compareAndSet(false, true)) {
                    sink.onComplete();
                }
            } catch (Exception e) {
                if (!cancelled) {
                    fail(e);
                }
            } catch (Throwable t) { // never let an Error escape silently from a pool thread
                if (!cancelled) {
                    fail(new IOException(t));
                }
            } finally {
                closeQuietly(stream);
                if (!retrying) {
                    finished.set(true);
                    active.remove(this);
                }
            }
        }

        private boolean sleepAndRetry(long ms) {
            try {
                Thread.sleep(ms);
                if (!cancelled) {
                    start();
                    return true;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return false;
        }

        private void fail(Exception e) {
            if (!cancelled && finished.compareAndSet(false, true)) {
                sink.onError(e);
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            CompletableFuture<?> f = future;
            if (f != null) {
                f.cancel(true);
            }
            closeQuietly(stream); // dropping the connection is what tells the server to stop
            finished.set(true);
            active.remove(this);
        }

        @Override
        public boolean isFinished() {
            return finished.get();
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** Reads {@code "key":"value"} out of the small, flat JSON our own server returns. Not a general JSON parser. */
    static String jsonString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int i = json.indexOf(marker);
        if (i < 0) {
            return null;
        }
        int start = i + marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    private static int parseIntOr24k(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return SAMPLE_RATE_FALLBACK;
        }
    }

    private static String readSmall(InputStream in) {
        try (in) {
            return new String(in.readNBytes(512), StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        } catch (IOException e) {
            return "";
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
                // closing is best effort
            }
        }
    }

    private static Throwable rootCause(Throwable t) {
        while ((t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}

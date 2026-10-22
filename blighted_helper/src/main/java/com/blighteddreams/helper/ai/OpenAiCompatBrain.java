package com.blighteddreams.helper.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streams a chat from any server that speaks the OpenAI chat-completions format: Groq, Google Gemini's OpenAI-compatible
 * endpoint, a local Ollama, or anything else (see {@code BrainPresets} in the client config). Reads
 * {@code choices[0].delta.content} only, so reasoning output (Groq's "reasoning", Ollama's "thinking") is never spoken.
 *
 * If the first model is rate limited or overloaded before it has produced any text, the request is retried once on
 * {@code fallbackModel}. The API key is only ever sent in the Authorization header and is never part of any message.
 */
public final class OpenAiCompatBrain implements BrainProvider {
    private static final int ERROR_BODY_CHARS = 600;

    private final String label;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final String fallbackModel;
    private final double temperature;
    private final int maxTokens;
    private final String reasoningEffort;
    private final HttpClient http;
    private final ExecutorService pool;
    private final Set<Task> active = ConcurrentHashMap.newKeySet();

    /**
     * @param label           shown to the player, for example "Groq"
     * @param baseUrl         up to and excluding /chat/completions, for example {@code https://api.groq.com/openai/v1}
     * @param apiKey          bearer token; may be empty (a local Ollama ignores it)
     * @param model           model id
     * @param fallbackModel   model to retry on when the first is rate limited or down; empty for none
     * @param reasoningEffort sent as {@code reasoning_effort} when not empty (for models that think); empty for none
     */
    public OpenAiCompatBrain(String label, String baseUrl, String apiKey, String model, String fallbackModel,
                             double temperature, int maxTokens, String reasoningEffort) {
        this.label = label;
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.endpoint = base + "/chat/completions";
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.fallbackModel = fallbackModel == null ? "" : fallbackModel.trim();
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.trim();
        this.pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "blighted-brain-http");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .executor(pool)
                .build();
    }

    @Override
    public String name() {
        return label + " (" + model + ")";
    }

    @Override
    public Handle chat(List<ChatMessage> messages, Listener listener) {
        Task t = new Task(List.copyOf(messages), listener);
        active.add(t);
        pool.execute(t);
        return t;
    }

    @Override
    public void close() {
        for (Task t : active) {
            t.cancel();
        }
        pool.shutdownNow();
    }

    /** What one HTTP attempt ended with. */
    private record Result(Exception error, boolean sentText, boolean retryable, boolean badReasoning) {
        static Result ok() {
            return new Result(null, true, false, false);
        }
    }

    private final class Task implements Handle, Runnable {
        private final List<ChatMessage> messages;
        private final Listener listener;
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile boolean cancelled;
        private volatile Thread worker;
        private volatile InputStream stream;

        Task(List<ChatMessage> messages, Listener listener) {
            this.messages = messages;
            this.listener = listener;
        }

        @Override
        public void run() {
            worker = Thread.currentThread();
            try {
                Result r = attemptModel(model);
                if (r.error() != null && !r.sentText() && r.retryable() && !fallbackModel.isEmpty() && !fallbackModel.equals(model)) {
                    r = attemptModel(fallbackModel);
                }
                if (cancelled) {
                    return;
                }
                Exception failure = r.error();
                if (failure != null) {
                    terminal(() -> listener.onError(failure));
                } else {
                    terminal(listener::onComplete);
                }
            } catch (Throwable t) { // never let an Error vanish on a pool thread
                if (!cancelled) {
                    terminal(() -> listener.onError(t instanceof Exception e ? e : new IOException(t)));
                }
            } finally {
                closeQuietly(stream);
                finished.set(true);
                active.remove(this);
            }
        }

        /** One model, with reasoning_effort; if the server answers 400 because that model does not take it, once more without. */
        private Result attemptModel(String modelId) {
            Result r = attempt(modelId, reasoningEffort);
            if (r.error() != null && !r.sentText() && r.badReasoning() && !reasoningEffort.isEmpty()) {
                r = attempt(modelId, "");
            }
            return r;
        }

        private Result attempt(String modelId, String effort) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofSeconds(30)) // until the response headers arrive
                        .POST(HttpRequest.BodyPublishers.ofString(body(modelId, effort), StandardCharsets.UTF_8));
                if (!apiKey.isEmpty()) {
                    b.header("Authorization", "Bearer " + apiKey);
                }
                HttpResponse<InputStream> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
                stream = resp.body();
                if (cancelled) {
                    return Result.ok();
                }
                int status = resp.statusCode();
                if (status != 200) {
                    String text = readSmall(resp.body());
                    boolean retry = status == 408 || status == 429 || status >= 500;
                    boolean badReasoning = status == 400 && text.toLowerCase().contains("reasoning");
                    return new Result(new IOException(friendly(status, modelId, text)), false, retry, badReasoning);
                }
                String type = resp.headers().firstValue("content-type").orElse("").toLowerCase();
                return type.contains("event-stream") ? readEvents(resp.body()) : readWholeBody(resp.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Result(new IOException("interrupted"), false, false, false);
            } catch (IOException e) {
                // connection refused, timeouts, dropped streams: readable, and worth the fallback only before any text
                return new Result(new IOException("Could not reach " + label + " (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")"), false, false, false);
            }
        }

        private Result readEvents(InputStream in) throws IOException {
            boolean sent = false;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while (!cancelled && (line = br.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue; // comments, event names, blank separators
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    if (data.equals("[DONE]")) {
                        break;
                    }
                    Object json;
                    try {
                        json = MiniJson.parse(data);
                    } catch (IllegalArgumentException malformed) {
                        continue; // a chunk we cannot read is not worth failing the whole answer for
                    }
                    String err = MiniJson.string(json, "error", "message");
                    if (err != null) {
                        return new Result(new IOException("Error from " + label + ": " + err), sent, !sent, false);
                    }
                    String piece = MiniJson.string(json, "choices", 0, "delta", "content");
                    if (piece != null && !piece.isEmpty() && !cancelled) {
                        sent = true;
                        listener.onToken(piece);
                    }
                }
            } catch (IOException e) {
                if (cancelled) {
                    return Result.ok();
                }
                if (sent) {
                    return new Result(new IOException("The connection to " + label + " dropped mid-answer"), true, false, false);
                }
                throw e;
            }
            return sent ? Result.ok() : new Result(new IOException("Empty answer from " + label), false, true, false);
        }

        /** A server that ignored stream=true and answered with one JSON document. */
        private Result readWholeBody(InputStream in) throws IOException {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try {
                Object json = MiniJson.parse(body);
                String err = MiniJson.string(json, "error", "message");
                if (err != null) {
                    return new Result(new IOException("Error from " + label + ": " + err), false, true, false);
                }
                String text = MiniJson.string(json, "choices", 0, "message", "content");
                if (text == null || text.isEmpty()) {
                    return new Result(new IOException("Empty answer from " + label), false, true, false);
                }
                if (!cancelled) {
                    listener.onToken(text);
                }
                return Result.ok();
            } catch (IllegalArgumentException e) {
                return new Result(new IOException("Unreadable answer from " + label), false, false, false);
            }
        }

        private String body(String modelId, String effort) {
            StringBuilder sb = new StringBuilder(4096);
            sb.append("{\"model\":");
            MiniJson.quote(sb, modelId);
            sb.append(",\"stream\":true,\"temperature\":").append(temperature);
            sb.append(",\"max_tokens\":").append(maxTokens);
            sb.append(",\"frequency_penalty\":0.35");
            sb.append(",\"presence_penalty\":0.25");
            if (endpoint.contains("127.0.0.1") || endpoint.contains("localhost")) {
                sb.append(",\"min_p\":0.08");
                sb.append(",\"repeat_penalty\":1.15");
            }
            if (!effort.isEmpty()) {
                sb.append(",\"reasoning_effort\":");
                MiniJson.quote(sb, effort);
            }
            sb.append(",\"messages\":[");
            for (int k = 0; k < messages.size(); k++) {
                if (k > 0) {
                    sb.append(',');
                }
                ChatMessage m = messages.get(k);
                sb.append("{\"role\":");
                MiniJson.quote(sb, m.role());
                sb.append(",\"content\":");
                MiniJson.quote(sb, m.content());
                sb.append('}');
            }
            return sb.append("]}").toString();
        }

        private String friendly(int status, String modelId, String body) {
            String snippet = body.isBlank() ? "" : " - " + body;
            return switch (status) {
                case 401, 403 -> "The API key was rejected by " + label + " (HTTP " + status + "). Check the key in the config" + snippet;
                case 404 -> "No model called '" + modelId + "' at " + label + " (HTTP 404)" + snippet;
                case 429 -> "The free quota at " + label + " is used up for now (HTTP 429)" + snippet;
                default -> "Got HTTP " + status + " from " + label + snippet;
            };
        }

        /** Runs the one terminal callback, once, unless the request was cancelled. */
        private void terminal(Runnable callback) {
            if (!cancelled && finished.compareAndSet(false, true)) {
                callback.run();
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            Thread w = worker;
            if (w != null) {
                w.interrupt(); // unblocks a request still waiting for its response headers
            }
            closeQuietly(stream); // unblocks a read in the middle of the stream, and tells the server to stop
            finished.set(true);
            active.remove(this);
        }

        @Override
        public boolean isFinished() {
            return finished.get();
        }
    }

    private static String readSmall(InputStream in) {
        try (in) {
            String s = new String(in.readNBytes(ERROR_BODY_CHARS * 2), StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
            Object json = null;
            try {
                json = MiniJson.parse(s);
            } catch (IllegalArgumentException notJson) {
                // keep the raw text
            }
            String msg = json == null ? null : MiniJson.string(json, "error", "message");
            if (msg == null && json != null) {
                msg = MiniJson.string(json, "message");
            }
            String out = msg != null ? msg : s;
            return out.length() > ERROR_BODY_CHARS ? out.substring(0, ERROR_BODY_CHARS) + "..." : out;
        } catch (IOException e) {
            return "";
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }
}

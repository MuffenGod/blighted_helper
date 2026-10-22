import com.blighteddreams.enhancements.helper.ai.LocalTtsProvider;
import com.blighteddreams.enhancements.helper.ai.TtsProvider;
import com.blighteddreams.enhancements.helper.client.voice.PcmSession;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Standalone check of LocalTtsProvider and PcmSession against a RUNNING voice server (no Minecraft needed).
 * Build and run: see run_smoke_test.ps1. Pass --no-audio to skip the one quiet audible line.
 */
public class LocalTtsSmoke {
    static int failures;

    static void check(String name, boolean ok, String detail) {
        System.out.printf("%s  %s  %s%n", ok ? "PASS" : "FAIL", name, detail);
        if (!ok) failures++;
    }

    /** Collects everything the provider delivers. */
    static final class Recorder implements TtsProvider.Sink {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicLong firstByteNanos = new AtomicLong();
        final AtomicInteger rate = new AtomicInteger();
        final AtomicInteger oddChunks = new AtomicInteger();
        final AtomicReference<Exception> error = new AtomicReference<>();
        volatile boolean completed;
        volatile int callbacksAfterDone;

        public void onFormat(int r) { rate.set(r); }
        public void onPcm(byte[] d, int o, int l) {
            if (done.getCount() == 0) callbacksAfterDone++;
            if (l % 2 != 0) oddChunks.incrementAndGet();
            firstByteNanos.compareAndSet(0, System.nanoTime());
            bytes.write(d, o, l);
        }
        public void onComplete() { completed = true; done.countDown(); }
        public void onError(Exception e) { error.set(e); done.countDown(); }
    }

    public static void main(String[] args) throws Exception {
        boolean playAudio = !java.util.Arrays.asList(args).contains("--no-audio");
        String url = System.getProperty("voice.url", "http://127.0.0.1:8791");
        LocalTtsProvider p = new LocalTtsProvider(url, "", 4, false);

        // 1. health
        AtomicReference<TtsProvider.Health> h = new AtomicReference<>();
        CountDownLatch hl = new CountDownLatch(1);
        p.checkHealth(x -> { h.set(x); hl.countDown(); });
        check("health answers", hl.await(5, TimeUnit.SECONDS), "");
        check("server is ready", h.get() != null && h.get().ready() && p.isAvailable(), String.valueOf(h.get()));

        // 2. a streamed line: format, whole samples, time to first byte, duration
        Recorder r = new Recorder();
        long t0 = System.nanoTime();
        TtsProvider.Handle hd = p.speak(new TtsProvider.Request("The Sawmill key unlocks the Simple Storage chests.", "emily", "calm"), r);
        boolean fin = r.done.await(30, TimeUnit.SECONDS);
        double secs = r.bytes.size() / 2.0 / Math.max(1, r.rate.get());
        double ttfaMs = (r.firstByteNanos.get() - t0) / 1e6;
        check("line completes", fin && r.completed && r.error.get() == null, "error=" + r.error.get());
        check("sample rate is 24000", r.rate.get() == 24000, "rate=" + r.rate.get());
        check("only whole 16-bit samples delivered", r.oddChunks.get() == 0, "odd chunks=" + r.oddChunks.get());
        check("audio has a plausible length", secs > 1.5 && secs < 8, String.format("%.2f s of audio", secs));
        check("first audio arrives quickly", ttfaMs > 0 && ttfaMs < 700, String.format("%.0f ms from speak() to first PCM", ttfaMs));
        check("handle reports finished", hd.isFinished(), "");

        // 3. JSON escaping: quotes, backslash, newline, tab and non-ASCII must reach the server intact
        Recorder esc = new Recorder();
        p.speak(new TtsProvider.Request("She said \"hold on\" \\ then\nwaited\tfor the café — quietly.", "emily", "whisper"), esc);
        check("awkward text is accepted", esc.done.await(30, TimeUnit.SECONDS) && esc.completed && esc.bytes.size() > 24000,
                "error=" + esc.error.get() + " bytes=" + esc.bytes.size());

        // 4. errors: unknown voice -> readable error, not a hang
        Recorder bad = new Recorder();
        p.speak(new TtsProvider.Request("Hello.", "nobody", null), bad);
        check("unknown voice reports an error", bad.done.await(10, TimeUnit.SECONDS) && bad.error.get() != null
                && bad.error.get().getMessage().contains("404"), String.valueOf(bad.error.get()));

        // 5. cancel mid-line, then speak again
        Recorder c = new Recorder();
        String longText = "The colony has grown while you were exploring. We have a new baker, two more builders, and a guard who keeps asking when you will return. Perhaps we should visit them before nightfall.";
        TtsProvider.Handle ch = p.speak(new TtsProvider.Request(longText, "emily", "calm"), c);
        while (c.bytes.size() < 24000 && System.nanoTime() - t0 < 60e9) Thread.sleep(5); // wait for ~0.5 s of audio
        ch.cancel();
        int atCancel = c.bytes.size();
        Thread.sleep(1500);
        check("cancel stops delivery", c.bytes.size() - atCancel <= 16384 && !c.completed && c.error.get() == null,
                "bytes after cancel=" + (c.bytes.size() - atCancel) + " completed=" + c.completed);
        Recorder after = new Recorder();
        p.speak(new TtsProvider.Request("Yes, I am here.", "emily", "calm"), after);
        check("speaking works right after a cancel", after.done.await(30, TimeUnit.SECONDS) && after.completed && after.bytes.size() > 24000,
                "bytes=" + after.bytes.size());

        // 6. real playback through PcmSession (short, quiet)
        if (playAudio) {
            AtomicReference<Exception> problem = new AtomicReference<>();
            PcmSession session = new PcmSession(() -> 0.35, problem::set);
            Recorder tee = new Recorder();
            TtsProvider.Sink both = new TtsProvider.Sink() {
                public void onFormat(int rt) { tee.onFormat(rt); session.onFormat(rt); }
                public void onPcm(byte[] d, int o, int l) { tee.onPcm(d, o, l); session.onPcm(d, o, l); }
                public void onComplete() { session.onComplete(); tee.onComplete(); }
                public void onError(Exception e) { session.onError(e); tee.onError(e); }
            };
            long s0 = System.nanoTime();
            p.speak(new TtsProvider.Request("Yes, I am here.", "emily", "calm"), both);
            boolean ok = tee.done.await(30, TimeUnit.SECONDS);
            long playedMs = (System.nanoTime() - s0) / 1_000_000;
            check("audio device plays a line to the end", ok && tee.completed && session.isEnded() && problem.get() == null,
                    "problem=" + problem.get() + ", " + playedMs + " ms start to finish");

            // stop() must cut a line off immediately
            AtomicReference<Exception> problem2 = new AtomicReference<>();
            PcmSession s2 = new PcmSession(() -> 0.2, problem2::set);
            TtsProvider.Handle h2 = p.speak(new TtsProvider.Request(longText, "emily", "calm"), s2);
            Thread.sleep(1200);
            long st = System.nanoTime();
            s2.stop();
            h2.cancel();
            check("stop() ends playback at once", s2.isEnded() && (System.nanoTime() - st) / 1e6 < 200, String.format("%.0f ms", (System.nanoTime() - st) / 1e6));
        }

        // 7. unreachable server: fast error, marked unavailable
        LocalTtsProvider dead = new LocalTtsProvider("http://127.0.0.1:1", "", 4, false);
        Recorder d = new Recorder();
        long d0 = System.nanoTime();
        dead.speak(new TtsProvider.Request("Hello.", "emily", null), d);
        boolean got = d.done.await(10, TimeUnit.SECONDS);
        check("dead server gives an error quickly", got && d.error.get() != null && (System.nanoTime() - d0) / 1e6 < 3000,
                String.format("%.0f ms, %s", (System.nanoTime() - d0) / 1e6, d.error.get()));
        check("dead server is marked unavailable", !dead.isAvailable(), String.valueOf(dead.lastHealth()));
        dead.close();

        p.close();
        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}

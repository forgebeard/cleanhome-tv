package com.gathin.tvhome;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.view.Choreographer;

import java.util.Arrays;
import java.util.Locale;

/**
 * Lightweight, always-on frame statistics for Home: it sits on the Choreographer callback and only stores
 * numbers, so the meter itself costs a few microseconds per frame. The hold-Settings menu prints the summary,
 * which is how "how fast is the TV actually going" gets answered without ADB.
 */
final class PerfMeter {

    /** Two seconds of frames at 60 Hz: enough to smooth out a scroll, short enough to react to a change. */
    private static final int SIZE = 128;

    private final long[] intervals = new long[SIZE];
    private int count;
    private int index;
    private long lastFrameNanos;
    private long firstFrameAt = -1;
    private final long processStart = Process.getStartUptimeMillis();
    private boolean running;

    private final Choreographer.FrameCallback callback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!running) return;
            if (lastFrameNanos != 0) {
                long ms = Math.max(1, (frameTimeNanos - lastFrameNanos) / 1_000_000L);
                intervals[index] = ms;
                index = (index + 1) % SIZE;
                if (count < SIZE) count++;
            }
            lastFrameNanos = frameTimeNanos;
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    void start() {
        if (running) return;
        running = true;
        lastFrameNanos = 0;
        Choreographer.getInstance().postFrameCallback(callback);
    }

    void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(callback);
    }

    /** Called from Home's first pre-draw: the gap to the process start is the cold start time. */
    void markFirstFrame() {
        if (firstFrameAt < 0) firstFrameAt = SystemClock.uptimeMillis();
    }

    /** A human-readable block for the performance dialog; empty numbers read as "no data yet". */
    String summary(Context context) {
        if (count < 2) return context.getString(R.string.perf_warming_up);
        long[] sorted = Arrays.copyOf(intervals, count);
        Arrays.sort(sorted);
        long sum = 0;
        int jank = 0;
        for (int i = 0; i < count; i++) {
            sum += sorted[i];
            if (sorted[i] > 34) jank++;
        }
        double average = (double) sum / count;
        long p95 = sorted[Math.min(count - 1, (int) (count * 0.95))];
        long worst = sorted[count - 1];
        long startup = firstFrameAt > 0 ? firstFrameAt - processStart : -1;

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        StringBuilder text = new StringBuilder();
        text.append(context.getString(R.string.perf_fps, 1000.0 / average)).append('\n');
        text.append(context.getString(R.string.perf_frame, average, p95, worst)).append('\n');
        text.append(context.getString(R.string.perf_jank, jank * 100.0 / count)).append('\n');
        text.append(startup > 0
                ? context.getString(R.string.perf_startup, startup)
                : context.getString(R.string.perf_startup_unknown)).append('\n');
        text.append(context.getString(R.string.perf_memory, usedMb));
        return text.toString();
    }
}

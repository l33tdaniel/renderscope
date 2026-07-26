package dev.renderscope.client.profiler;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RenderProfiler {
    public static final RenderProfiler INSTANCE = new RenderProfiler();
    private static final int SCHEMA_VERSION = 1;
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private final Map<RendererKey, MutableRendererStats> rows = new HashMap<>();
    private volatile boolean recording;
    private volatile CaptureMode captureMode = CaptureMode.FULL;
    private long startedAtNanos;
    private long endedAtNanos;

    private RenderProfiler() {}

    public synchronized boolean start() {
        return start(CaptureMode.FULL);
    }

    public synchronized boolean start(CaptureMode mode) {
        if (recording) {
            return false;
        }

        rows.clear();
        captureMode = mode;
        enableThreadCpuTime();
        startedAtNanos = System.nanoTime();
        endedAtNanos = startedAtNanos;
        recording = true;
        return true;
    }

    public synchronized boolean stop() {
        if (!recording) {
            return false;
        }

        endedAtNanos = System.nanoTime();
        recording = false;
        return true;
    }

    public boolean isRecording() {
        return recording;
    }

    public CaptureMode captureMode() {
        return captureMode;
    }

    public long currentThreadCpuNanos() {
        if (!THREADS.isCurrentThreadCpuTimeSupported() || !THREADS.isThreadCpuTimeEnabled()) {
            return -1;
        }
        return THREADS.getCurrentThreadCpuTime();
    }

    public synchronized void record(RendererKey key, Measurement measurement) {
        if (!recording) {
            return;
        }
        rows.computeIfAbsent(key, ignored -> new MutableRendererStats()).add(measurement);
    }

    public synchronized ProfileSnapshot snapshot() {
        long captureEnd = recording ? System.nanoTime() : endedAtNanos;
        long durationNanos = startedAtNanos == 0 ? 0 : Math.max(0, captureEnd - startedAtNanos);
        List<ProfileRow> snapshotRows = new ArrayList<>(rows.size());

        rows.forEach((key, value) -> snapshotRows.add(value.snapshot(key)));
        snapshotRows.sort((left, right) -> Long.compare(right.elapsedNanos(), left.elapsedNanos()));

        long totalCalls = snapshotRows.stream().mapToLong(ProfileRow::calls).sum();
        return new ProfileSnapshot(
            SCHEMA_VERSION,
            Instant.now().toString(),
            durationNanos,
            recording,
            captureMode,
            totalCalls,
            List.copyOf(snapshotRows)
        );
    }

    public record RendererKey(String blockEntityNamespace, String blockEntityId, String rendererClass) {}

    public record Measurement(
        long elapsedNanos,
        long threadCpuNanos,
        long vertices,
        long estimatedVertexBytes,
        long renderTypeRequests,
        int distinctRenderTypes,
        boolean failed
    ) {}

    public record ProfileRow(
        String blockEntityNamespace,
        String blockEntityId,
        String rendererClass,
        long calls,
        long failedCalls,
        long elapsedNanos,
        long threadCpuNanos,
        long threadCpuSamples,
        long maxElapsedNanos,
        long vertices,
        long estimatedVertexBytes,
        long renderTypeRequests,
        long distinctRenderTypeTotal
    ) {}

    public record ProfileSnapshot(
        int schemaVersion,
        String generatedAtUtc,
        long durationNanos,
        boolean recording,
        CaptureMode captureMode,
        long totalCalls,
        List<ProfileRow> rows
    ) {}

    public enum CaptureMode {
        FULL("full", true),
        CPU_ONLY("cpu", false);

        private final String commandName;
        private final boolean capturesGeometry;

        CaptureMode(String commandName, boolean capturesGeometry) {
            this.commandName = commandName;
            this.capturesGeometry = capturesGeometry;
        }

        public String commandName() {
            return commandName;
        }

        public boolean capturesGeometry() {
            return capturesGeometry;
        }
    }

    private static void enableThreadCpuTime() {
        try {
            if (THREADS.isCurrentThreadCpuTimeSupported() && !THREADS.isThreadCpuTimeEnabled()) {
                THREADS.setThreadCpuTimeEnabled(true);
            }
        } catch (SecurityException ignored) {
            // The report uses -1 when the JVM does not permit thread CPU timing.
        }
    }

    private static final class MutableRendererStats {
        private long calls;
        private long failedCalls;
        private long elapsedNanos;
        private long threadCpuNanos;
        private long threadCpuSamples;
        private long maxElapsedNanos;
        private long vertices;
        private long estimatedVertexBytes;
        private long renderTypeRequests;
        private long distinctRenderTypeTotal;

        private void add(Measurement measurement) {
            calls++;
            if (measurement.failed()) {
                failedCalls++;
            }
            elapsedNanos += measurement.elapsedNanos();
            if (measurement.threadCpuNanos() >= 0) {
                threadCpuNanos += measurement.threadCpuNanos();
                threadCpuSamples++;
            }
            maxElapsedNanos = Math.max(maxElapsedNanos, measurement.elapsedNanos());
            vertices += measurement.vertices();
            estimatedVertexBytes += measurement.estimatedVertexBytes();
            renderTypeRequests += measurement.renderTypeRequests();
            distinctRenderTypeTotal += measurement.distinctRenderTypes();
        }

        private ProfileRow snapshot(RendererKey key) {
            return new ProfileRow(
                key.blockEntityNamespace(),
                key.blockEntityId(),
                key.rendererClass(),
                calls,
                failedCalls,
                elapsedNanos,
                threadCpuSamples == 0 ? -1 : threadCpuNanos,
                threadCpuSamples,
                maxElapsedNanos,
                vertices,
                estimatedVertexBytes,
                renderTypeRequests,
                distinctRenderTypeTotal
            );
        }
    }
}

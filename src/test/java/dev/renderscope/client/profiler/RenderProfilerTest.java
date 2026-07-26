package dev.renderscope.client.profiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RenderProfilerTest {
    @Test
    void aggregatesMeasurementsAndSortsRowsByElapsedTime() {
        RenderProfiler profiler = RenderProfiler.INSTANCE;
        profiler.stop();
        assertTrue(profiler.start());

        RenderProfiler.RendererKey slow = new RenderProfiler.RendererKey(
            "example",
            "example:machine",
            "example.client.MachineRenderer"
        );
        RenderProfiler.RendererKey fast = new RenderProfiler.RendererKey(
            "minecraft",
            "minecraft:chest",
            "net.minecraft.client.renderer.blockentity.ChestRenderer"
        );

        profiler.record(slow, new RenderProfiler.Measurement(100, 80, 12, 384, 2, 1, false));
        profiler.record(slow, new RenderProfiler.Measurement(200, 170, 20, 640, 3, 2, true));
        profiler.record(fast, new RenderProfiler.Measurement(50, -1, 4, 128, 1, 1, false));
        assertTrue(profiler.stop());

        RenderProfiler.ProfileSnapshot snapshot = profiler.snapshot();
        assertFalse(snapshot.recording());
        assertEquals(3, snapshot.totalCalls());
        assertEquals(2, snapshot.rows().size());

        RenderProfiler.ProfileRow slowRow = snapshot.rows().getFirst();
        assertEquals("example:machine", slowRow.blockEntityId());
        assertEquals(2, slowRow.calls());
        assertEquals(1, slowRow.failedCalls());
        assertEquals(300, slowRow.elapsedNanos());
        assertEquals(250, slowRow.threadCpuNanos());
        assertEquals(2, slowRow.threadCpuSamples());
        assertEquals(32, slowRow.vertices());
        assertEquals(1024, slowRow.estimatedVertexBytes());
        assertEquals(5, slowRow.renderTypeRequests());
        assertEquals(3, slowRow.distinctRenderTypeTotal());
    }

    @Test
    void startingANewCaptureClearsPreviousRows() {
        RenderProfiler profiler = RenderProfiler.INSTANCE;
        profiler.stop();
        assertTrue(profiler.start());
        profiler.record(
            new RenderProfiler.RendererKey("example", "example:test", "example.Renderer"),
            new RenderProfiler.Measurement(1, 1, 1, 1, 1, 1, false)
        );
        assertTrue(profiler.stop());
        assertEquals(1, profiler.snapshot().totalCalls());

        assertTrue(profiler.start());
        assertEquals(0, profiler.snapshot().totalCalls());
        assertTrue(profiler.stop());
    }

    @Test
    void unsupportedThreadCpuTimeIsReportedAsUnavailable() {
        RenderProfiler profiler = RenderProfiler.INSTANCE;
        profiler.stop();
        assertTrue(profiler.start(RenderProfiler.CaptureMode.CPU_ONLY));
        profiler.record(
            new RenderProfiler.RendererKey("example", "example:test", "example.Renderer"),
            new RenderProfiler.Measurement(1, -1, 0, 0, 0, 0, false)
        );
        assertTrue(profiler.stop());

        RenderProfiler.ProfileSnapshot snapshot = profiler.snapshot();
        assertEquals(RenderProfiler.CaptureMode.CPU_ONLY, snapshot.captureMode());
        assertEquals(-1, snapshot.rows().getFirst().threadCpuNanos());
        assertEquals(0, snapshot.rows().getFirst().threadCpuSamples());
    }
}

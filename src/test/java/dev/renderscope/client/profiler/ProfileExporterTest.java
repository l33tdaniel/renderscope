package dev.renderscope.client.profiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileExporterTest {
    private static final RenderProfiler.ProfileSnapshot SNAPSHOT = new RenderProfiler.ProfileSnapshot(
        1,
        "2026-01-02T03:04:05Z",
        42,
        false,
        RenderProfiler.CaptureMode.FULL,
        1,
        List.of(
            new RenderProfiler.ProfileRow(
                "example",
                "example:machine",
                "example.client.MachineRenderer",
                1,
                0,
                20,
                18,
                1,
                20,
                4,
                128,
                1,
                1
            )
        )
    );

    @Test
    void jsonContainsSchemaAndAggregateRow() {
        String json = ProfileExporter.toJson(SNAPSHOT);
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"blockEntityId\": \"example:machine\""));
        assertTrue(json.contains("\"estimatedVertexBytes\": 128"));
    }

    @Test
    void csvContainsStableHeaderAndAggregateRow() {
        String csv = ProfileExporter.toCsv(SNAPSHOT);
        assertTrue(csv.startsWith("capture_mode,block_entity_namespace,block_entity_id,renderer_class"));
        assertTrue(csv.contains("\"full\",\"example\",\"example:machine\",\"example.client.MachineRenderer\",1,0"));
    }
}

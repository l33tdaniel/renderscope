package dev.renderscope.client.profiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ProfileExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
        .ofPattern("uuuuMMdd-HHmmss-SSS'Z'", Locale.ROOT)
        .withZone(ZoneOffset.UTC);

    private ProfileExporter() {}

    public static Path export(
        RenderProfiler.ProfileSnapshot snapshot,
        Path directory,
        Format format
    ) throws IOException {
        Files.createDirectories(directory);
        String filename = "renderscope-" + FILE_TIMESTAMP.format(Instant.now()) + "." + format.extension;
        Path destination = directory.resolve(filename);
        String contents = format == Format.JSON ? toJson(snapshot) : toCsv(snapshot);
        writeAtomically(destination, contents);
        return destination;
    }

    static String toJson(RenderProfiler.ProfileSnapshot snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", snapshot.schemaVersion());
        root.put("generatedAtUtc", snapshot.generatedAtUtc());
        root.put("captureDurationNanos", snapshot.durationNanos());
        root.put("recording", snapshot.recording());
        root.put("captureMode", snapshot.captureMode().commandName());
        root.put("totalCalls", snapshot.totalCalls());
        root.put("rows", snapshot.rows());
        return GSON.toJson(root) + System.lineSeparator();
    }

    static String toCsv(RenderProfiler.ProfileSnapshot snapshot) {
        StringBuilder csv = new StringBuilder(
            "capture_mode,block_entity_namespace,block_entity_id,renderer_class,calls,failed_calls,"
                + "elapsed_nanos,thread_cpu_nanos,thread_cpu_samples,max_elapsed_nanos,vertices,"
                + "estimated_vertex_bytes,render_type_requests,distinct_render_type_total\n"
        );
        for (RenderProfiler.ProfileRow row : snapshot.rows()) {
            appendCsv(csv, snapshot.captureMode().commandName());
            appendCsv(csv, row.blockEntityNamespace());
            appendCsv(csv, row.blockEntityId());
            appendCsv(csv, row.rendererClass());
            csv.append(row.calls()).append(',')
                .append(row.failedCalls()).append(',')
                .append(row.elapsedNanos()).append(',')
                .append(row.threadCpuNanos()).append(',')
                .append(row.threadCpuSamples()).append(',')
                .append(row.maxElapsedNanos()).append(',')
                .append(row.vertices()).append(',')
                .append(row.estimatedVertexBytes()).append(',')
                .append(row.renderTypeRequests()).append(',')
                .append(row.distinctRenderTypeTotal()).append('\n');
        }
        return csv.toString();
    }

    private static void appendCsv(StringBuilder csv, String value) {
        csv.append('"').append(value.replace("\"", "\"\"")).append("\",");
    }

    private static void writeAtomically(Path destination, String contents) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".renderscope-", ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try {
                Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public enum Format {
        JSON("json"),
        CSV("csv");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }
    }
}

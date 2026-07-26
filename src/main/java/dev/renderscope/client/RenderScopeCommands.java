package dev.renderscope.client;

import com.mojang.brigadier.CommandDispatcher;
import dev.renderscope.RenderScope;
import dev.renderscope.client.profiler.ProfileExporter;
import dev.renderscope.client.profiler.RenderProfiler;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

final class RenderScopeCommands {
    private static final int TOP_ROW_LIMIT = 10;

    private RenderScopeCommands() {}

    static void register(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("renderscope")
                .then(
                    Commands.literal("start")
                        .executes(context -> start(RenderProfiler.CaptureMode.FULL))
                        .then(
                            Commands.literal("full")
                                .executes(context -> start(RenderProfiler.CaptureMode.FULL))
                        )
                        .then(
                            Commands.literal("cpu")
                                .executes(context -> start(RenderProfiler.CaptureMode.CPU_ONLY))
                        )
                )
                .then(Commands.literal("stop").executes(context -> stop()))
                .then(Commands.literal("status").executes(context -> status()))
                .then(Commands.literal("top").executes(context -> top()))
                .then(
                    Commands.literal("export")
                        .executes(context -> export(ProfileExporter.Format.JSON))
                        .then(Commands.literal("json").executes(context -> export(ProfileExporter.Format.JSON)))
                        .then(Commands.literal("csv").executes(context -> export(ProfileExporter.Format.CSV)))
                )
        );
    }

    private static int start(RenderProfiler.CaptureMode mode) {
        if (!RenderProfiler.INSTANCE.start(mode)) {
            return reply("A capture is already running.");
        }

        return reply(
            "Capture started in %s mode. Run /renderscope stop when the scene is complete."
                .formatted(mode.commandName())
        );
    }

    private static int stop() {
        if (!RenderProfiler.INSTANCE.stop()) {
            return reply("No capture is running.");
        }

        RenderProfiler.ProfileSnapshot snapshot = RenderProfiler.INSTANCE.snapshot();
        return reply(
            "Capture stopped: %,d calls across %,d renderer rows."
                .formatted(snapshot.totalCalls(), snapshot.rows().size())
        );
    }

    private static int status() {
        RenderProfiler.ProfileSnapshot snapshot = RenderProfiler.INSTANCE.snapshot();
        String state = snapshot.recording() ? "recording" : "stopped";
        return reply(
            "%s in %s mode; %.2f s captured, %,d calls, %,d renderer rows."
                .formatted(
                    state,
                    snapshot.captureMode().commandName(),
                    snapshot.durationNanos() / 1_000_000_000.0,
                    snapshot.totalCalls(),
                    snapshot.rows().size()
                )
        );
    }

    private static int top() {
        RenderProfiler.ProfileSnapshot snapshot = RenderProfiler.INSTANCE.snapshot();
        if (snapshot.rows().isEmpty()) {
            return reply("No samples are available. Start a capture first.");
        }

        reply("Top renderers by inclusive elapsed time:");
        snapshot.rows().stream().limit(TOP_ROW_LIMIT).forEach(row ->
            reply(
                "%7.2f ms  %7d calls  %s  (%s)"
                    .formatted(
                        row.elapsedNanos() / 1_000_000.0,
                        row.calls(),
                        row.rendererClass(),
                        row.blockEntityId()
                    )
            )
        );
        return 1;
    }

    private static int export(ProfileExporter.Format format) {
        RenderProfiler.ProfileSnapshot snapshot = RenderProfiler.INSTANCE.snapshot();
        if (snapshot.rows().isEmpty()) {
            return reply("No samples are available to export.");
        }

        Path reportDirectory = Minecraft.getInstance().gameDirectory.toPath().resolve("renderscope-reports");
        try {
            Path report = ProfileExporter.export(snapshot, reportDirectory, format);
            return reply("Wrote " + report.getFileName());
        } catch (IOException exception) {
            RenderScope.LOGGER.error("Could not export the RenderScope capture", exception);
            return reply("Export failed. See the game log for details.");
        }
    }

    private static int reply(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        Component component = Component.literal("[RenderScope] " + message);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(component, false);
        } else {
            RenderScope.LOGGER.info(message);
        }
        return 1;
    }
}

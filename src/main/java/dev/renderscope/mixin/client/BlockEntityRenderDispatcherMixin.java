package dev.renderscope.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.renderscope.client.profiler.CountingMultiBufferSource;
import dev.renderscope.client.profiler.RenderProfiler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityRenderDispatcherMixin {
    @Redirect(
        method = "setupAndRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render("
                + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
                + "FLcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
        )
    )
    private static <T extends BlockEntity> void renderScope$profile(
        BlockEntityRenderer<T> renderer,
        T blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        RenderProfiler profiler = RenderProfiler.INSTANCE;
        if (!profiler.isRecording()) {
            renderer.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        CountingMultiBufferSource countingBuffers = profiler.captureMode().capturesGeometry()
            ? new CountingMultiBufferSource(bufferSource)
            : null;
        MultiBufferSource measuredBuffers = countingBuffers == null ? bufferSource : countingBuffers;
        long elapsedStart = System.nanoTime();
        long cpuStart = profiler.currentThreadCpuNanos();
        boolean failed = true;
        try {
            renderer.render(blockEntity, partialTick, poseStack, measuredBuffers, packedLight, packedOverlay);
            failed = false;
        } finally {
            long elapsedNanos = System.nanoTime() - elapsedStart;
            long cpuEnd = profiler.currentThreadCpuNanos();
            long threadCpuNanos = cpuStart >= 0 && cpuEnd >= cpuStart ? cpuEnd - cpuStart : -1;
            ResourceLocation blockEntityId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
            String id = blockEntityId == null ? "unknown:unknown" : blockEntityId.toString();
            String namespace = blockEntityId == null ? "unknown" : blockEntityId.getNamespace();

            profiler.record(
                new RenderProfiler.RendererKey(namespace, id, renderer.getClass().getName()),
                new RenderProfiler.Measurement(
                    elapsedNanos,
                    threadCpuNanos,
                    countingBuffers == null ? 0 : countingBuffers.vertices(),
                    countingBuffers == null ? 0 : countingBuffers.estimatedVertexBytes(),
                    countingBuffers == null ? 0 : countingBuffers.renderTypeRequests(),
                    countingBuffers == null ? 0 : countingBuffers.distinctRenderTypes(),
                    failed
                )
            );
        }
    }
}

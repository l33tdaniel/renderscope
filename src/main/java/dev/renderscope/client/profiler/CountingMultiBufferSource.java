package dev.renderscope.client.profiler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;

public final class CountingMultiBufferSource implements MultiBufferSource {
    private static final int INTS_PER_BAKED_QUAD_VERTEX = 8;

    private final MultiBufferSource delegate;
    private final Set<RenderType> distinctRenderTypes = Collections.newSetFromMap(new IdentityHashMap<>());
    private long vertices;
    private long estimatedVertexBytes;
    private long renderTypeRequests;

    public CountingMultiBufferSource(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        renderTypeRequests++;
        distinctRenderTypes.add(renderType);

        VertexConsumer consumer = delegate.getBuffer(renderType);
        return new CountingVertexConsumer(consumer, renderType.format().getVertexSize());
    }

    public long vertices() {
        return vertices;
    }

    public long estimatedVertexBytes() {
        return estimatedVertexBytes;
    }

    public long renderTypeRequests() {
        return renderTypeRequests;
    }

    public int distinctRenderTypes() {
        return distinctRenderTypes.size();
    }

    private void countVertices(long count, int vertexSize) {
        vertices += count;
        estimatedVertexBytes += count * vertexSize;
    }

    private final class CountingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int vertexSize;

        private CountingVertexConsumer(VertexConsumer delegate, int vertexSize) {
            this.delegate = delegate;
            this.vertexSize = vertexSize;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            countVertices(1, vertexSize);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }

        @Override
        public VertexConsumer misc(VertexFormatElement element, int... rawData) {
            delegate.misc(element, rawData);
            return this;
        }

        @Override
        public void putBulkData(
            PoseStack.Pose pose,
            BakedQuad quad,
            float red,
            float green,
            float blue,
            float alpha,
            int packedLight,
            int packedOverlay
        ) {
            delegate.putBulkData(pose, quad, red, green, blue, alpha, packedLight, packedOverlay);
            countQuad(quad);
        }

        @Override
        public void putBulkData(
            PoseStack.Pose pose,
            BakedQuad quad,
            float[] brightness,
            float red,
            float green,
            float blue,
            float alpha,
            int[] lightmap,
            int packedOverlay,
            boolean readAlpha
        ) {
            delegate.putBulkData(
                pose,
                quad,
                brightness,
                red,
                green,
                blue,
                alpha,
                lightmap,
                packedOverlay,
                readAlpha
            );
            countQuad(quad);
        }

        @Override
        public void putBulkData(
            PoseStack.Pose pose,
            BakedQuad quad,
            float red,
            float green,
            float blue,
            float alpha,
            int packedLight,
            int packedOverlay,
            boolean readExistingColor
        ) {
            delegate.putBulkData(
                pose,
                quad,
                red,
                green,
                blue,
                alpha,
                packedLight,
                packedOverlay,
                readExistingColor
            );
            countQuad(quad);
        }

        private void countQuad(BakedQuad quad) {
            countVertices(quad.getVertices().length / INTS_PER_BAKED_QUAD_VERTEX, vertexSize);
        }
    }
}

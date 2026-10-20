package com.einsteinmods.relativity.client.frames;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * {@code RenderTypeFeatureRenderer#getVertexBuilder} of the item feature renderer, exposed by
 * {@code ItemFeatureRendererMixin} so a cached submit asks for its builders exactly like vanilla.
 */
public interface VertexBuilderSource {
  VertexConsumer relativity$vertexBuilder(RenderType renderType);
}

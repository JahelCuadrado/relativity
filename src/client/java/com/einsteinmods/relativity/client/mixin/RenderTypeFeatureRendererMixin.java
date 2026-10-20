package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.frames.ItemFrameGeometryCache;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code RenderTypeFeatureRenderer#getVertexBuilder} is where every feature renderer (vanilla's
 * item renderer, Indigo's block model renderer) gets the builder for a render type; {@link
 * ItemFrameGeometryCache} watches it while it captures a submit, to split the bytes by render type.
 *
 * <p>Cost: one field check per call when nothing is being captured; no allocation.
 */
@Mixin(RenderTypeFeatureRenderer.class)
abstract class RenderTypeFeatureRendererMixin {
  @ModifyReturnValue(method = "getVertexBuilder", at = @At("RETURN"))
  private VertexConsumer relativity$watchVertexBuilder(
      VertexConsumer builder, @Local(argsOnly = true) RenderType renderType) {
    ItemFrameGeometryCache.INSTANCE.onVertexBuilder(renderType, builder);
    return builder;
  }
}

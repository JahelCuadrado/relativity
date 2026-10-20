package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.frames.ItemFrameGeometryCache;
import com.einsteinmods.relativity.client.frames.VertexBuilderSource;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Item submits of item frames: {@code ItemFeatureRenderer#prepareSubmit} calls {@code
 * #prepareMainSubmit}, which asks {@code RenderTypeFeatureRenderer#getVertexBuilder} for each
 * quad's render type and writes it with {@code VertexConsumer#putBakedQuad} (or {@code
 * #putBakedQuadWithGlint}). {@link ItemFrameGeometryCache} decides whether the bytes are replayed,
 * or emitted by vanilla and captured ({@link RenderTypeFeatureRendererMixin} splits the capture by
 * render type). Outline submits ({@code #prepareOutlineSubmit}) are untouched.
 */
@Mixin(ItemFeatureRenderer.class)
abstract class ItemFeatureRendererMixin
    extends RenderTypeFeatureRenderer<ItemFeatureRenderer.Submit> implements VertexBuilderSource {
  @Shadow
  private void prepareMainSubmit(ItemFeatureRenderer.Submit submit) {}

  @Redirect(
      method = "prepareSubmit",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;prepareMainSubmit(Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer$Submit;)V"))
  private void relativity$prepareMainSubmit(
      ItemFeatureRenderer renderer, ItemFeatureRenderer.Submit submit) {
    ItemFrameGeometryCache.Outcome outcome =
        ItemFrameGeometryCache.INSTANCE.beginItemSubmit(submit, this);
    if (outcome == ItemFrameGeometryCache.Outcome.REPLAYED) {
      return;
    }
    prepareMainSubmit(submit);
    if (outcome == ItemFrameGeometryCache.Outcome.CAPTURING) {
      ItemFrameGeometryCache.INSTANCE.endItemSubmit();
    }
  }

  @Override
  public VertexConsumer relativity$vertexBuilder(RenderType renderType) {
    return getVertexBuilder(renderType);
  }
}

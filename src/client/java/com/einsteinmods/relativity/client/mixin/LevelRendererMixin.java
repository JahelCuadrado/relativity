package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.frames.ItemFrameGeometryCache;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Starts each level frame of {@link ItemFrameGeometryCache} right before {@code
 * LevelRenderer#render} calls {@code #submitFeatures} (entities submit there, then {@code
 * FeatureRenderDispatcher#prepareFrame} builds the vertices). Cost: one call per frame.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
  @Shadow
  private void submitFeatures(
      LevelRenderState levelRenderState,
      SubmitNodeCollector submitNodeCollector,
      boolean renderOutline) {}

  @Redirect(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/LevelRenderer;submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V"))
  private void relativity$beginFrame(
      LevelRenderer renderer,
      LevelRenderState levelRenderState,
      SubmitNodeCollector submitNodeCollector,
      boolean renderOutline) {
    ItemFrameGeometryCache.INSTANCE.beginFrame();
    submitFeatures(levelRenderState, submitNodeCollector, renderOutline);
  }
}

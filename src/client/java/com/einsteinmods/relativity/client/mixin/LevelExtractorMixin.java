package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.frames.ItemFrameExtractCache;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Brackets {@code LevelExtractor#extractVisibleEntities} (called once per frame from {@code
 * LevelExtractor#extract}) for {@link ItemFrameExtractCache}: only that loop's item frame
 * extractions may keep their state. Cost: two calls per frame, no allocation (a redirect, not
 * callbacks).
 */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
  @Shadow
  private void extractVisibleEntities(
      Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState output) {}

  @Redirect(
      method = "extract",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V"))
  private void relativity$bracketEntityPass(
      LevelExtractor extractor,
      Camera camera,
      Frustum frustum,
      DeltaTracker deltaTracker,
      LevelRenderState output) {
    ItemFrameExtractCache.INSTANCE.beginEntityPass();
    try {
      extractVisibleEntities(camera, frustum, deltaTracker, output);
    } finally {
      ItemFrameExtractCache.INSTANCE.endEntityPass();
    }
  }
}

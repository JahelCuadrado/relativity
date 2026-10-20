package com.einsteinmods.relativity.gametest.mixin;

import com.einsteinmods.relativity.gametest.PrepareFrameTimings;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dev-only: times {@code FeatureRenderDispatcher#prepareFrame}. Never shipped. */
@Mixin(FeatureRenderDispatcher.class)
abstract class FeatureRenderDispatcherTimingMixin {
  @Inject(method = "prepareFrame", at = @At("HEAD"))
  private void relativityGametest$begin(
      SubmitNodeStorage storage,
      CallbackInfoReturnable<FeatureRenderDispatcher.PreparedFrame> callback) {
    PrepareFrameTimings.begin();
  }

  @Inject(method = "prepareFrame", at = @At("RETURN"))
  private void relativityGametest$end(
      SubmitNodeStorage storage,
      CallbackInfoReturnable<FeatureRenderDispatcher.PreparedFrame> callback) {
    PrepareFrameTimings.end();
  }
}

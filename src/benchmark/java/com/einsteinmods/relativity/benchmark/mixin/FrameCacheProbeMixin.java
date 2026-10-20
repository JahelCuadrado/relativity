package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.FrameCacheProbe;
import com.einsteinmods.relativity.client.frames.ItemFrameGeometryCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dev-only: counts how {@link ItemFrameGeometryCache} served each submit it was asked about. */
@Mixin(value = ItemFrameGeometryCache.class, remap = false)
abstract class FrameCacheProbeMixin {
  @Inject(method = "beginItemSubmit", at = @At("RETURN"))
  private void relativityBenchmark$itemOutcome(
      CallbackInfoReturnable<ItemFrameGeometryCache.Outcome> callback) {
    FrameCacheProbe.onItemOutcome(callback.getReturnValue());
  }

  @Inject(method = "beginMeshSubmit", at = @At("RETURN"))
  private void relativityBenchmark$meshOutcome(
      CallbackInfoReturnable<ItemFrameGeometryCache.Outcome> callback) {
    FrameCacheProbe.onMeshOutcome(callback.getReturnValue());
  }
}

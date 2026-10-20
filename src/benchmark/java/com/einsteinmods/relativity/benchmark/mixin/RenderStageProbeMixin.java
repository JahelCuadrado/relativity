package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.FrameCacheProbe;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev-only: render-thread CPU of {@code LevelRenderer#submitFeatures} (entities submit) and of the
 * {@code FeatureRenderDispatcher#prepareFrame} call that follows it in {@code #render}.
 */
@Mixin(LevelRenderer.class)
abstract class RenderStageProbeMixin {
  @Inject(method = "submitFeatures", at = @At("HEAD"))
  private void relativityBenchmark$submitStart(CallbackInfo callback) {
    FrameCacheProbe.onStageStart();
  }

  @Inject(method = "submitFeatures", at = @At("RETURN"))
  private void relativityBenchmark$submitEnd(CallbackInfo callback) {
    FrameCacheProbe.onSubmitFeaturesEnd();
  }

  @Inject(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"))
  private void relativityBenchmark$prepareStart(CallbackInfo callback) {
    FrameCacheProbe.onStageStart();
  }

  @Inject(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;",
              shift = At.Shift.AFTER))
  private void relativityBenchmark$prepareEnd(CallbackInfo callback) {
    FrameCacheProbe.onPrepareFrameEnd();
  }
}

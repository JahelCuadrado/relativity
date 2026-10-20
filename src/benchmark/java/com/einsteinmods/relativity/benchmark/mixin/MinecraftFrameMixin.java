package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.FrameTimings;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev-only frame clock. {@code Minecraft#runTick} is one iteration of the game loop (client ticks,
 * {@code renderFrame}, buffer swap and frame limiter), so the time between two of its starts is the
 * frame time players feel; {@code Minecraft#renderFrame(boolean)} is the frame's render work,
 * present (swap) included, then {@code FramerateLimiter#limitDisplayFPS} when an FPS cap is set.
 */
@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
  @Inject(method = "runTick", at = @At("HEAD"))
  private void relativityBenchmark$frameStart(boolean advanceGameTime, CallbackInfo info) {
    FrameTimings.onFrameStart();
  }

  @Inject(method = "renderFrame", at = @At("HEAD"))
  private void relativityBenchmark$renderStart(boolean advanceGameTime, CallbackInfo info) {
    FrameTimings.onRenderStart();
  }

  @Inject(
      method = "renderFrame",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/FramerateLimiter;limitDisplayFPS(I)V"))
  private void relativityBenchmark$renderWorkEnd(boolean advanceGameTime, CallbackInfo info) {
    FrameTimings.onRenderWorkEnd();
  }

  @Inject(method = "renderFrame", at = @At("RETURN"))
  private void relativityBenchmark$renderEnd(boolean advanceGameTime, CallbackInfo info) {
    FrameTimings.onRenderEnd();
  }
}

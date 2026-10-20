package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.FrameCacheProbe;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev-only: counts item frames submitted per frame. */
@Mixin(ItemFrameRenderer.class)
abstract class ItemFrameSubmitProbeMixin {
  @Inject(
      method =
          "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
      at = @At("HEAD"))
  private void relativityBenchmark$countSubmit(CallbackInfo callback) {
    FrameCacheProbe.onItemFrameSubmit();
  }
}

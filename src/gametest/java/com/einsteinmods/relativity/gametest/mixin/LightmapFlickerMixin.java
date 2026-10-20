package com.einsteinmods.relativity.gametest.mixin;

import com.einsteinmods.relativity.gametest.LightFlicker;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev-only: skips the flicker update while {@link LightFlicker} holds it. Never shipped. */
@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapFlickerMixin {
  @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
  private void relativityGametest$holdFlicker(CallbackInfo callback) {
    if (LightFlicker.isHeld()) {
      callback.cancel();
    }
  }
}

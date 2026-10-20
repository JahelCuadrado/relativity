package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.UserInputGate;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev-only: drops keyboard input during a benchmark session ({@link UserInputGate}). Vanilla {@code
 * KeyboardHandler#keyPress} opens the pause screen on Escape; {@code charTyped} feeds screens.
 */
@Mixin(KeyboardHandler.class)
abstract class KeyboardInputMixin {
  @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
  private void relativityBenchmark$dropKey(
      long handle, int action, KeyEvent event, CallbackInfo info) {
    if (UserInputGate.isClosed()) {
      info.cancel();
    }
  }

  @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
  private void relativityBenchmark$dropChar(long handle, CharacterEvent event, CallbackInfo info) {
    if (UserInputGate.isClosed()) {
      info.cancel();
    }
  }
}

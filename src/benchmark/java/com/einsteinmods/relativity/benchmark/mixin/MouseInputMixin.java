package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.UserInputGate;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev-only: drops mouse input during a benchmark session ({@link UserInputGate}) and never grabs
 * the cursor. Vanilla {@code MouseHandler#grabMouse} captures the cursor whenever the game window
 * is active (it is, right after launch on macOS), and {@code onMove} turns the scene camera.
 */
@Mixin(MouseHandler.class)
abstract class MouseInputMixin {
  @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
  private void relativityBenchmark$keepCursorFree(CallbackInfo info) {
    if (UserInputGate.isClosed()) {
      info.cancel();
    }
  }

  @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
  private void relativityBenchmark$dropButton(
      long handle, MouseButtonInfo buttonInfo, int action, CallbackInfo info) {
    if (UserInputGate.isClosed()) {
      info.cancel();
    }
  }

  @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
  private void relativityBenchmark$dropScroll(
      long handle, double xOffset, double yOffset, CallbackInfo info) {
    if (UserInputGate.isClosed()) {
      info.cancel();
    }
  }

  @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
  private void relativityBenchmark$dropMove(
      long handle, double x, double y, double xRelative, double yRelative, CallbackInfo info) {
    if (UserInputGate.isClosed()) {
      info.cancel();
    }
  }
}

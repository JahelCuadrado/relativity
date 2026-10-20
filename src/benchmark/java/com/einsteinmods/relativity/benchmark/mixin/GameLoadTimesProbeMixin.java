package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.StartupPhases;
import net.minecraft.client.telemetry.TelemetryProperty;
import net.minecraft.client.telemetry.events.GameLoadTimesEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev-only: wall-clock marks of vanilla's load-time telemetry steps ({@code Minecraft} ends the
 * pre-window step once the window exists, and begins and ends the loading-overlay step around the
 * first resource reload).
 */
@Mixin(GameLoadTimesEvent.class)
abstract class GameLoadTimesProbeMixin {
  @Inject(
      method = "beginStep(Lnet/minecraft/client/telemetry/TelemetryProperty;)V",
      at = @At("HEAD"))
  private void relativityBenchmark$stepBegin(
      TelemetryProperty<GameLoadTimesEvent.Measurement> property, CallbackInfo callback) {
    StartupPhases.onStepBegin(property.id());
  }

  @Inject(method = "endStep", at = @At("HEAD"))
  private void relativityBenchmark$stepEnd(
      TelemetryProperty<GameLoadTimesEvent.Measurement> property, CallbackInfo callback) {
    StartupPhases.onStepEnd(property.id());
  }
}

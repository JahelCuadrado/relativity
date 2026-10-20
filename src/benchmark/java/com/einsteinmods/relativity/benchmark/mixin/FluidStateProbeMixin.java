package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.Candidate;
import com.einsteinmods.relativity.benchmark.ProbeClock;
import com.einsteinmods.relativity.benchmark.ProbeMixinPriority;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev-only: inclusive time of {@code FluidState#randomTick} (only lava ticks randomly). */
@Mixin(value = FluidState.class, priority = ProbeMixinPriority.AFTER_PACK_MODS)
abstract class FluidStateProbeMixin {
  @Inject(method = "randomTick", at = @At("HEAD"))
  private void relativityBenchmark$fluidStart(CallbackInfo callback) {
    ProbeClock.SERVER.begin(Candidate.FLUID_RANDOM_TICK);
  }

  @Inject(method = "randomTick", at = @At("RETURN"))
  private void relativityBenchmark$fluidEnd(CallbackInfo callback) {
    ProbeClock.SERVER.end(Candidate.FLUID_RANDOM_TICK);
  }
}

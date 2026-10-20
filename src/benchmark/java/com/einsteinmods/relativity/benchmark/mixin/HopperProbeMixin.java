package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.Candidate;
import com.einsteinmods.relativity.benchmark.ProbeClock;
import com.einsteinmods.relativity.benchmark.ProbeMixinPriority;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev-only: inclusive time of {@code HopperBlockEntity#pushItemsTick}, the ticker of every hopper
 * (server only). Lithium keeps it as the entry point and unregisters sleeping hoppers instead.
 */
@Mixin(value = HopperBlockEntity.class, priority = ProbeMixinPriority.AFTER_PACK_MODS)
abstract class HopperProbeMixin {
  @Inject(method = "pushItemsTick", at = @At("HEAD"))
  private static void relativityBenchmark$hopperStart(CallbackInfo callback) {
    ProbeClock.SERVER.begin(Candidate.HOPPER_TICK);
  }

  @Inject(method = "pushItemsTick", at = @At("RETURN"))
  private static void relativityBenchmark$hopperEnd(CallbackInfo callback) {
    ProbeClock.SERVER.end(Candidate.HOPPER_TICK);
  }
}

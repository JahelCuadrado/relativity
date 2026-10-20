package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.Candidate;
import com.einsteinmods.relativity.benchmark.ProbeClock;
import com.einsteinmods.relativity.benchmark.ProbeMixinPriority;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev-only: inclusive time of {@code Brain#tick} for villagers (sensors, behaviour start and tick,
 * including the paths their behaviours ask for), called from {@code Villager#customServerAiStep}.
 */
@Mixin(value = Brain.class, priority = ProbeMixinPriority.AFTER_PACK_MODS)
abstract class BrainProbeMixin {
  @Inject(method = "tick", at = @At("HEAD"))
  private void relativityBenchmark$brainStart(
      ServerLevel level, LivingEntity body, CallbackInfo callback) {
    if (body instanceof Villager) {
      ProbeClock.SERVER.begin(Candidate.VILLAGER_BRAIN);
    }
  }

  @Inject(method = "tick", at = @At("RETURN"))
  private void relativityBenchmark$brainEnd(
      ServerLevel level, LivingEntity body, CallbackInfo callback) {
    if (body instanceof Villager) {
      ProbeClock.SERVER.end(Candidate.VILLAGER_BRAIN);
    }
  }
}

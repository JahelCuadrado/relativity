package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.Candidate;
import com.einsteinmods.relativity.benchmark.ProbeClock;
import com.einsteinmods.relativity.benchmark.ProbeMixinPriority;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dev-only: inclusive time of the {@code PathNavigation#createPath} overload every other one ends
 * in: the {@code PathNavigationRegion} snapshot of nearby chunks plus {@code PathFinder#findPath}.
 */
@Mixin(value = PathNavigation.class, priority = ProbeMixinPriority.AFTER_PACK_MODS)
abstract class PathNavigationProbeMixin {
  private static final String CREATE_PATH =
      "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;";

  @Inject(method = CREATE_PATH, at = @At("HEAD"))
  private void relativityBenchmark$createStart(CallbackInfoReturnable<Path> callback) {
    ProbeClock.SERVER.begin(Candidate.CREATE_PATH);
  }

  @Inject(method = CREATE_PATH, at = @At("RETURN"))
  private void relativityBenchmark$createEnd(CallbackInfoReturnable<Path> callback) {
    ProbeClock.SERVER.end(Candidate.CREATE_PATH);
  }
}

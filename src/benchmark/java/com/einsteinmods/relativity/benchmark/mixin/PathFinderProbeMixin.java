package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.Candidate;
import com.einsteinmods.relativity.benchmark.ProbeClock;
import com.einsteinmods.relativity.benchmark.ProbeMixinPriority;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dev-only: inclusive time of the public {@code PathFinder#findPath} (the A* search). */
@Mixin(value = PathFinder.class, priority = ProbeMixinPriority.AFTER_PACK_MODS)
abstract class PathFinderProbeMixin {
  private static final String FIND_PATH =
      "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;";

  @Inject(method = FIND_PATH, at = @At("HEAD"))
  private void relativityBenchmark$findStart(CallbackInfoReturnable<Path> callback) {
    ProbeClock.SERVER.begin(Candidate.FIND_PATH);
  }

  @Inject(method = FIND_PATH, at = @At("RETURN"))
  private void relativityBenchmark$findEnd(CallbackInfoReturnable<Path> callback) {
    ProbeClock.SERVER.end(Candidate.FIND_PATH);
  }
}

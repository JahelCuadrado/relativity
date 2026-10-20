package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.Candidate;
import com.einsteinmods.relativity.benchmark.ProbeClock;
import com.einsteinmods.relativity.benchmark.ProbeMixinPriority;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev-only: inclusive time of {@code ServerLevel#tickChunk} (precipitation and the random-tick loop
 * of one chunk; Lithium replaces the loop through an expression in it) and of the {@code
 * tickBlockEntities} call in {@code ServerLevel#tick} (every ticking block entity, server side).
 */
@Mixin(value = ServerLevel.class, priority = ProbeMixinPriority.AFTER_PACK_MODS)
abstract class ServerLevelProbeMixin {
  private static final String TICK_BLOCK_ENTITIES =
      "Lnet/minecraft/server/level/ServerLevel;tickBlockEntities()V";

  @Inject(method = "tickChunk", at = @At("HEAD"))
  private void relativityBenchmark$chunkStart(CallbackInfo callback) {
    ProbeClock.SERVER.begin(Candidate.CHUNK_TICK);
  }

  @Inject(method = "tickChunk", at = @At("RETURN"))
  private void relativityBenchmark$chunkEnd(CallbackInfo callback) {
    ProbeClock.SERVER.end(Candidate.CHUNK_TICK);
  }

  @Inject(method = "tick", at = @At(value = "INVOKE", target = TICK_BLOCK_ENTITIES))
  private void relativityBenchmark$blockEntitiesStart(CallbackInfo callback) {
    ProbeClock.SERVER.begin(Candidate.BLOCK_ENTITY_TICKS);
  }

  @Inject(
      method = "tick",
      at = @At(value = "INVOKE", target = TICK_BLOCK_ENTITIES, shift = At.Shift.AFTER))
  private void relativityBenchmark$blockEntitiesEnd(CallbackInfo callback) {
    ProbeClock.SERVER.end(Candidate.BLOCK_ENTITY_TICKS);
  }
}

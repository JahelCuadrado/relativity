package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.Candidate;
import com.einsteinmods.relativity.benchmark.ProbeClock;
import com.einsteinmods.relativity.benchmark.ProbeMixinPriority;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev-only: inclusive time of {@code BlockStateBase#randomTick}, the block's own random-tick work
 * (vanilla's {@code ServerLevel#tickChunk} loop and Lithium's replacement both call it), so the
 * report can split chunk ticking into block work and loop overhead.
 */
@Mixin(value = BlockBehaviour.BlockStateBase.class, priority = ProbeMixinPriority.AFTER_PACK_MODS)
abstract class BlockRandomTickProbeMixin {
  @Inject(method = "randomTick", at = @At("HEAD"))
  private void relativityBenchmark$blockStart(CallbackInfo callback) {
    ProbeClock.SERVER.begin(Candidate.BLOCK_RANDOM_TICK);
  }

  @Inject(method = "randomTick", at = @At("RETURN"))
  private void relativityBenchmark$blockEnd(CallbackInfo callback) {
    ProbeClock.SERVER.end(Candidate.BLOCK_RANDOM_TICK);
  }
}

package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.Candidate;
import com.einsteinmods.relativity.benchmark.ProbeClock;
import com.einsteinmods.relativity.benchmark.ProbeMixinPriority;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev-only: inclusive time of vanilla {@code LevelExtractor#extractVisibleEntities} (visibility
 * check and render-state extract of every entity in the level) and of its per-entity {@code
 * extractEntity} call, timed at the call site so EntityCulling's handler inside it is included.
 */
@Mixin(value = LevelExtractor.class, priority = ProbeMixinPriority.AFTER_PACK_MODS)
abstract class LevelExtractorProbeMixin {
  private static final String EXTRACT_ENTITY =
      "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;";

  @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
  private void relativityBenchmark$visibleStart(CallbackInfo callback) {
    ProbeClock.RENDER.begin(Candidate.EXTRACT_VISIBLE_ENTITIES);
  }

  @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
  private void relativityBenchmark$visibleEnd(CallbackInfo callback) {
    ProbeClock.RENDER.end(Candidate.EXTRACT_VISIBLE_ENTITIES);
  }

  @Inject(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = EXTRACT_ENTITY))
  private void relativityBenchmark$extractStart(CallbackInfo callback) {
    ProbeClock.RENDER.begin(Candidate.EXTRACT_ENTITY);
  }

  @Inject(
      method = "extractVisibleEntities",
      at = @At(value = "INVOKE", target = EXTRACT_ENTITY, shift = At.Shift.AFTER))
  private void relativityBenchmark$extractEnd(CallbackInfo callback) {
    ProbeClock.RENDER.end(Candidate.EXTRACT_ENTITY);
  }
}

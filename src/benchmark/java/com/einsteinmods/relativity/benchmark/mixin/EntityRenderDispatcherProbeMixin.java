package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.Candidate;
import com.einsteinmods.relativity.benchmark.ProbeClock;
import com.einsteinmods.relativity.benchmark.ProbeMixinPriority;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dev-only: inclusive time of {@code EntityRenderDispatcher#getRenderer(Entity)} (a type switch and
 * a map lookup in vanilla; BadOptimizations overwrites it with a per-entity cache).
 */
@Mixin(value = EntityRenderDispatcher.class, priority = ProbeMixinPriority.AFTER_PACK_MODS)
abstract class EntityRenderDispatcherProbeMixin {
  private static final String GET_RENDERER =
      "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;";

  @Inject(method = GET_RENDERER, at = @At("HEAD"))
  private void relativityBenchmark$rendererStart(
      CallbackInfoReturnable<EntityRenderer<?, ?>> callback) {
    ProbeClock.RENDER.begin(Candidate.GET_RENDERER);
  }

  @Inject(method = GET_RENDERER, at = @At("RETURN"))
  private void relativityBenchmark$rendererEnd(
      CallbackInfoReturnable<EntityRenderer<?, ?>> callback) {
    ProbeClock.RENDER.end(Candidate.GET_RENDERER);
  }
}

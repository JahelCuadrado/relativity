package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.frames.ItemFrameExtractCache;
import com.einsteinmods.relativity.client.frames.RetainedFrameStateHolder;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets {@link ItemFrameExtractCache} hand an item frame last frame's render state: in {@code
 * EntityRenderer#createRenderState(Entity, float)}, the {@code createRenderState()} call that
 * allocates a new state before {@code #extractRenderState} fills it.
 *
 * <p>Cost: one {@code instanceof} per extracted entity per frame, no allocation (a redirect, not a
 * callback).
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
  @Redirect(
      method =
          "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/EntityRenderer;createRenderState()Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
  private EntityRenderState relativity$keepItemFrameState(
      EntityRenderer<?, ?> renderer, @Local(argsOnly = true) Entity entity) {
    if (entity instanceof ItemFrame frame && frame instanceof RetainedFrameStateHolder holder) {
      EntityRenderState kept = ItemFrameExtractCache.INSTANCE.stateFor(renderer, frame, holder);
      if (kept != null) {
        return kept;
      }
    }
    return renderer.createRenderState();
  }
}

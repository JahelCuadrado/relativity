package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.frames.ItemFrameExtractCache;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Skips, for a state {@link ItemFrameExtractCache} kept, the two resolutions of {@code
 * ItemFrameRenderer#extractRenderState} whose result the state already holds: {@code
 * ItemModelResolver#updateForNonLiving} (the item) and {@code BlockModelResolver
 * #updateForItemFrame} (the frame's wood). Everything else in the method runs as vanilla.
 *
 * <p>Cost: one identity compare per item frame per frame for each call, no allocation.
 */
@Mixin(ItemFrameRenderer.class)
abstract class ItemFrameExtractMixin {
  private static final String EXTRACT =
      "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V";

  @Redirect(
      method = EXTRACT,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/item/ItemModelResolver;updateForNonLiving(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/Entity;)V"))
  private void relativity$keepItemModel(
      ItemModelResolver resolver,
      ItemStackRenderState output,
      ItemStack stack,
      ItemDisplayContext displayContext,
      Entity entity,
      @Local(argsOnly = true) ItemFrameRenderState state) {
    if (!ItemFrameExtractCache.INSTANCE.keepItemModel(state)) {
      resolver.updateForNonLiving(output, stack, displayContext, entity);
    }
  }

  @Redirect(
      method = EXTRACT,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/block/BlockModelResolver;updateForItemFrame(Lnet/minecraft/client/renderer/block/BlockModelRenderState;ZZ)V"))
  private void relativity$keepFrameModel(
      BlockModelResolver resolver,
      BlockModelRenderState frameModel,
      boolean glowing,
      boolean map,
      @Local(argsOnly = true) ItemFrameRenderState state) {
    if (!ItemFrameExtractCache.INSTANCE.keepFrameModel(state, glowing, map)) {
      resolver.updateForItemFrame(frameModel, glowing, map);
    }
  }
}

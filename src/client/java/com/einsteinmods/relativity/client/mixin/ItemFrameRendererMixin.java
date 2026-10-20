package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.frames.ItemFrameGeometryCache;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Marks the submits of an item frame for {@link ItemFrameGeometryCache}: in {@code
 * ItemFrameRenderer#submit}, the frame model ({@code BlockModelRenderState#submitWithZOffset}) and
 * the item ({@code ItemStackRenderState#submit}). Maps ({@code MapRenderer#render}) and everything
 * {@code EntityRenderer#submit} adds (name tag) are not marked and stay vanilla.
 *
 * <p>Cost: two calls per item frame per frame, no allocation (redirects, not callbacks).
 */
@Mixin(ItemFrameRenderer.class)
abstract class ItemFrameRendererMixin {
  @Redirect(
      method =
          "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/block/BlockModelRenderState;submitWithZOffset(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
  private void relativity$markFrameModel(
      BlockModelRenderState frameModel,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      int lightCoords,
      int overlayCoords,
      int outlineColor,
      @Local(argsOnly = true) ItemFrameRenderState state) {
    ItemFrameGeometryCache.INSTANCE.beginFrameSubmit(state, ItemFrameGeometryCache.FRAME_ORDINAL);
    frameModel.submitWithZOffset(poseStack, collector, lightCoords, overlayCoords, outlineColor);
    ItemFrameGeometryCache.INSTANCE.endFrameSubmit();
  }

  @Redirect(
      method =
          "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
  private void relativity$markItem(
      ItemStackRenderState item,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      int lightCoords,
      int overlayCoords,
      int outlineColor,
      @Local(argsOnly = true) ItemFrameRenderState state) {
    ItemFrameGeometryCache.INSTANCE.beginFrameSubmit(state, ItemFrameGeometryCache.ITEM_ORDINAL);
    item.submit(poseStack, collector, lightCoords, overlayCoords, outlineColor);
    ItemFrameGeometryCache.INSTANCE.endFrameSubmit();
  }
}

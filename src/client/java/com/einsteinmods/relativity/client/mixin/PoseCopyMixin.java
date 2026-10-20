package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.frames.ItemFrameGeometryCache;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hands {@link ItemFrameGeometryCache} every pose copy made while an item frame submits: the copy
 * each submit keeps ({@code SubmitNodeCollection#submitItem}, and Fabric API's {@code
 * SubmitNodeCollectionMixin#submitBlockModel} for the frame's wood), so the submit can be
 * recognised when it is built. Hooked at {@code PoseStack.Pose#copy} itself because Fabric's block
 * model submit is a method its own mixin adds.
 *
 * <p>Cost: one boolean check per pose copy when no item frame is submitting; no allocation.
 */
@Mixin(PoseStack.Pose.class)
abstract class PoseCopyMixin {
  @ModifyReturnValue(method = "copy", at = @At("RETURN"))
  private PoseStack.Pose relativity$markPoseCopy(PoseStack.Pose copy) {
    ItemFrameGeometryCache.INSTANCE.onPoseCopied(copy);
    return copy;
  }
}

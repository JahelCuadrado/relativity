package com.einsteinmods.relativity.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Whether {@code PoseStack.Pose#transformNormal} skips normalising: part of the inputs of cached
 * item frame vertices.
 */
@Mixin(PoseStack.Pose.class)
public interface PoseAccessor {
  @Accessor("trustedNormals")
  boolean relativity$trustedNormals();
}

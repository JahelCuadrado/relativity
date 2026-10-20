package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.frames.RetainedFrameState;
import com.einsteinmods.relativity.client.frames.RetainedFrameStateHolder;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives each item frame entity a slot for the render state {@code item_frame_extract_cache} keeps
 * between frames; it is freed with the entity. Only the render thread touches it (integrated server
 * item frames leave it null).
 */
@Mixin(ItemFrame.class)
abstract class ItemFrameMixin implements RetainedFrameStateHolder {
  @Unique private @Nullable RetainedFrameState relativity$retained;

  @Override
  public @Nullable RetainedFrameState relativity$retainedState() {
    return relativity$retained;
  }

  @Override
  public void relativity$setRetainedState(RetainedFrameState retained) {
    relativity$retained = retained;
  }
}

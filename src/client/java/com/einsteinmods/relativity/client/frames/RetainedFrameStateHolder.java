package com.einsteinmods.relativity.client.frames;

import org.jspecify.annotations.Nullable;

/**
 * Implemented by {@code ItemFrame} (mixin): where {@link ItemFrameExtractCache} keeps the entity's
 * {@link RetainedFrameState}, so finding it costs a field read and it goes away with the entity.
 */
public interface RetainedFrameStateHolder {
  @Nullable RetainedFrameState relativity$retainedState();

  void relativity$setRetainedState(RetainedFrameState retained);
}

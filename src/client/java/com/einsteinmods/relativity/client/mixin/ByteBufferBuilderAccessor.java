package com.einsteinmods.relativity.client.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Base address and write offset of the native buffer behind a {@code BufferBuilder}. */
@Mixin(ByteBufferBuilder.class)
public interface ByteBufferBuilderAccessor {
  @Accessor("pointer")
  long relativity$pointer();

  @Accessor("writeOffset")
  long relativity$writeOffset();
}

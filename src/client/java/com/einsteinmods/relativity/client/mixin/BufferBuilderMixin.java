package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.frames.RawVertexSink;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Lets the item frame cache append whole vertices it copied from an earlier frame. {@link
 * #relativity$appendVertices} leaves the builder exactly as vanilla's per-vertex writes do ({@code
 * BufferBuilder#beginVertex} for each vertex, every element filled): same bytes, same vertex count,
 * last vertex complete.
 */
@Mixin(BufferBuilder.class)
abstract class BufferBuilderMixin implements RawVertexSink {
  private static final int MAX_VERTEX_COUNT = 16777215;

  @Shadow @Final private ByteBufferBuilder buffer;
  @Shadow @Final private VertexFormat format;
  @Shadow @Final private int vertexSize;
  @Shadow private long vertexPointer;
  @Shadow private int vertices;
  @Shadow private int elementsToFill;

  @Shadow
  private void ensureBuilding() {}

  @Shadow
  private void endLastVertex() {}

  @Override
  public long relativity$writeOffset() {
    return accessor().relativity$writeOffset();
  }

  @Override
  public long relativity$address(long offset) {
    return accessor().relativity$pointer() + offset;
  }

  @Override
  public VertexFormat relativity$format() {
    return format;
  }

  @Override
  public void relativity$appendVertices(long sourceAddress, int byteCount) {
    ensureBuilding();
    endLastVertex();
    int count = byteCount / vertexSize;
    if (vertices + count > MAX_VERTEX_COUNT) {
      throw new IllegalStateException(
          "Trying to write too many vertices (>" + MAX_VERTEX_COUNT + ") into BufferBuilder");
    }
    long target = buffer.reserve(byteCount);
    MemoryUtil.memCopy(sourceAddress, target, byteCount);
    vertices += count;
    vertexPointer = target + byteCount - vertexSize;
    elementsToFill = 0;
  }

  private ByteBufferBuilderAccessor accessor() {
    if (buffer instanceof ByteBufferBuilderAccessor accessor) {
      return accessor;
    }
    throw new IllegalStateException("ByteBufferBuilder accessor not applied");
  }
}

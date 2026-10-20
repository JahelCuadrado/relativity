package com.einsteinmods.relativity.client.frames;

import com.mojang.renderpearl.api.vertex.VertexFormat;

/**
 * Raw access to a {@code BufferBuilder}, added by {@code BufferBuilderMixin}. Every builder of the
 * shared {@code StagedVertexBuffer} writes into the same {@code ByteBufferBuilder}, so offsets
 * taken from different builders of one frame are comparable.
 */
public interface RawVertexSink {
  /** Offset, inside the backing byte buffer, where the next vertex will be written. */
  long relativity$writeOffset();

  /** Current native address of {@code offset} (the backing buffer may move when it grows). */
  long relativity$address(long offset);

  VertexFormat relativity$format();

  /**
   * Appends {@code byteCount} bytes of complete vertices of this builder's format, as if they had
   * been written one vertex at a time.
   */
  void relativity$appendVertices(long sourceAddress, int byteCount);
}

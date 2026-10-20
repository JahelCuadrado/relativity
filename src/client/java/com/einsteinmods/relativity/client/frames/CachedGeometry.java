package com.einsteinmods.relativity.client.frames;

import com.einsteinmods.relativity.core.PoseChange;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * The vertex bytes one item frame submit wrote last time, plus every input those bytes depend on.
 * Equal inputs mean equal bytes, because the emission is a pure function of them:
 *
 * <ul>
 *   <li>items, {@code ItemFeatureRenderer#prepareMainSubmit}: pose, quads, tints, light, overlay,
 *       foil and display context;
 *   <li>the frame's wood, Indigo's {@code ExtendedBlockModelFeatureRenderer#buildGroup} → {@code
 *       #bufferQuad} → {@code QuadViewImpl#buffer}: pose, the mesh's encoded quads (positions,
 *       colours, UVs, normals, light, tint index, layer, emissive flag), light, overlay, tint
 *       colour and tints.
 * </ul>
 *
 * Owned by the render thread.
 */
final class CachedGeometry {
  /** Which emission path wrote the bytes. */
  enum Kind {
    ITEM,
    MESH
  }

  static final int NORMAL_ELEMENTS = 9;
  static final int VERTICES_PER_QUAD = 4;
  private static final String POSITION_ATTRIBUTE = "Position";
  private static final int FLOAT_BYTES = Float.BYTES;
  private static final int INITIAL_LENGTH = 4;

  final long key;
  long lastUsedFrame;
  boolean valid;
  Kind kind = Kind.ITEM;

  final float[] pose = new float[PoseChange.MATRIX_ELEMENTS];
  final float[] normal = new float[NORMAL_ELEMENTS];
  boolean trustedNormals;
  int lightCoords;
  int overlayCoords;
  int tintColor;
  int[] tints = new int[INITIAL_LENGTH];
  int tintCount;
  ItemStackRenderState.@Nullable FoilType foilType;
  @Nullable ItemDisplayContext displayContext;
  BakedQuad[] quads = new BakedQuad[INITIAL_LENGTH];
  int quadCount;
  int[] meshData = new int[INITIAL_LENGTH];
  int meshLength;
  @Nullable VertexFormat format;

  long address;
  int capacityBytes;
  int byteCount;
  int vertexSize;
  int positionOffset;
  boolean positionsRecomputable;
  RenderType[] runTypes = new RenderType[INITIAL_LENGTH];
  int[] runBytes = new int[INITIAL_LENGTH];
  int runCount;

  CachedGeometry(long key) {
    this.key = key;
  }

  /** Records the inputs of an item submit, before vanilla emits it. */
  void setItemInputs(
      float[] currentPose,
      float[] currentNormal,
      boolean currentTrustedNormals,
      int light,
      int overlay,
      int[] currentTints,
      List<BakedQuad> currentQuads,
      ItemStackRenderState.FoilType currentFoil,
      ItemDisplayContext currentDisplayContext) {
    setCommonInputs(
        Kind.ITEM, currentPose, currentNormal, currentTrustedNormals, light, overlay, currentTints);
    tintColor = 0;
    foilType = currentFoil;
    displayContext = currentDisplayContext;
    int size = currentQuads.size();
    if (quads.length < size) {
      quads = new BakedQuad[size];
    }
    for (int index = 0; index < size; index++) {
      quads[index] = currentQuads.get(index);
    }
    quadCount = size;
    meshLength = 0;
  }

  /** Records the inputs of a mesh submit (the frame's wood), before Indigo emits it. */
  void setMeshInputs(
      float[] currentPose,
      float[] currentNormal,
      boolean currentTrustedNormals,
      int light,
      int overlay,
      int currentTintColor,
      int[] currentTints,
      int[] currentMeshData,
      int currentMeshLength,
      int currentQuadCount) {
    setCommonInputs(
        Kind.MESH, currentPose, currentNormal, currentTrustedNormals, light, overlay, currentTints);
    tintColor = currentTintColor;
    foilType = null;
    displayContext = null;
    Arrays.fill(quads, null);
    quadCount = currentQuadCount;
    if (meshData.length < currentMeshLength) {
      meshData = new int[currentMeshLength];
    }
    System.arraycopy(currentMeshData, 0, meshData, 0, currentMeshLength);
    meshLength = currentMeshLength;
  }

  boolean matchesItem(
      float[] currentNormal,
      boolean currentTrustedNormals,
      int light,
      int overlay,
      int[] currentTints,
      List<BakedQuad> currentQuads,
      ItemStackRenderState.FoilType currentFoil,
      ItemDisplayContext currentDisplayContext) {
    if (kind != Kind.ITEM
        || foilType != currentFoil
        || displayContext != currentDisplayContext
        || quadCount != currentQuads.size()
        || !matchesCommon(currentNormal, currentTrustedNormals, light, overlay, currentTints)) {
      return false;
    }
    for (int index = 0; index < quadCount; index++) {
      if (quads[index] != currentQuads.get(index)) {
        return false;
      }
    }
    return true;
  }

  boolean matchesMesh(
      float[] currentNormal,
      boolean currentTrustedNormals,
      int light,
      int overlay,
      int currentTintColor,
      int[] currentTints,
      int[] currentMeshData,
      int currentMeshLength) {
    return kind == Kind.MESH
        && tintColor == currentTintColor
        && meshLength == currentMeshLength
        && matchesCommon(currentNormal, currentTrustedNormals, light, overlay, currentTints)
        && Arrays.equals(meshData, 0, meshLength, currentMeshData, 0, currentMeshLength);
  }

  /** Starts a new run of vertices for {@code renderType}; its byte count is set later. */
  void addRun(RenderType renderType) {
    if (runTypes.length == runCount) {
      runTypes = Arrays.copyOf(runTypes, runCount * 2);
      runBytes = Arrays.copyOf(runBytes, runCount * 2);
    }
    runTypes[runCount] = renderType;
    runBytes[runCount] = 0;
    runCount++;
  }

  /**
   * Makes room for {@code bytes}; returns the change in reserved native memory, or {@code -1} when
   * the allocation failed (the entry is then left empty).
   */
  long reserve(int bytes) {
    if (capacityBytes >= bytes) {
      return 0;
    }
    long grown = MemoryUtil.nmemRealloc(address, bytes);
    if (grown == MemoryUtil.NULL) {
      return -1;
    }
    long change = (long) bytes - capacityBytes;
    address = grown;
    capacityBytes = bytes;
    return change;
  }

  /** Stores the vertex layout of {@code vertexFormat}; false when positions cannot be located. */
  boolean setLayout(VertexFormat vertexFormat, boolean recomputable) {
    format = vertexFormat;
    vertexSize = vertexFormat.getVertexSize();
    VertexFormatElement position = vertexFormat.getElement(POSITION_ATTRIBUTE);
    positionOffset = position == null ? 0 : position.offset();
    positionsRecomputable = recomputable && position != null;
    return position != null;
  }

  /** Overwrites the position of {@code vertex} in the cached bytes. */
  void writePosition(int vertex, float x, float y, float z) {
    long target = address + (long) vertex * vertexSize + positionOffset;
    MemoryUtil.memPutFloat(target, x);
    MemoryUtil.memPutFloat(target + FLOAT_BYTES, y);
    MemoryUtil.memPutFloat(target + 2L * FLOAT_BYTES, z);
  }

  /** Releases the native memory; returns how many bytes were released. */
  long free() {
    long released = capacityBytes;
    if (address != MemoryUtil.NULL) {
      MemoryUtil.nmemFree(address);
    }
    address = MemoryUtil.NULL;
    capacityBytes = 0;
    byteCount = 0;
    valid = false;
    Arrays.fill(quads, null);
    Arrays.fill(runTypes, null);
    return released;
  }

  private void setCommonInputs(
      Kind currentKind,
      float[] currentPose,
      float[] currentNormal,
      boolean currentTrustedNormals,
      int light,
      int overlay,
      int[] currentTints) {
    kind = currentKind;
    System.arraycopy(currentPose, 0, pose, 0, PoseChange.MATRIX_ELEMENTS);
    System.arraycopy(currentNormal, 0, normal, 0, NORMAL_ELEMENTS);
    trustedNormals = currentTrustedNormals;
    lightCoords = light;
    overlayCoords = overlay;
    if (tints.length < currentTints.length) {
      tints = new int[currentTints.length];
    }
    System.arraycopy(currentTints, 0, tints, 0, currentTints.length);
    tintCount = currentTints.length;
    format = null;
    runCount = 0;
    valid = false;
  }

  private boolean matchesCommon(
      float[] currentNormal,
      boolean currentTrustedNormals,
      int light,
      int overlay,
      int[] currentTints) {
    if (trustedNormals != currentTrustedNormals
        || lightCoords != light
        || overlayCoords != overlay
        || tintCount != currentTints.length
        || !PoseChange.sameBits(normal, currentNormal, NORMAL_ELEMENTS)) {
      return false;
    }
    return Arrays.equals(tints, 0, tintCount, currentTints, 0, tintCount);
  }
}

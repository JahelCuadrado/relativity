package com.einsteinmods.relativity.client.frames;

import com.einsteinmods.relativity.client.mixin.MeshViewImplAccessor;
import com.einsteinmods.relativity.client.mixin.PoseAccessor;
import com.einsteinmods.relativity.config.RelativitySettings;
import com.einsteinmods.relativity.core.IdentityLongTable;
import com.einsteinmods.relativity.core.LongIntTable;
import com.einsteinmods.relativity.core.OptimizationToggles;
import com.einsteinmods.relativity.core.PoseChange;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedBlockModelSubmit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * Reuses the vertex bytes of item frame submits whose inputs did not change ({@code
 * item_frame_cache}). Render thread only.
 *
 * <p>How 26.3 draws an item frame: {@code LevelRenderer#submitFeatures} starts from an identity
 * {@code PoseStack}; {@code EntityRenderDispatcher#submit} translates it by {@code entity - camera}
 * (camera rotation lives in the model-view matrix, {@code LevelRenderer#render}: {@code
 * modelViewStack.mul(viewRotationMatrix)}), then {@code ItemFrameRenderer#submit} submits the
 * frame's wood and the item, each with its own copy of the pose ({@code PoseStack.Pose#copy}):
 *
 * <ul>
 *   <li>wood: {@code BlockModelRenderState#submitWithZOffset}; with Fabric API (a hard dependency)
 *       the model was encoded into a mesh by {@code BlockStateModelWrapperMixin#update} during
 *       extraction, and {@code BlockModelRenderStateMixin#submitMesh} submits it as an {@code
 *       ExtendedBlockModelSubmit}, which Indigo's {@code
 *       ExtendedBlockModelFeatureRenderer#buildGroup} writes through {@code MeshView#outputTo} →
 *       {@code #bufferQuad} → {@code QuadViewImpl#buffer};
 *   <li>item: {@code ItemStackRenderState#submit} → {@code SubmitNodeCollection#submitItem},
 *       written by {@code ItemFeatureRenderer#prepareMainSubmit} with {@code
 *       VertexConsumer#putBakedQuad}.
 * </ul>
 *
 * {@code FeatureRenderDispatcher#prepareFrame} has both write camera-relative vertices into the
 * shared {@code StagedVertexBuffer}, which is uploaded and drawn by render type.
 *
 * <p>So the bytes of a submit depend only on its inputs, the camera position included (through the
 * pose translation). This class tags the pose copies an item frame makes, and when such a submit is
 * built it either
 *
 * <ul>
 *   <li>copies last frame's bytes as they are (same inputs, same pose bits);
 *   <li>copies them after rewriting only the positions with the same arithmetic the emitter uses
 *       ({@code Matrix4f#transformPosition} for items, {@code Vector4f#mul} for meshes), when only
 *       the pose translation changed (the camera or frame moved); or
 *   <li>lets vanilla build them and keeps a copy.
 * </ul>
 *
 * Bytes land in the same builder, at the same point of the same draw as vanilla's, so draw calls,
 * sorting (translucent, OIT), pipelines and state are untouched. Not used (vanilla path) with
 * Sodium or Iris loaded, which replace this emission path, for glowing (outlined) frames, map
 * contents, special item renderers, and any submit that does not follow vanilla's layout.
 *
 * <p>Per-frame cost: one settings read; per item frame, a tag per submit; per submit built, one
 * identity lookup when item frames are visible (nothing otherwise), and for item frame submits an
 * input compare plus a memcpy (or a position rewrite) instead of vanilla's per-vertex emission. No
 * allocation in steady state.
 */
public final class ItemFrameGeometryCache {
  public static final ItemFrameGeometryCache INSTANCE = new ItemFrameGeometryCache();

  /** What the caller must do with the submit it asked about. */
  public enum Outcome {
    /** Not cached: emit it as vanilla does. */
    VANILLA,
    /** Its bytes are already in the buffer: skip vanilla emission. */
    REPLAYED,
    /** Emit it as vanilla does, then call the matching {@code end} method. */
    CAPTURING
  }

  private static final String SODIUM_MOD_ID = "sodium";
  private static final String IRIS_MOD_ID = "iris";
  private static final boolean SUPPORTED =
      !FabricLoader.getInstance().isModLoaded(SODIUM_MOD_ID)
          && !FabricLoader.getInstance().isModLoaded(IRIS_MOD_ID);

  /** Ordinal of the frame model's submit; item layers count up from {@link #ITEM_ORDINAL}. */
  public static final int FRAME_ORDINAL = 0;

  public static final int ITEM_ORDINAL = 16;

  private static final int TRANSLUCENT_ITEM_SUB_KEY = 1;
  private static final int MESH_SUB_KEY = 2;
  private static final long KEY_MULTIPLIER = 0x9E3779B97F4A7C15L;
  private static final int KEY_SHIFT = 31;

  /** Native memory the cache may hold; submits beyond it stay on the vanilla path. */
  private static final long MAX_CACHED_BYTES = 32L * 1024 * 1024;

  /** An entry no frame used for this many frames is released. */
  private static final int STALE_AFTER_FRAMES = 120;

  private static final int SWEEP_INTERVAL_FRAMES = 60;
  private static final int INITIAL_ENTRIES = 64;

  private final IdentityLongTable submitTags = new IdentityLongTable();
  private final LongIntTable slotsByKey = new LongIntTable();
  private @Nullable CachedGeometry[] entries = new CachedGeometry[INITIAL_ENTRIES];
  private int[] freeSlots = new int[INITIAL_ENTRIES];
  private int freeSlotCount;
  private int usedSlotCount;
  private long cachedBytes;

  private boolean active;
  private long frame;
  private long replayedSubmits;
  private long rewrittenSubmits;
  private long capturedSubmits;
  private long replayedMeshSubmits;

  private boolean tagging;
  private long frameKey;
  private int nextOrdinal;

  private final float[] scratchPose = new float[PoseChange.MATRIX_ELEMENTS];
  private final float[] scratchNormal = new float[CachedGeometry.NORMAL_ELEMENTS];
  private final Vector3f scratchPosition = new Vector3f();
  private final MeshPositionRewriter meshRewriter = new MeshPositionRewriter();

  private @Nullable CachedGeometry capture;
  private boolean captureFailed;
  private long captureStart;
  private long runStart;
  private @Nullable RawVertexSink captureSink;
  private @Nullable RenderType captureType;

  private ItemFrameGeometryCache() {}

  /** Called once per level frame, before entities submit. Reads the setting. */
  public void beginFrame() {
    frame++;
    submitTags.clear();
    clearCapture();
    boolean enabled =
        SUPPORTED && RelativitySettings.current().isEnabled(OptimizationToggles.ITEM_FRAME_CACHE);
    if (!enabled && usedSlotCount > 0) {
      releaseAll();
    }
    active = enabled;
    if (active && frame % SWEEP_INTERVAL_FRAMES == 0) {
      releaseStale();
    }
  }

  /** Native memory held by cached vertices, in bytes. */
  public long cachedBytes() {
    return cachedBytes;
  }

  /** Submits whose cached bytes were reused, rewritten or not, since the game started. */
  public long replayedSubmits() {
    return replayedSubmits;
  }

  /** Reused submits whose positions had to be rewritten (the pose translation changed). */
  public long rewrittenSubmits() {
    return rewrittenSubmits;
  }

  /** Reused submits that were the frame's wood (mesh path). */
  public long replayedMeshSubmits() {
    return replayedMeshSubmits;
  }

  /** Submits emitted by vanilla and stored. */
  public long capturedSubmits() {
    return capturedSubmits;
  }

  /** Item frame submits currently cached. */
  public int cachedSubmits() {
    return usedSlotCount;
  }

  /**
   * The next submits until {@link #endFrameSubmit} belong to {@code state}'s item frame; their
   * ordinals start at {@code firstOrdinal}. Outlined frames stay on the vanilla path.
   */
  public void beginFrameSubmit(ItemFrameRenderState state, int firstOrdinal) {
    if (!active || state.outlineColor != 0) {
      return;
    }
    long key = Double.doubleToRawLongBits(state.x);
    key = key * KEY_MULTIPLIER + Double.doubleToRawLongBits(state.y);
    key = key * KEY_MULTIPLIER + Double.doubleToRawLongBits(state.z);
    frameKey = key;
    nextOrdinal = firstOrdinal;
    tagging = true;
  }

  public void endFrameSubmit() {
    tagging = false;
  }

  /** {@code pose} is the copy a submit of the current item frame keeps. */
  public void onPoseCopied(PoseStack.Pose pose) {
    if (tagging) {
      submitTags.put(pose, mix(frameKey, nextOrdinal++));
    }
  }

  /**
   * Before {@code ItemFeatureRenderer#prepareMainSubmit}. {@code source} must be the renderer that
   * would build {@code submit}.
   */
  public Outcome beginItemSubmit(ItemFeatureRenderer.Submit submit, VertexBuilderSource source) {
    if (submitTags.isEmpty()) {
      return Outcome.VANILLA;
    }
    long tag = submitTags.get(submit.pose());
    if (tag == IdentityLongTable.MISSING
        || !((Object) submit.pose() instanceof PoseAccessor poseAccess)) {
      return Outcome.VANILLA;
    }
    List<BakedQuad> quads = submit.quads();
    boolean translucent = quads.get(0).materialInfo().itemRenderType().hasBlending();
    long key = mix(tag, translucent ? TRANSLUCENT_ITEM_SUB_KEY : 0);
    Matrix4f matrix = submit.pose().pose();
    matrix.get(scratchPose);
    submit.pose().normal().get(scratchNormal);
    boolean trusted = poseAccess.relativity$trustedNormals();
    CachedGeometry entry = find(key);
    if (entry != null
        && entry.valid
        && entry.matchesItem(
            scratchNormal,
            trusted,
            submit.lightCoords(),
            submit.overlayCoords(),
            submit.tintLayers(),
            quads,
            submit.foilType(),
            submit.displayContext())
        && prepareReplay(entry, matrix, null)) {
      replayRuns(entry, source);
      return Outcome.REPLAYED;
    }
    entry = obtain(key);
    entry.setItemInputs(
        scratchPose,
        scratchNormal,
        trusted,
        submit.lightCoords(),
        submit.overlayCoords(),
        submit.tintLayers(),
        quads,
        submit.foilType(),
        submit.displayContext());
    startCapture(entry);
    return Outcome.CAPTURING;
  }

  /**
   * Each {@code RenderTypeFeatureRenderer#getVertexBuilder} call; only matters while a submit is
   * being captured, which splits its bytes into runs by render type and builder.
   */
  public void onVertexBuilder(RenderType renderType, VertexConsumer builder) {
    CachedGeometry entry = capture;
    if (entry == null || captureFailed) {
      return;
    }
    if (!(builder instanceof RawVertexSink sink)
        || !renderType.canConsolidateConsecutiveGeometry()
        || (entry.runCount > 0 && sink.relativity$format() != entry.format)) {
      captureFailed = true;
      return;
    }
    long offset = sink.relativity$writeOffset();
    if (entry.runCount == 0) {
      captureStart = offset;
      entry.format = sink.relativity$format();
    }
    if (entry.runCount == 0 || renderType != captureType || sink != captureSink) {
      closeRun(entry, offset);
      entry.addRun(renderType);
      runStart = offset;
    }
    captureType = renderType;
    captureSink = sink;
  }

  /** After vanilla emitted an item submit for which {@link #beginItemSubmit} said CAPTURING. */
  public void endItemSubmit() {
    CachedGeometry entry = capture;
    endCapture(entry != null && entry.foilType != ItemStackRenderState.FoilType.SPECIAL);
  }

  private void endCapture(boolean recomputable) {
    CachedGeometry entry = capture;
    RawVertexSink sink = captureSink;
    if (entry == null) {
      return;
    }
    if (captureFailed || sink == null || entry.format == null) {
      entry.valid = false;
      clearCapture();
      return;
    }
    long end = sink.relativity$writeOffset();
    closeRun(entry, end);
    finishCapture(entry, sink, end, entry.quadCount, recomputable);
  }

  /**
   * Before Indigo's {@code ExtendedBlockModelFeatureRenderer#buildGroup} sends {@code mesh} (the
   * frame's wood: Fabric API's {@code BlockStateModelWrapperMixin#update} encodes every block model
   * into a mesh, {@code BlockModelRenderStateMixin#submitMesh} submits it as an {@code
   * ExtendedBlockModelSubmit}) through {@code MeshView#outputTo} → {@code #bufferQuad}. {@code
   * source} must be that renderer. Submits with model parts or a decal pose stay on the normal
   * path.
   */
  public Outcome beginMeshSubmit(
      ExtendedBlockModelSubmit submit, Mesh mesh, VertexBuilderSource source) {
    if (submitTags.isEmpty()) {
      return Outcome.VANILLA;
    }
    PoseStack.Pose pose = submit.pose();
    long tag = submitTags.get(pose);
    if (tag == IdentityLongTable.MISSING
        || !((Object) pose instanceof PoseAccessor poseAccess)
        || !(mesh instanceof MeshViewImplAccessor meshData)
        || submit.sheetedDecalPose() != null
        || !submit.modelParts().isEmpty()) {
      return Outcome.VANILLA;
    }
    long key = mix(tag, MESH_SUB_KEY);
    Matrix4f matrix = pose.pose();
    matrix.get(scratchPose);
    pose.normal().get(scratchNormal);
    boolean trusted = poseAccess.relativity$trustedNormals();
    int[] data = meshData.relativity$data();
    int limit = meshData.relativity$limit();
    CachedGeometry entry = find(key);
    if (entry != null
        && entry.valid
        && entry.matchesMesh(
            scratchNormal,
            trusted,
            submit.lightCoords(),
            submit.overlayCoords(),
            submit.tintColor(),
            submit.tintLayers(),
            data,
            limit)
        && prepareReplay(entry, matrix, mesh)) {
      replayRuns(entry, source);
      replayedMeshSubmits++;
      return Outcome.REPLAYED;
    }
    entry = obtain(key);
    entry.setMeshInputs(
        scratchPose,
        scratchNormal,
        trusted,
        submit.lightCoords(),
        submit.overlayCoords(),
        submit.tintColor(),
        submit.tintLayers(),
        data,
        limit,
        mesh.size());
    startCapture(entry);
    return Outcome.CAPTURING;
  }

  /** After Indigo emitted a mesh for which {@link #beginMeshSubmit} said CAPTURING. */
  public void endMeshSubmit() {
    endCapture(true);
  }

  /**
   * Readies {@code entry}'s bytes for {@code matrix}: nothing when the pose bits are the same,
   * positions rewritten when only the translation changed. False when it cannot be reused.
   */
  private boolean prepareReplay(CachedGeometry entry, Matrix4f matrix, @Nullable Mesh mesh) {
    PoseChange change = PoseChange.between(entry.pose, scratchPose);
    if (change == PoseChange.OTHER
        || (change == PoseChange.TRANSLATION_ONLY && !entry.positionsRecomputable)) {
      return false;
    }
    if (change == PoseChange.TRANSLATION_ONLY) {
      if (mesh == null) {
        rewriteItemPositions(entry, matrix);
      } else {
        meshRewriter.rewrite(entry, matrix, mesh);
      }
      System.arraycopy(scratchPose, 0, entry.pose, 0, PoseChange.MATRIX_ELEMENTS);
      rewrittenSubmits++;
    }
    replayedSubmits++;
    entry.lastUsedFrame = frame;
    return true;
  }

  private void replayRuns(CachedGeometry entry, VertexBuilderSource source) {
    long address = entry.address;
    for (int run = 0; run < entry.runCount; run++) {
      VertexConsumer builder = source.relativity$vertexBuilder(entry.runTypes[run]);
      if (!(builder instanceof RawVertexSink sink)) {
        throw new IllegalStateException("Vertex builder changed type since it was cached");
      }
      sink.relativity$appendVertices(address, entry.runBytes[run]);
      address += entry.runBytes[run];
    }
  }

  /** Same arithmetic as {@code VertexConsumer#putBakedQuad}: {@code transformPosition}. */
  private void rewriteItemPositions(CachedGeometry entry, Matrix4f matrix) {
    int vertex = 0;
    for (int index = 0; index < entry.quadCount; index++) {
      vertex = rewriteQuad(entry, matrix, entry.quads[index], vertex, scratchPosition);
    }
  }

  /**
   * Rewrites the four positions of {@code quad} with the same call {@code
   * VertexConsumer#putBakedQuad} makes ({@code matrix.transformPosition(quad.position(vertex),
   * …)}), so the floats are bit-identical to vanilla's. Returns the next vertex index.
   */
  static int rewriteQuad(
      CachedGeometry entry, Matrix4f matrix, BakedQuad quad, int firstVertex, Vector3f scratch) {
    for (int corner = 0; corner < CachedGeometry.VERTICES_PER_QUAD; corner++) {
      Vector3fc position = quad.position(corner);
      Vector3f moved = matrix.transformPosition(position, scratch);
      entry.writePosition(firstVertex + corner, moved.x(), moved.y(), moved.z());
    }
    return firstVertex + CachedGeometry.VERTICES_PER_QUAD;
  }

  private void startCapture(CachedGeometry entry) {
    capture = entry;
    captureFailed = false;
    captureSink = null;
    captureType = null;
    entry.lastUsedFrame = frame;
  }

  private void closeRun(CachedGeometry entry, long offset) {
    if (entry.runCount > 0) {
      entry.runBytes[entry.runCount - 1] = (int) (offset - runStart);
    }
  }

  private void finishCapture(
      CachedGeometry entry, RawVertexSink sink, long end, int quadCount, boolean recomputable) {
    clearCapture();
    if (entry.format == null) {
      return;
    }
    boolean layoutKnown = entry.setLayout(entry.format, recomputable);
    long bytes = end - captureStart;
    long expected = (long) quadCount * CachedGeometry.VERTICES_PER_QUAD * entry.vertexSize;
    if (!layoutKnown || bytes != expected || bytes == 0) {
      return;
    }
    int size = (int) bytes;
    if (entry.capacityBytes < size && cachedBytes + size - entry.capacityBytes > MAX_CACHED_BYTES) {
      return;
    }
    long change = entry.reserve(size);
    if (change < 0) {
      return;
    }
    cachedBytes += change;
    MemoryUtil.memCopy(sink.relativity$address(captureStart), entry.address, size);
    entry.byteCount = size;
    entry.valid = true;
    capturedSubmits++;
  }

  private void clearCapture() {
    capture = null;
    captureSink = null;
    captureType = null;
    captureFailed = false;
  }

  private @Nullable CachedGeometry find(long key) {
    int slot = slotsByKey.get(key);
    return slot == LongIntTable.MISSING ? null : entries[slot];
  }

  private CachedGeometry obtain(long key) {
    int slot = slotsByKey.get(key);
    if (slot != LongIntTable.MISSING) {
      CachedGeometry existing = entries[slot];
      if (existing != null) {
        return existing;
      }
    }
    if (freeSlotCount > 0) {
      slot = freeSlots[--freeSlotCount];
    } else {
      slot = usedSlotCount;
      if (slot == entries.length) {
        entries = Arrays.copyOf(entries, slot * 2);
        freeSlots = Arrays.copyOf(freeSlots, slot * 2);
      }
    }
    CachedGeometry created = new CachedGeometry(key);
    entries[slot] = created;
    slotsByKey.put(key, slot);
    usedSlotCount++;
    return created;
  }

  private void releaseStale() {
    long oldest = frame - STALE_AFTER_FRAMES;
    for (int slot = 0; slot < entries.length; slot++) {
      CachedGeometry entry = entries[slot];
      if (entry != null && entry.lastUsedFrame < oldest) {
        release(slot, entry);
      }
    }
  }

  private void releaseAll() {
    for (int slot = 0; slot < entries.length; slot++) {
      CachedGeometry entry = entries[slot];
      if (entry != null) {
        release(slot, entry);
      }
    }
  }

  private void release(int slot, CachedGeometry entry) {
    cachedBytes -= entry.free();
    slotsByKey.remove(entry.key);
    entries[slot] = null;
    freeSlots[freeSlotCount++] = slot;
    usedSlotCount--;
  }

  private static long mix(long key, int ordinal) {
    long mixed = (key + ordinal + 1) * KEY_MULTIPLIER;
    mixed ^= mixed >>> KEY_SHIFT;
    return mixed == IdentityLongTable.MISSING ? mixed + 1 : mixed;
  }
}

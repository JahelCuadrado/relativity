package com.einsteinmods.relativity.client.frames;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einsteinmods.relativity.core.PoseChange;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

/**
 * When only the pose translation changed (camera or frame moved), the positions the cache rewrites
 * must have exactly the bits vanilla's {@code VertexConsumer#putBakedQuad} writes for the new pose.
 */
class PositionRewriteTest {
  private static final long SEED = 7L;
  private static final int TRIALS = 20_000;
  private static final int QUADS_PER_TRIAL = 3;
  private static final double CAMERA_RANGE = 3_000_000.0;
  private static final double OFFSET_RANGE = 64.0;
  private static final float FRAME_OFFSET = 0.46875F;
  private static final float ITEM_DEPTH = 0.4375F;
  private static final float ITEM_SCALE = 0.5F;
  private static final float EIGHTH_TURN = 45.0F;
  private static final int ROTATIONS = 8;
  private static final float MAX_DEGREES = 360.0F;
  private static final int FLOAT_VERTEX_SIZE = 3 * Float.BYTES;
  private static final int VERTICES = QUADS_PER_TRIAL * CachedGeometry.VERTICES_PER_QUAD;
  private static final int MINIMUM_SHARE_DIVISOR = 4;

  private final CachedGeometry entry = new CachedGeometry(0L);

  @AfterEach
  void release() {
    entry.free();
  }

  @Test
  void rewrittenPositionsMatchPutBakedQuadBitForBit() {
    Random random = new Random(SEED);
    entry.vertexSize = FLOAT_VERTEX_SIZE;
    entry.positionOffset = 0;
    assertTrue(entry.reserve(VERTICES * FLOAT_VERTEX_SIZE) > 0);
    Vector3f scratch = new Vector3f();
    int translationOnly = 0;
    for (int trial = 0; trial < TRIALS; trial++) {
      double frameX = (random.nextDouble() - 0.5) * CAMERA_RANGE;
      double frameY = (random.nextDouble() - 0.5) * OFFSET_RANGE;
      double frameZ = (random.nextDouble() - 0.5) * CAMERA_RANGE;
      Shape shape = Shape.random(random);
      PoseStack.Pose before = shape.pose(frameX - camera(random, frameX), frameY, frameZ);
      PoseStack.Pose after = shape.pose(frameX - camera(random, frameX), frameY, frameZ);
      float[] beforeBits = new float[PoseChange.MATRIX_ELEMENTS];
      float[] afterBits = new float[PoseChange.MATRIX_ELEMENTS];
      before.pose().get(beforeBits);
      after.pose().get(afterBits);
      PoseChange change = PoseChange.between(beforeBits, afterBits);
      if (change == PoseChange.OTHER) {
        continue;
      }
      translationOnly++;
      RecordingConsumer vanilla = new RecordingConsumer(VERTICES);
      int vertex = 0;
      for (int quadIndex = 0; quadIndex < QUADS_PER_TRIAL; quadIndex++) {
        BakedQuad quad = randomQuad(random);
        vanilla.putBakedQuad(after, quad, new QuadInstance());
        vertex = ItemFrameGeometryCache.rewriteQuad(entry, after.pose(), quad, vertex, scratch);
      }
      for (int index = 0; index < VERTICES * 3; index++) {
        float rewritten = MemoryUtil.memGetFloat(entry.address + (long) index * Float.BYTES);
        assertEquals(
            Float.floatToRawIntBits(vanilla.positions[index]),
            Float.floatToRawIntBits(rewritten),
            "trial " + trial + " component " + index);
      }
    }
    // Most random shapes keep their rotation bits when only the camera moves.
    assertTrue(
        translationOnly > TRIALS / MINIMUM_SHARE_DIVISOR,
        "translation-only cases: " + translationOnly);
  }

  private static double camera(Random random, double frameCoordinate) {
    return frameCoordinate + (random.nextDouble() - 0.5) * OFFSET_RANGE;
  }

  private static BakedQuad randomQuad(Random random) {
    Vector3f[] corners = new Vector3f[CachedGeometry.VERTICES_PER_QUAD];
    for (int corner = 0; corner < corners.length; corner++) {
      corners[corner] = new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat());
    }
    BakedQuad.MaterialInfo material =
        new BakedQuad.MaterialInfo(null, null, null, null, null, -1, null, 0);
    Direction direction = Direction.values()[random.nextInt(Direction.values().length)];
    return new BakedQuad(
        corners[0], corners[1], corners[2], corners[3], 0L, 0L, 0L, 0L, direction, material);
  }

  /** The operations {@code ItemFrameRenderer#submit} applies after the camera translation. */
  private record Shape(Direction direction, int rotation, Quaternionf extra, float scale) {
    static Shape random(Random random) {
      Direction direction = Direction.values()[random.nextInt(Direction.values().length)];
      Quaternionf extra =
          new Quaternionf()
              .rotateXYZ(
                  random.nextFloat() * MAX_DEGREES,
                  random.nextFloat() * MAX_DEGREES,
                  random.nextFloat() * MAX_DEGREES);
      float scale = random.nextBoolean() ? ITEM_SCALE : random.nextFloat() + ITEM_SCALE;
      return new Shape(direction, random.nextInt(ROTATIONS), extra, scale);
    }

    PoseStack.Pose pose(double x, double y, double z) {
      PoseStack stack = new PoseStack();
      stack.translate(x, y, z);
      stack.translate(
          direction.getStepX() * FRAME_OFFSET,
          direction.getStepY() * FRAME_OFFSET,
          direction.getStepZ() * FRAME_OFFSET);
      stack.rotateDegrees(Axis.XP, direction.getAxis().isHorizontal() ? 0.0F : -EIGHTH_TURN * 2);
      stack.rotateDegrees(Axis.YP, MAX_DEGREES / 2 - direction.toYRot());
      stack.translate(0.0F, 0.0F, ITEM_DEPTH);
      stack.rotateDegrees(Axis.ZP, rotation * EIGHTH_TURN);
      stack.rotate(extra);
      stack.scale(scale, scale, ITEM_SCALE);
      return stack.last().copy();
    }
  }

  /** Records the positions {@code putBakedQuad} writes. */
  private static final class RecordingConsumer implements VertexConsumer {
    final float[] positions;
    private int next;

    RecordingConsumer(int vertices) {
      positions = new float[vertices * 3];
    }

    @Override
    public void addVertex(
        float x,
        float y,
        float z,
        int color,
        float u,
        float v,
        int overlayCoords,
        int lightCoords,
        float normalX,
        float normalY,
        float normalZ) {
      positions[next++] = x;
      positions[next++] = y;
      positions[next++] = z;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
      return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
      return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
      return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
      return this;
    }
  }
}

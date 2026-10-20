package com.einsteinmods.relativity.client.frames;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Rewrites the cached positions of a mesh submit with the arithmetic Indigo's {@code
 * QuadViewImpl#buffer(int, PoseStack.Pose, VertexConsumer)} uses ({@code posVec.set(x, y, z,
 * 1).mul(posMatrix)}), in the order {@code MeshView#outputTo} emits the quads, so the floats are
 * bit-identical. One reusable instance: {@code MeshView#forEach} gets no new lambda per call.
 */
final class MeshPositionRewriter implements Consumer<QuadView> {
  private static final float POINT_W = 1.0F;

  private final Vector4f position = new Vector4f();
  private @Nullable CachedGeometry target;
  private @Nullable Matrix4f matrix;
  private int nextVertex;

  void rewrite(CachedGeometry entry, Matrix4f poseMatrix, MeshView mesh) {
    target = entry;
    matrix = poseMatrix;
    nextVertex = 0;
    mesh.forEach(this);
    target = null;
    matrix = null;
  }

  @Override
  public void accept(QuadView quad) {
    CachedGeometry entry = target;
    Matrix4f poseMatrix = matrix;
    if (entry == null || poseMatrix == null) {
      return;
    }
    for (int corner = 0; corner < CachedGeometry.VERTICES_PER_QUAD; corner++) {
      position.set(quad.x(corner), quad.y(corner), quad.z(corner), POINT_W);
      position.mul(poseMatrix);
      entry.writePosition(nextVertex++, position.x(), position.y(), position.z());
    }
  }
}

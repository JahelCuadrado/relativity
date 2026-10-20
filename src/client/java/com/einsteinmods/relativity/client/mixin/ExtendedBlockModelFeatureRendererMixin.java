package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.frames.ItemFrameGeometryCache;
import com.einsteinmods.relativity.client.frames.VertexBuilderSource;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedBlockModelSubmit;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.ExtendedBlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The item frame's wood: Indigo's {@code ExtendedBlockModelFeatureRenderer#buildGroup} stores the
 * submit in {@code #submit}, then sends its mesh through {@code MeshView#outputTo}, whose quads
 * reach {@code #bufferQuad} → {@code BufferCache#getBuffer} ({@code getVertexBuilder}) → {@code
 * QuadViewImpl#buffer}. {@link ItemFrameGeometryCache} replays or captures that whole call.
 *
 * <p>Indigo is Fabric API's internal renderer: if it changes shape this redirect does not apply
 * ({@code require = 0}) and the wood is simply drawn as before.
 */
@Mixin(value = ExtendedBlockModelFeatureRenderer.class, remap = false)
abstract class ExtendedBlockModelFeatureRendererMixin
    extends RenderTypeFeatureRenderer<ExtendedBlockModelSubmit> implements VertexBuilderSource {
  @Shadow private ExtendedBlockModelSubmit submit;

  @Redirect(
      method = "buildGroup",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/Mesh;outputTo(Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/QuadEmitter;)V"),
      require = 0)
  private void relativity$outputMesh(Mesh mesh, QuadEmitter emitter) {
    ItemFrameGeometryCache.Outcome outcome =
        ItemFrameGeometryCache.INSTANCE.beginMeshSubmit(submit, mesh, this);
    if (outcome == ItemFrameGeometryCache.Outcome.REPLAYED) {
      return;
    }
    mesh.outputTo(emitter);
    if (outcome == ItemFrameGeometryCache.Outcome.CAPTURING) {
      ItemFrameGeometryCache.INSTANCE.endMeshSubmit();
    }
  }

  @Override
  public VertexConsumer relativity$vertexBuilder(RenderType renderType) {
    return getVertexBuilder(renderType);
  }
}

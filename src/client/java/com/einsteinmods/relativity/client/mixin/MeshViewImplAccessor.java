package com.einsteinmods.relativity.client.mixin;

import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MeshViewImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The encoded quads of an Indigo mesh ({@code MeshViewImpl#data} up to {@code #limit}): every
 * per-quad input {@code ExtendedBlockModelFeatureRenderer#bufferQuad} reads. A mesh that is not
 * Indigo's never matches this interface and stays on the normal path.
 */
@Mixin(value = MeshViewImpl.class, remap = false)
public interface MeshViewImplAccessor {
  @Accessor("data")
  int[] relativity$data();

  @Accessor("limit")
  int relativity$limit();
}

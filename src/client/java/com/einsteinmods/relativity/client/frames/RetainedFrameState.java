package com.einsteinmods.relativity.client.frames;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What {@link ItemFrameExtractCache} keeps for one item frame entity: its render state from the
 * last frame and the inputs its item and frame models were resolved from. One per item frame
 * entity, created the first time it is extracted with the optimization on; render thread only.
 */
public final class RetainedFrameState {
  /** The state handed out last frame, or null when this frame's models cannot be kept. */
  @Nullable EntityRenderState state;

  @Nullable EntityRenderer<?, ?> renderer;

  /** {@code ModelManager#getBlockModelSet} when resolved: replaced on every resource reload. */
  @Nullable Object modelGeneration;

  /** The frame's item stack when resolved, compared by identity. */
  @Nullable ItemStack stack;

  boolean invisible;
  boolean itemModelStatic;
  boolean frameModelStatic;

  /** The {@code map} argument the frame model was last resolved with. */
  boolean frameModelMap;

  /** Entity pass that last extracted this frame; a second extraction in one pass gets vanilla. */
  long lastPass;

  RetainedFrameState() {}
}

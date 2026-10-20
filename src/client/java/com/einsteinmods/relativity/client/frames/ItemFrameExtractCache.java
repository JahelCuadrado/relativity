package com.einsteinmods.relativity.client.frames;

import com.einsteinmods.relativity.config.RelativitySettings;
import com.einsteinmods.relativity.core.OptimizationToggles;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Lets an item frame whose item, visibility and loaded models did not change keep last frame's
 * render state instead of resolving its item and frame models again ({@code
 * item_frame_extract_cache}). Render thread only.
 *
 * <p>How 26.3 extracts an item frame, once per visible entity per frame ({@code
 * LevelExtractor#extractVisibleEntities} → {@code EntityRenderDispatcher#extractEntity} → {@code
 * EntityRenderer#createRenderState(Entity, float)}): a new {@code ItemFrameRenderState} (with new
 * block, item and map sub-states) is allocated every frame and filled by {@code
 * ItemFrameRenderer#extractRenderState}:
 *
 * <ul>
 *   <li>{@code EntityRenderer#extractRenderState}: interpolated position, age, name tag and camera
 *       distance, leash, glowing outline colour, packed light (two light lookups). Per-frame:
 *       always runs;
 *   <li>direction, rotation, glow flag, and for filled maps {@code MapRenderer#extractRenderState}
 *       (map texture upload when the map changed, decorations). Always runs;
 *   <li>{@code ItemModelResolver#updateForNonLiving}: walks the item model tree, computes tints,
 *       and with Fabric API copies mesh items into the layer ({@code CuboidItemModelWrapperMixin}).
 *       Skipped when kept;
 *   <li>{@code BlockModelResolver#updateForItemFrame}: the frame's wood, which Fabric API's {@code
 *       BlockStateModelWrapperMixin#update} ({@code @Overwrite}) re-encodes into a new mutable mesh
 *       every frame ({@code setupMesh} + {@code emitQuads}). Skipped when kept (the
 *       {@code @Overwrite} is not touched: its call is simply not made).
 * </ul>
 *
 * A kept state is the same object vanilla filled on an earlier frame, so its item and frame model
 * sub-states hold exactly what resolving them again would produce: the stack (by identity: the
 * client replaces it on every sync, {@code EntityDataSerializers.ITEM_STACK} copies), the entity's
 * level and id (the model seed) and the models ({@code ModelManager#getBlockModelSet}, replaced
 * together with the item models on reload) are the same, and {@link StaticFrameModels} only admits
 * models that read nothing else. Visibility changes which frame model branch runs, so it is
 * compared too; a filled map that appears later changes the frame model's {@code map} argument,
 * which {@link #keepFrameModel} compares. Anything else gets vanilla's new state. Submits only read
 * the state (Fabric API copies the frame mesh at submit), so last frame's draw never sees a change.
 * Off with Iris, whose shadow pass may extract the same entity again.
 *
 * <p>Per-frame cost: one settings read; per extracted entity, one {@code instanceof}; per item
 * frame, a field read and five identity or flag compares. No allocation in steady state (one {@link
 * RetainedFrameState} per item frame entity, once); it saves vanilla's per-frame state allocation
 * for every kept frame.
 */
public final class ItemFrameExtractCache {
  public static final ItemFrameExtractCache INSTANCE = new ItemFrameExtractCache();

  private static final String IRIS_MOD_ID = "iris";
  private static final boolean SUPPORTED = !FabricLoader.getInstance().isModLoaded(IRIS_MOD_ID);

  private boolean active;
  private boolean inEntityPass;
  private long pass;
  private @Nullable ModelManager modelManager;
  private @Nullable Object modelGeneration;

  /** The state handed out kept for the item frame being extracted, if any. */
  private @Nullable EntityRenderState keptState;

  /** Retained data of the item frame being extracted when its state may be kept next frame. */
  private @Nullable RetainedFrameState current;

  private long keptStates;
  private long retainedStates;
  private long vanillaStates;

  private ItemFrameExtractCache() {}

  /**
   * Around {@code LevelExtractor#extractVisibleEntities}: only that loop's extractions are kept.
   * Reads the setting.
   */
  public void beginEntityPass() {
    pass++;
    keptState = null;
    current = null;
    active =
        SUPPORTED
            && RelativitySettings.current().isEnabled(OptimizationToggles.ITEM_FRAME_EXTRACT_CACHE);
    if (active) {
      ModelManager models = Minecraft.getInstance().getModelManager();
      modelManager = models;
      modelGeneration = models.getBlockModelSet();
    }
    inEntityPass = true;
  }

  public void endEntityPass() {
    inEntityPass = false;
    keptState = null;
    current = null;
  }

  /** States reused from the previous frame since the game started. */
  public long keptStates() {
    return keptStates;
  }

  /** New states resolved and retained for reuse. */
  public long retainedStates() {
    return retainedStates;
  }

  /** Item frame states left to vanilla because their models change on their own. */
  public long vanillaStates() {
    return vanillaStates;
  }

  /**
   * Replaces {@code renderer.createRenderState()} in {@code
   * EntityRenderer#createRenderState(Entity, float)} for an item frame: last frame's state when it
   * can be kept, a new one (retained) when it can be next frame, or null for vanilla's.
   */
  public @Nullable EntityRenderState stateFor(
      EntityRenderer<?, ?> renderer, ItemFrame frame, RetainedFrameStateHolder holder) {
    ModelManager models = modelManager;
    if (!active || !inEntityPass || models == null) {
      return null;
    }
    RetainedFrameState retained = holder.relativity$retainedState();
    if (retained == null) {
      retained = new RetainedFrameState();
      holder.relativity$setRetainedState(retained);
    }
    keptState = null;
    current = null;
    if (retained.lastPass == pass) {
      return null;
    }
    retained.lastPass = pass;
    ItemStack stack = frame.getItem();
    boolean invisible = frame.isInvisible();
    boolean sameInputs =
        retained.stack == stack
            && retained.renderer == renderer
            && retained.modelGeneration == modelGeneration
            && retained.invisible == invisible;
    if (sameInputs && !(retained.itemModelStatic && retained.frameModelStatic)) {
      vanillaStates++;
      return null;
    }
    EntityRenderState state = retained.state;
    if (sameInputs && state != null) {
      keptState = state;
      current = retained;
      keptStates++;
      return state;
    }
    retained.stack = stack;
    retained.renderer = renderer;
    retained.modelGeneration = modelGeneration;
    retained.invisible = invisible;
    retained.frameModelStatic = true;
    retained.itemModelStatic =
        StaticFrameModels.isStaticItem(
            models,
            stack,
            frame.level() instanceof ClientLevel clientLevel ? clientLevel : null,
            frame.getId());
    if (!retained.itemModelStatic) {
      retained.state = null;
      vanillaStates++;
      return null;
    }
    EntityRenderState created = renderer.createRenderState();
    retained.state = created;
    current = retained;
    retainedStates++;
    return created;
  }

  /**
   * Instead of {@code ItemModelResolver#updateForNonLiving} in {@code
   * ItemFrameRenderer#extractRenderState}: true when {@code state} is a kept state, whose item
   * sub-state already holds the result.
   */
  public boolean keepItemModel(ItemFrameRenderState state) {
    return state == keptState;
  }

  /**
   * Instead of {@code BlockModelResolver#updateForItemFrame}: true when {@code state} is a kept
   * state last resolved with the same {@code map} argument. Otherwise the caller resolves, and a
   * retained state records what it is resolved with.
   */
  public boolean keepFrameModel(ItemFrameRenderState state, boolean glowing, boolean map) {
    RetainedFrameState retained = current;
    ModelManager models = modelManager;
    if (retained == null || retained.state != state || models == null) {
      return false;
    }
    if (state == keptState && retained.frameModelMap == map) {
      return true;
    }
    retained.frameModelMap = map;
    retained.frameModelStatic = StaticFrameModels.isStaticFrameModel(models, glowing, map);
    return false;
  }
}

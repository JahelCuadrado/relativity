package com.einsteinmods.relativity.client.frames;

import com.einsteinmods.relativity.client.mixin.BlockStateModelWrapperAccessor;
import com.einsteinmods.relativity.client.mixin.CompositeModelAccessor;
import com.einsteinmods.relativity.client.mixin.CuboidItemModelWrapperAccessor;
import com.einsteinmods.relativity.client.mixin.RangeSelectItemModelAccessor;
import com.einsteinmods.relativity.client.mixin.SelectItemModelAccessor;
import java.util.List;
import java.util.Set;
import net.minecraft.client.color.item.Constant;
import net.minecraft.client.color.item.CustomModelDataSource;
import net.minecraft.client.color.item.Dye;
import net.minecraft.client.color.item.Firework;
import net.minecraft.client.color.item.GrassColorSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.Potion;
import net.minecraft.client.color.item.TeamColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.EmptyModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.item.RangeSelectItemModel;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.renderer.item.properties.numeric.BundleFullness;
import net.minecraft.client.renderer.item.properties.numeric.Cooldown;
import net.minecraft.client.renderer.item.properties.numeric.Count;
import net.minecraft.client.renderer.item.properties.numeric.CrossbowPull;
import net.minecraft.client.renderer.item.properties.numeric.Damage;
import net.minecraft.client.renderer.item.properties.numeric.UseCycle;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.client.renderer.item.properties.select.Charge;
import net.minecraft.client.renderer.item.properties.select.ComponentContents;
import net.minecraft.client.renderer.item.properties.select.ContextDimension;
import net.minecraft.client.renderer.item.properties.select.ContextEntityType;
import net.minecraft.client.renderer.item.properties.select.DisplayContext;
import net.minecraft.client.renderer.item.properties.select.ItemBlockState;
import net.minecraft.client.renderer.item.properties.select.MainHand;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.client.renderer.item.properties.select.TrimMaterialProperty;
import net.minecraft.client.resources.model.BlockStateDefinitions;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Whether resolving an item frame's item model ({@code ItemModelResolver#updateForNonLiving}) or
 * frame model ({@code BlockModelResolver#updateForItemFrame}) again would give the same result
 * while the stack, the frame entity and the loaded models stay the same. Only vanilla model and
 * property classes known to read nothing else qualify (exact classes, so a subclass or another
 * mod's model never does); everything else is re-resolved every frame as vanilla does.
 *
 * <p>The item frame is the model's {@code ItemOwner} and is not a living entity, so vanilla passes
 * a null {@code LivingEntity} to properties and tints ({@code SelectItemModel#update}, {@code
 * CuboidItemModelWrapper#update}) and owner-based numeric properties read {@code
 * ItemOwner#asLivingEntity} (null): those results are constant. Excluded because they change on
 * their own: {@code RangeSelectItemModel} with {@code Time} or {@code CompassAngle} (clock,
 * compasses), {@code SelectItemModel} with {@code LocalTime}, every {@code ConditionalItemModel}
 * (its property is wrapped in a registry-swapping lambda by {@code ConditionalItemModel.Unbaked
 * #adaptProperty}, so it cannot be told apart from {@code extended_view} or {@code keybind_down}),
 * and every {@code SpecialModelWrapper} (a player head's argument changes when its skin loads).
 */
final class StaticFrameModels {
  /** Deeper item model trees are treated as dynamic; vanilla's are at most a few levels deep. */
  private static final int MAX_DEPTH = 16;

  /** {@code ItemTintSource#calculate} of these reads only the stack and a null owner. */
  private static final Set<Class<?>> STATIC_TINTS =
      Set.of(
          Constant.class,
          CustomModelDataSource.class,
          Dye.class,
          Firework.class,
          GrassColorSource.class,
          Potion.class,
          TeamColor.class);

  /** {@code SelectItemModelProperty#get} of these reads only the stack, level and display. */
  private static final Set<Class<?>> STATIC_SELECT_PROPERTIES =
      Set.of(
          Charge.class,
          ComponentContents.class,
          ContextDimension.class,
          ContextEntityType.class,
          net.minecraft.client.renderer.item.properties.select.CustomModelDataProperty.class,
          DisplayContext.class,
          ItemBlockState.class,
          MainHand.class,
          TrimMaterialProperty.class);

  /** {@code RangeSelectItemModelProperty#get} of these reads only the stack or a null owner. */
  private static final Set<Class<?>> STATIC_RANGE_PROPERTIES =
      Set.of(
          BundleFullness.class,
          Cooldown.class,
          Count.class,
          CrossbowPull.class,
          net.minecraft.client.renderer.item.properties.numeric.CustomModelDataProperty.class,
          Damage.class,
          UseCycle.class,
          UseDuration.class);

  /** Block state models whose parts depend only on the state and the fixed seed. */
  private static final Set<Class<?>> STATIC_BLOCK_STATE_MODELS =
      Set.of(SingleVariant.class, WeightedVariants.class);

  private StaticFrameModels() {}

  /**
   * Same lookup as {@code ItemModelResolver#appendItemLayers}: no {@code ITEM_MODEL} (or an empty
   * stack, for which {@code #updateForTopItem} only clears) resolves to nothing.
   */
  static boolean isStaticItem(
      ModelManager models, ItemStack stack, @Nullable ClientLevel level, int seed) {
    if (stack.isEmpty()) {
      return true;
    }
    Identifier modelId = stack.get(DataComponents.ITEM_MODEL);
    return modelId == null || isStatic(models.getItemModel(modelId), stack, level, seed, 0);
  }

  /**
   * Same lookup as {@code BlockModelResolver#updateForItemFrame}; {@code BlockStateModelWrapper
   * #update} (and Fabric API's {@code @Overwrite} of it) seeds its random source with a constant
   * and reads the fake block state only.
   */
  static boolean isStaticFrameModel(ModelManager models, boolean glowing, boolean map) {
    BlockModel model =
        models.getBlockModelSet().get(BlockStateDefinitions.getItemFrameFakeState(glowing, map));
    return model.getClass() == BlockStateModelWrapper.class
        && model instanceof BlockStateModelWrapperAccessor wrapper
        && wrapper.relativity$tints().isEmpty()
        && STATIC_BLOCK_STATE_MODELS.contains(wrapper.relativity$model().getClass());
  }

  private static boolean isStatic(
      ItemModel model, ItemStack stack, @Nullable ClientLevel level, int seed, int depth) {
    if (depth > MAX_DEPTH) {
      return false;
    }
    Class<?> type = model.getClass();
    if (type == EmptyModel.class || type == MissingItemModel.class) {
      return true;
    }
    if (type == CuboidItemModelWrapper.class
        && model instanceof CuboidItemModelWrapperAccessor cuboid) {
      return allStaticTints(cuboid.relativity$tints());
    }
    if (type == CompositeModel.class && model instanceof CompositeModelAccessor composite) {
      for (ItemModel child : composite.relativity$models()) {
        if (!isStatic(child, stack, level, seed, depth + 1)) {
          return false;
        }
      }
      return true;
    }
    if (type == RangeSelectItemModel.class && model instanceof RangeSelectItemModelAccessor range) {
      if (!STATIC_RANGE_PROPERTIES.contains(range.relativity$property().getClass())) {
        return false;
      }
      for (ItemModel child : range.relativity$models()) {
        if (!isStatic(child, stack, level, seed, depth + 1)) {
          return false;
        }
      }
      return isStatic(range.relativity$fallback(), stack, level, seed, depth + 1);
    }
    if (type == SelectItemModel.class && model instanceof SelectItemModelAccessor<?> select) {
      return isStaticSelection(select, stack, level, seed, depth);
    }
    return false;
  }

  /**
   * Follows the case vanilla picks ({@code SelectItemModel#update}): with a static property the
   * pick is the same every frame, so only that case matters.
   */
  private static <T> boolean isStaticSelection(
      SelectItemModelAccessor<T> select,
      ItemStack stack,
      @Nullable ClientLevel level,
      int seed,
      int depth) {
    SelectItemModelProperty<T> property = select.relativity$property();
    if (!STATIC_SELECT_PROPERTIES.contains(property.getClass())) {
      return false;
    }
    T value = property.get(stack, level, null, seed, ItemDisplayContext.FIXED);
    return isStatic(select.relativity$models().get(value, level), stack, level, seed, depth + 1);
  }

  private static boolean allStaticTints(List<ItemTintSource> tints) {
    for (ItemTintSource tint : tints) {
      if (!STATIC_TINTS.contains(tint.getClass())) {
        return false;
      }
    }
    return true;
  }
}

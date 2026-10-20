package com.einsteinmods.relativity.client.mixin;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.RangeSelectItemModel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The property and cases {@code RangeSelectItemModel#update} picks from: read to classify. */
@Mixin(RangeSelectItemModel.class)
public interface RangeSelectItemModelAccessor {
  @Accessor("property")
  RangeSelectItemModelProperty relativity$property();

  @Accessor("models")
  ItemModel[] relativity$models();

  @Accessor("fallback")
  ItemModel relativity$fallback();
}

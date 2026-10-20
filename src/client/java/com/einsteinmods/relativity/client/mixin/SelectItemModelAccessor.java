package com.einsteinmods.relativity.client.mixin;

import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The property and case selector {@code SelectItemModel#update} uses: read to classify. */
@Mixin(SelectItemModel.class)
public interface SelectItemModelAccessor<T> {
  @Accessor("property")
  SelectItemModelProperty<T> relativity$property();

  @Accessor("models")
  SelectItemModel.ModelSelector<T> relativity$models();
}

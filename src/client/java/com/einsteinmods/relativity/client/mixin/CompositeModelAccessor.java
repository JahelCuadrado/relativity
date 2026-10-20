package com.einsteinmods.relativity.client.mixin;

import java.util.List;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.ItemModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The models {@code CompositeModel#update} layers: read to classify a model. */
@Mixin(CompositeModel.class)
public interface CompositeModelAccessor {
  @Accessor("models")
  List<ItemModel> relativity$models();
}

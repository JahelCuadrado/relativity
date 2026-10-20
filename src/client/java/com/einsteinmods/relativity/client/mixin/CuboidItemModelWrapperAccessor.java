package com.einsteinmods.relativity.client.mixin;

import java.util.List;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The tint sources {@code CuboidItemModelWrapper#update} evaluates: read to classify a model. */
@Mixin(CuboidItemModelWrapper.class)
public interface CuboidItemModelWrapperAccessor {
  @Accessor("tints")
  List<ItemTintSource> relativity$tints();
}

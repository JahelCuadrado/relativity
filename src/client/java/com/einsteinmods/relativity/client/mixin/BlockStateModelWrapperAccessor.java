package com.einsteinmods.relativity.client.mixin;

import java.util.List;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The block state model and tints {@code BlockStateModelWrapper#update} reads (also through Fabric
 * API's {@code @Overwrite} of it): read to classify the item frame's wood model.
 */
@Mixin(BlockStateModelWrapper.class)
public interface BlockStateModelWrapperAccessor {
  @Accessor("model")
  BlockStateModel relativity$model();

  @Accessor("tints")
  List<BlockTintSource> relativity$tints();
}

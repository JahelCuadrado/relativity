package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.WorldgenProbes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Dev-only: thread CPU time of {@code ChunkGenerator#doCreateBiomes} (climate noise per biome cell)
 * and {@code ChunkGenerator#applyBiomeDecoration} (features), per chunk, on whatever thread runs
 * them. Wrapping allocates per call; these run once per chunk.
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorProbeMixin {
  @WrapMethod(method = "doCreateBiomes")
  private void relativityBenchmark$timeBiomes(
      Blender blender, RandomState randomState, ChunkAccess chunk, Operation<Void> original) {
    long begin = WorldgenProbes.begin();
    try {
      original.call(blender, randomState, chunk);
    } finally {
      WorldgenProbes.end(WorldgenProbes.Stage.CREATE_BIOMES, begin);
    }
  }

  @WrapMethod(method = "applyBiomeDecoration")
  private void relativityBenchmark$timeFeatures(
      WorldGenLevel level,
      ChunkAccess chunk,
      StructureManager structureManager,
      Operation<Void> original) {
    long begin = WorldgenProbes.begin();
    try {
      original.call(level, chunk, structureManager);
    } finally {
      WorldgenProbes.end(WorldgenProbes.Stage.FEATURES, begin);
    }
  }
}

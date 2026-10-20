package com.einsteinmods.relativity.benchmark.mixin;

import com.einsteinmods.relativity.benchmark.WorldgenProbes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Dev-only: thread CPU time of the three terrain stages of {@code
 * NoiseBasedChunkGenerator#buildTerrain}, per chunk: {@code doFill} (density functions and noise
 * into blocks), {@code buildSurface} (surface rules) and {@code generateCarvers}.
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseGeneratorProbeMixin {
  @WrapMethod(method = "doFill")
  private void relativityBenchmark$timeFill(
      NoiseChunk noiseChunk, ChunkAccess chunk, Operation<Void> original) {
    long begin = WorldgenProbes.begin();
    try {
      original.call(noiseChunk, chunk);
    } finally {
      WorldgenProbes.end(WorldgenProbes.Stage.FILL_NOISE, begin);
    }
  }

  @WrapMethod(method = "buildSurface")
  private void relativityBenchmark$timeSurface(
      ChunkAccess chunk,
      NoiseChunk noiseChunk,
      RandomState randomState,
      BiomeManager biomeManager,
      Set<Holder<Biome>> possibleBiomes,
      MaterialRule materialRule,
      Operation<Void> original) {
    long begin = WorldgenProbes.begin();
    try {
      original.call(chunk, noiseChunk, randomState, biomeManager, possibleBiomes, materialRule);
    } finally {
      WorldgenProbes.end(WorldgenProbes.Stage.BUILD_SURFACE, begin);
    }
  }

  @WrapMethod(method = "generateCarvers")
  private void relativityBenchmark$timeCarvers(
      ChunkAccess chunk,
      Blender blender,
      NoiseChunk noiseChunk,
      RandomState randomState,
      BiomeManager biomeManager,
      @Nullable WorldGenRegion carverBiomeRegion,
      MaterialRule materialRule,
      Operation<Void> original) {
    long begin = WorldgenProbes.begin();
    try {
      original.call(
          chunk, blender, noiseChunk, randomState, biomeManager, carverBiomeRegion, materialRule);
    } finally {
      WorldgenProbes.end(WorldgenProbes.Stage.CARVERS, begin);
    }
  }
}

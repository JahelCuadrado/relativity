package com.einsteinmods.relativity.client.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The world folder a server saves to. */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
  @Accessor("storageSource")
  LevelStorageSource.LevelStorageAccess relativity$storageSource();
}

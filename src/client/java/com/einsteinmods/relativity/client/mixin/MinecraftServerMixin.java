package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.exit.PendingWorldSaves;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forgets a background save as soon as it is done, so the stopped server is not kept in memory.
 * {@code MinecraftServer#runServer} calls {@code onServerExit} in its last {@code finally}, after
 * {@code stopServer} has saved every level and closed {@code storageSource} (the session lock), and
 * after Fabric's {@code SERVER_STOPPED} (tail of {@code stopServer}). Cost: one check per server
 * stop.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
  @Inject(method = "onServerExit", at = @At("HEAD"))
  private void relativity$forgetFinishedSave(CallbackInfo callback) {
    if (PendingWorldSaves.hasPending() && (Object) this instanceof IntegratedServer server) {
      PendingWorldSaves.finished(server);
    }
  }
}

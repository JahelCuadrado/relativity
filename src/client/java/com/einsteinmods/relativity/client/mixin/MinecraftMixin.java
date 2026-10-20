package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.exit.PendingWorldSaves;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Instant world exit. {@code Minecraft#disconnect(Screen, boolean, boolean)} clears {@code
 * singleplayerServer}, calls {@code IntegratedServer#halt(false)} and then blocks the render thread
 * in {@code while (!server.isShutdown()) { pumpEvents; renderFrame }} until {@code
 * MinecraftServer#stopServer} has saved players and chunks, closed the levels and released the
 * session lock. With the optimization on, the server is tracked in {@link PendingWorldSaves} right
 * before {@code halt} and the loop is skipped; the rest of {@code disconnect} (menu screen, {@code
 * updateLevelInEngines(null)}, {@code player = null}) runs as in vanilla. Off: vanilla, unchanged.
 *
 * <p>What the stopping server still touches on the client after {@code disconnect} returns, checked
 * in 26.3 sources; nothing the client resets or a new world reuses is written:
 *
 * <ul>
 *   <li>At most one more {@code IntegratedServer#tickServer} (the {@code runServer} loop checks
 *       {@code running} once per tick): reads {@code Minecraft#isPaused} and the render and
 *       simulation distance options only. Vanilla's own wait loop renders concurrently with it too.
 *   <li>{@code getTickTimeLogger} (debug overlay sample logger) in that last tick only; {@code
 *       stopServer} does not tick, so it never overlaps a new server's ticks.
 *   <li>{@code stopServer}: its own {@code ServerConnectionListener}, player list, levels, saved
 *       data and {@code storageSource}; nothing on {@code Minecraft}. The client connection was
 *       already closed by {@code disconnect}; a new world gets its own in-memory channel address.
 *   <li>LAN: {@code halt} stops the LAN pinger; {@code teardownPublishedState} and the TCP listener
 *       belong to this server. A new world picks a free port when published.
 *   <li>{@code reportChunkSaveFailure} and low-disk warnings post toasts through {@code
 *       Minecraft#execute}, run on the render thread: shown over the menu instead of the saving
 *       screen.
 *   <li>{@code getServerDirectory}, {@code forceSynchronousWrites}, {@code getModdedStatus}: reads.
 *   <li>A crash while stopping goes through {@code onServerCrash}, as in vanilla.
 * </ul>
 *
 * Opening the same folder again waits in {@link LevelStorageSourceMixin}. Quitting waits here,
 * right after {@code Minecraft#exitWorldAndClose} disconnects and before {@code Minecraft#close}
 * tears the renderer down, so the saving screen still renders; the total wait is at most vanilla's,
 * since background saves started earlier. That happens before {@code Main} starts the post-main
 * {@code ClientShutdownWatchdog} (15 s, then {@code System.exit(-8)}), so it cannot fire on a
 * pending save; the window-close watchdog (15 s, crash report only) sees no longer a wait than in
 * vanilla. Crashes and {@code System.exit} are covered by the shutdown hook in {@link
 * PendingWorldSaves}.
 *
 * <p>Cost: nothing per frame or tick; one check per world exit and one per game quit.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
  @WrapOperation(
      method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;halt(Z)V"))
  private void relativity$trackBackgroundSave(
      IntegratedServer server, boolean wait, Operation<Void> original) {
    // Tracked before halt, so the server cannot finish (and forget itself) before it is tracked.
    if (PendingWorldSaves.ENABLED) {
      PendingWorldSaves.track(server);
    }
    original.call(server, wait);
  }

  @WrapOperation(
      method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/server/IntegratedServer;isShutdown()Z"))
  private boolean relativity$skipSavingWait(IntegratedServer server, Operation<Boolean> original) {
    return PendingWorldSaves.ENABLED || original.call(server);
  }

  @Inject(
      method = "exitWorldAndClose",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/Minecraft;disconnectWithProgressScreen()V",
              shift = At.Shift.AFTER))
  private void relativity$awaitBackgroundSavesBeforeClosing(CallbackInfo callback) {
    if (PendingWorldSaves.hasPending()) {
      PendingWorldSaves.awaitAllWhileQuitting(Minecraft.getInstance());
    }
  }
}

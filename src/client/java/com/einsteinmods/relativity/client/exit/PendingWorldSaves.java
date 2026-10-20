package com.einsteinmods.relativity.client.exit;

import com.einsteinmods.relativity.client.mixin.MinecraftServerAccessor;
import com.einsteinmods.relativity.config.RelativitySettings;
import com.einsteinmods.relativity.core.OptimizationToggles;
import com.einsteinmods.relativity.core.PendingSaves;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/**
 * Local worlds the player has left that are still saving ({@code instant_world_exit}). The client
 * returns to the menu at once; anything that opens the same world folder again waits here first,
 * and so does quitting the game.
 *
 * <p>Steady-state cost: none per frame or tick. Each hook that touches a world folder pays one
 * {@link #hasPending()} check when nothing is saving.
 */
public final class PendingWorldSaves {
  /** Read once: the optimization is applied by mixins, so a change needs a restart. */
  public static final boolean ENABLED =
      RelativitySettings.startup().isEnabled(OptimizationToggles.INSTANT_WORLD_EXIT);

  private static final String SHUTDOWN_HOOK_NAME = "Relativity world save shutdown hook";

  /**
   * Keyed by the absolute, normalised world folder. Finished once the server thread is dead, like
   * vanilla's own wait in {@code Minecraft#disconnect} ({@code MinecraftServer#isShutdown}).
   */
  private static final PendingSaves<Path, IntegratedServer> SAVES =
      new PendingSaves<>(IntegratedServer::isShutdown);

  private static final AtomicBoolean SHUTDOWN_HOOK_INSTALLED = new AtomicBoolean();

  private PendingWorldSaves() {}

  /** True while at least one left world may still be saving. */
  public static boolean hasPending() {
    return !SAVES.isEmpty();
  }

  /** Tracks {@code server}, which is about to stop and save in the background. */
  public static void track(IntegratedServer server) {
    if (SHUTDOWN_HOOK_INSTALLED.compareAndSet(false, true)) {
      Runtime.getRuntime()
          .addShutdownHook(new Thread(PendingWorldSaves::joinAll, SHUTDOWN_HOOK_NAME));
    }
    // LevelDirectory is an immutable record; LevelStorageAccess#getLevelPath would touch a
    // HashMap the server thread also uses while saving.
    Path worldFolder =
        ((MinecraftServerAccessor) server).relativity$storageSource().getLevelDirectory().path();
    SAVES.add(key(worldFolder), server);
  }

  /** Called on the server thread once its world is saved and its folder unlocked. */
  public static void finished(IntegratedServer server) {
    SAVES.remove(server);
  }

  /** True while the world in {@code worldFolder} is still saving. */
  public static boolean isSaving(Path worldFolder) {
    return SAVES.pendingFor(key(worldFolder)) != null;
  }

  /**
   * Returns once the world in {@code worldFolder} has finished saving; at once for any other world.
   * On the render thread it shows "Saving world" meanwhile and then restores the screen.
   */
  public static void awaitSave(Path worldFolder) {
    IntegratedServer server = SAVES.pendingFor(key(worldFolder));
    if (server == null) {
      return;
    }
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.isSameThread()) {
      SavingScreen.showUntilSaved(minecraft, List.of(server), true);
    } else {
      join(server);
    }
  }

  /** Returns once every left world has finished saving, showing "Saving world" meanwhile. */
  public static void awaitAllWhileQuitting(Minecraft minecraft) {
    List<IntegratedServer> servers = SAVES.pending();
    if (!servers.isEmpty()) {
      SavingScreen.showUntilSaved(minecraft, servers, false);
    }
  }

  /**
   * JVM shutdown (crash, {@code System.exit}, signal): vanilla's own hook in {@code
   * net.minecraft.client.main.Main} only joins the current server, so a world still saving here
   * would be cut off mid-write.
   */
  private static void joinAll() {
    for (IntegratedServer server : SAVES.pending()) {
      join(server);
    }
  }

  private static void join(IntegratedServer server) {
    try {
      server.getRunningThread().join();
    } catch (InterruptedException e) {
      // Stop waiting but keep the interrupt for the caller; the folder stays locked until the
      // save ends, so nothing can open it half-written.
      Thread.currentThread().interrupt();
    }
  }

  private static Path key(Path worldFolder) {
    return worldFolder.toAbsolutePath().normalize();
  }
}

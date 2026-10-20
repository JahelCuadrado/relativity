package com.einsteinmods.relativity.gametest;

import com.einsteinmods.relativity.client.exit.PendingWorldSaves;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.ContentValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leaving a world returns to the title screen while the world saves in the background; opening the
 * same world right away waits for that save (it must not fail on the session lock) and finds every
 * block and item the player left. Also logs how long the exit took (dev-only timing).
 *
 * <p>The client GameTest harness runs client, server and test thread in lock step. The exit uses
 * the two-argument {@code Minecraft#disconnect}, which Fabric defers to the client tick phase,
 * where the server can still run the task {@code IntegratedServer#halt} blocks on. The reopen is
 * queued with {@code BlockableEventLoop#schedule} and runs with the next client tasks, while the
 * stopping server has left its tick loop (and the harness phases) for {@code stopServer}.
 *
 * <p>Exit time is measured on the test thread, so its resolution is one harness tick.
 */
public final class InstantWorldExitClientGameTest implements FabricClientGameTest {
  private static final Logger LOGGER = LoggerFactory.getLogger("relativity-gametest");

  private static final List<BlockPos> PLACED_OFFSETS =
      List.of(
          new BlockPos(2, 1, 2),
          new BlockPos(-3, 2, 4),
          new BlockPos(5, 3, -6),
          new BlockPos(40, 4, 40),
          new BlockPos(-45, 5, 30));

  private static final int DIAMOND_SLOT = 3;
  private static final int DIAMOND_COUNT = 37;
  private static final int EMERALD_SLOT = 20;
  private static final int EMERALD_COUNT = 5;

  /** Lets the client receive inventory and block updates before checking them. */
  private static final int SETTLE_TICKS = 20;

  private static final int SAVE_TIMEOUT_TICKS = 20 * 60;

  @Override
  public void runTest(ClientGameTestContext context) {
    TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create();
    TestServerConnection connection = world.getConnection();
    connection.waitForChunksDownload();

    BlockPos origin = world.getServer().computeOnServer(server -> placeBlocksAndItems(connection));
    context.waitTicks(SETTLE_TICKS);

    Path worldFolder = world.getWorldSave().getSaveDirectory();
    String levelId = worldFolder.getFileName().toString();

    long exitStart = System.nanoTime();
    int exitTicks = exitToTitle(context);
    long exitNanos = System.nanoTime() - exitStart;

    AtomicBoolean savingAtReopen = new AtomicBoolean();
    AtomicLong reopenWaitNanos = new AtomicLong();
    AtomicReference<Throwable> reopenFailure = new AtomicReference<>();
    context.runOnClient(
        minecraft ->
            minecraft.schedule(
                () -> {
                  try {
                    reopen(minecraft, worldFolder, levelId, savingAtReopen, reopenWaitNanos);
                  } catch (IOException | ContentValidationException | RuntimeException e) {
                    reopenFailure.set(e);
                  }
                }));
    context.waitFor(
        minecraft -> reopenFailure.get() != null || worldLoaded(minecraft), SAVE_TIMEOUT_TICKS);
    if (reopenFailure.get() != null) {
      throw new AssertionError("reopening the world right after exit failed", reopenFailure.get());
    }

    boolean instantExit = PendingWorldSaves.ENABLED;
    LOGGER.info(
        "Instant world exit {}: exit to title {} ms ({} ticks), world still saving at reopen: {},"
            + " reopen waited {} ms",
        instantExit ? "on" : "off",
        TimeUnit.NANOSECONDS.toMillis(exitNanos),
        exitTicks,
        savingAtReopen.get(),
        TimeUnit.NANOSECONDS.toMillis(reopenWaitNanos.get()));
    if (instantExit && !savingAtReopen.get()) {
      throw new AssertionError("the world must still be saving when it is reopened");
    }

    for (BlockPos offset : PLACED_OFFSETS) {
      BlockPos pos = origin.offset(offset);
      context.waitFor(
          minecraft ->
              minecraft
                  .level
                  .getChunkSource()
                  .hasChunk(
                      SectionPos.blockToSectionCoord(pos.getX()),
                      SectionPos.blockToSectionCoord(pos.getZ())),
          SAVE_TIMEOUT_TICKS);
    }
    context.waitTicks(SETTLE_TICKS);
    context.runOnClient(minecraft -> assertWorldKept(minecraft, origin));

    exitToTitle(context);
    context.waitFor(minecraft -> !PendingWorldSaves.hasPending(), SAVE_TIMEOUT_TICKS);
    context.waitTicks(2);
  }

  private static BlockPos placeBlocksAndItems(TestServerConnection connection) {
    ServerPlayer player = connection.getServerPlayer();
    ServerLevel level = connection.getServerLevel();
    BlockPos origin = player.blockPosition();
    for (BlockPos offset : PLACED_OFFSETS) {
      level.setBlockAndUpdate(origin.offset(offset), Blocks.DIAMOND_BLOCK.defaultBlockState());
    }
    player.getInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMOND_COUNT));
    player.getInventory().setItem(EMERALD_SLOT, new ItemStack(Items.EMERALD, EMERALD_COUNT));
    return origin;
  }

  /** Leaves the world like the pause menu does; returns the ticks until the title screen shows. */
  private static int exitToTitle(ClientGameTestContext context) {
    context.runOnClient(
        minecraft -> {
          minecraft.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
          minecraft.disconnect(new TitleScreen(), false);
        });
    return context.waitFor(
        minecraft -> minecraft.level == null && minecraft.gui.screen() instanceof TitleScreen,
        SAVE_TIMEOUT_TICKS);
  }

  /**
   * Opens the same world folder through {@code LevelStorageSource} first (waits for the save, then
   * must take the session lock), then plays it through the normal world list flow.
   */
  private static void reopen(
      Minecraft minecraft,
      Path worldFolder,
      String levelId,
      AtomicBoolean savingAtReopen,
      AtomicLong reopenWaitNanos)
      throws IOException, ContentValidationException {
    savingAtReopen.set(PendingWorldSaves.isSaving(worldFolder));
    long start = System.nanoTime();
    try (LevelStorageSource.LevelStorageAccess access =
        minecraft.getLevelSource().validateAndCreateAccess(levelId)) {
      reopenWaitNanos.set(System.nanoTime() - start);
      if (!access.hasWorldData()) {
        throw new IllegalStateException("reopened world has no level data");
      }
    }
    minecraft
        .createWorldOpenFlows()
        .openWorld(
            levelId,
            () -> {
              throw new IllegalStateException("reopening the world was cancelled");
            });
  }

  private static boolean worldLoaded(Minecraft minecraft) {
    return minecraft.level != null
        && minecraft.player != null
        && !(minecraft.gui.screen() instanceof LevelLoadingScreen);
  }

  private static void assertWorldKept(Minecraft minecraft, BlockPos origin) {
    for (BlockPos offset : PLACED_OFFSETS) {
      BlockPos pos = origin.offset(offset);
      if (!minecraft.level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK)) {
        throw new AssertionError("block lost at " + pos);
      }
    }
    assertStack(minecraft, DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMOND_COUNT));
    assertStack(minecraft, EMERALD_SLOT, new ItemStack(Items.EMERALD, EMERALD_COUNT));
  }

  private static void assertStack(Minecraft minecraft, int slot, ItemStack expected) {
    ItemStack actual = minecraft.player.getInventory().getItem(slot);
    if (!ItemStack.matches(actual, expected)) {
      throw new AssertionError("slot " + slot + " is " + actual + ", expected " + expected);
    }
  }
}

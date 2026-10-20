package com.einsteinmods.relativity.gametest;

import com.einsteinmods.relativity.client.frames.ItemFrameExtractCache;
import com.einsteinmods.relativity.client.frames.ItemFrameGeometryCache;
import com.einsteinmods.relativity.config.RelativitySettings;
import com.einsteinmods.relativity.core.OptimizationToggle;
import com.einsteinmods.relativity.core.OptimizationToggles;
import com.einsteinmods.relativity.core.ToggleStates;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Item frames look exactly the same with {@code item_frame_cache} and {@code
 * item_frame_extract_cache} on and off, each in its own pass: screenshots of every case (6
 * directions, 8 rotations, normal/glow/invisible frames, block, flat, enchanted, animated, clock,
 * compass, map and translucent items) are compared pixel by pixel, with the cache capturing,
 * reusing and rewriting positions (camera nudged), and again after an item change, a rotation, a
 * light change, a map update, a visibility change, a time change (clocks) and a spawn change
 * (compasses). Enchantment glint keeps animating. Also logs dev-only timings of {@code
 * FeatureRenderDispatcher#prepareFrame} for a dense wall of frames. With Sodium or Iris loaded
 * ({@code runClientGameTestSodium}) {@code item_frame_cache} stays off, so only the extract pass
 * runs.
 *
 * <p>Glint animates with the wall clock, so the comparisons run with glint speed 0; everything else
 * that moves (animated textures, clocks, clouds) only changes on level ticks, which are frozen
 * ({@code /tick freeze}), with the lightmap flicker held, while a comparison takes its screenshots
 * (Fabric's screenshot waits for ticks); an off-off control pair proves the picture is still.
 */
public final class ItemFrameCacheClientGameTest implements FabricClientGameTest {
  private static final Logger LOGGER = LoggerFactory.getLogger("relativity-gametest");

  private static final int SHOT_WIDTH = 320;
  private static final int SHOT_HEIGHT = 240;
  private static final int RENDER_DISTANCE = 2;
  private static final double NO_GLINT_SPEED = 0.0;
  private static final double DEFAULT_GLINT_SPEED = 0.5;
  private static final long GLINT_WAIT_MILLIS = 300L;

  private static final int SETTLE_TICKS = 30;
  private static final int FREEZE_TICKS = 3;
  private static final int SCENE_HEIGHT = 8;
  private static final int GROUP_SPACING = 10;
  private static final int[][] GROUP_OFFSETS = {
    {-1, -1}, {0, -1}, {1, -1}, {-1, 1}, {0, 1}, {1, 1}
  };
  private static final Direction[] GROUP_DIRECTIONS = {
    Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN
  };
  private static final int FRAMES_PER_SIDE = 3;
  private static final int FRAMES_PER_GROUP = FRAMES_PER_SIDE * FRAMES_PER_SIDE;
  private static final int ROTATIONS = 8;
  private static final int FRAME_KINDS = 3;
  private static final int GLOW_KIND = 1;
  private static final int INVISIBLE_KIND = 2;
  private static final double CAMERA_DISTANCE = 3.2;
  private static final double EYE_HEIGHT = 1.62;
  private static final double NUDGE = 0.37;
  private static final double CENTER = 0.5;

  private static final int MAP_SCALE = 0;
  private static final int MAP_EDIT_SIZE = 48;
  private static final byte[] MAP_EDIT_COLORS = {74, 30};

  /** Per pass: the item swapped into a frame (pass 0 places a light source, pass 1 removes it). */
  private static final Item[] CHANGED_ITEMS = {Items.EMERALD, Items.GOLD_INGOT};

  private static final int TIME_STEP_TICKS = 6000;
  private static final int SPAWN_OFFSET = 400;
  private static final String[] GEOMETRY_CACHE_OFF_MODS = {"sodium", "iris"};

  private static final int DENSE_SIDE = 16;
  private static final int DENSE_DISTANCE = 22;
  private static final double DENSE_CAMERA_DISTANCE = 9.0;
  private static final int TIMED_TICKS = 150;
  private static final int WARMUP_TICKS = 20;
  private static final double MOVE_STEP = 0.05;

  private int screenshotIndex;
  private OptimizationToggle toggle = OptimizationToggles.ITEM_FRAME_CACHE;

  @Override
  public void runTest(ClientGameTestContext context) {
    context.runOnClient(ItemFrameCacheClientGameTest::prepareOptions);
    TestSingleplayerContext world = context.worldBuilder().create();
    TestServerConnection connection = world.getConnection();
    TestServerContext server = world.getServer();
    connection.waitForChunksDownload();
    server.runCommand("gamemode spectator @a");
    server.runCommand("time set noon");
    BlockPos origin =
        server.computeOnServer(
            ignored -> connection.getServerPlayer().blockPosition().above(SCENE_HEIGHT));
    List<View> views =
        server.computeOnServer(ignored -> buildScene(connection.getServerLevel(), origin));
    context.waitTicks(SETTLE_TICKS);

    List<OptimizationToggle> toggles = new ArrayList<>();
    if (geometryCacheSupported()) {
      toggles.add(OptimizationToggles.ITEM_FRAME_CACHE);
    }
    toggles.add(OptimizationToggles.ITEM_FRAME_EXTRACT_CACHE);
    for (int pass = 0; pass < toggles.size(); pass++) {
      toggle = toggles.get(pass);
      LOGGER.info("Item frame comparisons for {}", toggle.key());
      comparePass(context, server, connection, origin, views, pass);
      if (toggle == OptimizationToggles.ITEM_FRAME_CACHE) {
        assertCachePaths();
      } else {
        assertExtractPaths();
      }
      setCache(context, true);
    }

    assertGlintAnimates(context, server);
    if (geometryCacheSupported()) {
      toggle = OptimizationToggles.ITEM_FRAME_CACHE;
      measure(context, server, connection, origin);
      setCache(context, true);
    }
    world.close();
  }

  /** Every view, then each kind of change, compared off and on for {@link #toggle}. */
  private void comparePass(
      ClientGameTestContext context,
      TestServerContext server,
      TestServerConnection connection,
      BlockPos origin,
      List<View> views,
      int pass) {
    String suffix = "-" + toggle.key();
    for (View view : views) {
      compareView(context, server, connection, view, "initial" + suffix, true);
    }

    server.runOnServer(
        ignored ->
            frameAt(connection, views.get(0), 1).setItem(CHANGED_ITEMS[pass].getDefaultInstance()));
    compareView(context, server, connection, views.get(0), "item-changed" + suffix, false);

    server.runOnServer(
        ignored -> {
          ItemFrame frame = frameAt(connection, views.get(1), 2);
          frame.setRotation(frame.getRotation() + 1);
        });
    compareView(context, server, connection, views.get(1), "rotated" + suffix, false);

    server.runOnServer(
        ignored ->
            connection
                .getServerLevel()
                .setBlockAndUpdate(
                    views.get(2).lightPos(),
                    pass == 0
                        ? Blocks.GLOWSTONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState()));
    compareView(context, server, connection, views.get(2), "light-changed" + suffix, false);

    server.runOnServer(ignored -> editMaps(connection, views, MAP_EDIT_COLORS[pass]));
    for (View view : views) {
      compareView(context, server, connection, view, "map-updated" + suffix, false);
    }

    server.runOnServer(
        ignored -> {
          ItemFrame frame = frameAt(connection, views.get(3), 0);
          frame.setInvisible(!frame.isInvisible());
        });
    compareView(context, server, connection, views.get(3), "visibility-changed" + suffix, false);

    server.runCommand("time add " + TIME_STEP_TICKS);
    int spawnOffset = pass == 0 ? SPAWN_OFFSET : -SPAWN_OFFSET;
    server.runCommand(
        String.format(
            Locale.ROOT,
            "setworldspawn %d %d %d",
            origin.getX() + spawnOffset,
            origin.getY(),
            origin.getZ() - spawnOffset));
    for (View view : views) {
      compareView(context, server, connection, view, "time-and-spawn" + suffix, false);
    }
  }

  private static boolean geometryCacheSupported() {
    for (String modId : GEOMETRY_CACHE_OFF_MODS) {
      if (FabricLoader.getInstance().isModLoaded(modId)) {
        return false;
      }
    }
    return true;
  }

  private static void prepareOptions(Minecraft minecraft) {
    minecraft.options.renderDistance().set(RENDER_DISTANCE);
    minecraft.options.glintSpeed().set(NO_GLINT_SPEED);
    minecraft.options.chunkSectionFadeInTime().set(0.0);
    minecraft.options.bobView().set(false);
    if (!minecraft.gui.hud.isHidden()) {
      minecraft.gui.hud.toggle();
    }
  }

  /** A 3x3 group of frames facing {@code facing}, and where the camera looks at it from. */
  private record View(
      String name,
      Direction facing,
      List<BlockPos> framePositions,
      Vec3 eye,
      float yaw,
      float pitch,
      BlockPos lightPos) {}

  private static List<View> buildScene(ServerLevel level, BlockPos origin) {
    List<ItemStack> items = sceneItems(level, origin);
    List<View> views = new ArrayList<>();
    for (int group = 0; group < GROUP_DIRECTIONS.length; group++) {
      Direction facing = GROUP_DIRECTIONS[group];
      BlockPos base =
          origin.offset(
              GROUP_OFFSETS[group][0] * GROUP_SPACING, 0, GROUP_OFFSETS[group][1] * GROUP_SPACING);
      Direction right = facing.getAxis().isHorizontal() ? facing.getClockWise() : Direction.EAST;
      Direction up = facing.getAxis().isHorizontal() ? Direction.UP : Direction.SOUTH;
      List<BlockPos> framePositions = new ArrayList<>();
      for (int index = 0; index < FRAMES_PER_GROUP; index++) {
        BlockPos wall =
            base.relative(right, index % FRAMES_PER_SIDE - 1)
                .relative(up, index / FRAMES_PER_SIDE - 1);
        level.setBlockAndUpdate(wall, Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState());
        BlockPos framePos = wall.relative(facing);
        int variant = index + group;
        ItemFrame frame =
            variant % FRAME_KINDS == GLOW_KIND
                ? new GlowItemFrame(level, framePos, facing)
                : new ItemFrame(level, framePos, facing);
        frame.setItem(items.get(variant % items.size()).copy(), false);
        frame.setRotation(variant % ROTATIONS);
        frame.setInvisible(variant % FRAME_KINDS == INVISIBLE_KIND);
        level.addFreshEntity(frame);
        framePositions.add(framePos);
      }
      Vec3 center = Vec3.atLowerCornerOf(base.relative(facing)).add(CENTER, CENTER, CENTER);
      Vec3 eye = center.add(Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(CAMERA_DISTANCE));
      Vec3 look = center.subtract(eye).normalize();
      float yaw = (float) Math.toDegrees(Mth.atan2(-look.x, look.z));
      float pitch = (float) -Math.toDegrees(Math.asin(look.y));
      BlockPos lightPos = base.relative(facing).relative(right, 2);
      views.add(
          new View(
              facing.getName().toLowerCase(Locale.ROOT),
              facing,
              framePositions,
              eye,
              yaw,
              pitch,
              lightPos));
    }
    return views;
  }

  /** Block, flat, enchanted, animated, clock, compass, map, translucent block and flat items. */
  private static List<ItemStack> sceneItems(ServerLevel level, BlockPos origin) {
    ItemStack map =
        MapItem.create(level, origin.getX(), origin.getZ(), (byte) MAP_SCALE, true, false);
    MapItem.renderBiomePreviewMap(level, map);
    return List.of(
        Items.STONE.getDefaultInstance(),
        Items.DIAMOND.getDefaultInstance(),
        Items.ENCHANTED_GOLDEN_APPLE.getDefaultInstance(),
        Items.PRISMARINE.getDefaultInstance(),
        Items.CLOCK.getDefaultInstance(),
        Items.COMPASS.getDefaultInstance(),
        map,
        Items.STAINED_GLASS.pick(DyeColor.RED).getDefaultInstance(),
        Items.STAINED_GLASS_PANE.pick(DyeColor.BLUE).getDefaultInstance());
  }

  private static ItemFrame frameAt(TestServerConnection connection, View view, int index) {
    BlockPos pos = view.framePositions().get(index);
    List<ItemFrame> frames =
        connection.getServerLevel().getEntitiesOfClass(ItemFrame.class, new AABB(pos));
    if (frames.isEmpty()) {
      throw new AssertionError("no item frame at " + pos);
    }
    return frames.get(0);
  }

  private static void editMaps(TestServerConnection connection, List<View> views, byte color) {
    ServerLevel level = connection.getServerLevel();
    for (View view : views) {
      for (int index = 0; index < FRAMES_PER_GROUP; index++) {
        ItemStack item = frameAt(connection, view, index).getItem();
        MapItemSavedData data = MapItem.getSavedData(item, level);
        if (data != null) {
          for (int x = 0; x < MAP_EDIT_SIZE; x++) {
            for (int y = 0; y < MAP_EDIT_SIZE; y++) {
              data.setColor(x, y, color);
            }
          }
          return;
        }
      }
    }
  }

  /**
   * Off, on (capture), on (reuse) at the view; then, when {@code nudge}, the camera moves a little
   * without a tick: on (positions rewritten) and off there. Every pair must be pixel-identical.
   */
  private void compareView(
      ClientGameTestContext context,
      TestServerContext server,
      TestServerConnection connection,
      View view,
      String label,
      boolean nudge) {
    server.runCommand(
        String.format(
            Locale.ROOT,
            "tp @a %.4f %.4f %.4f %.4f %.4f",
            view.eye().x,
            view.eye().y - EYE_HEIGHT,
            view.eye().z,
            view.yaw(),
            view.pitch()));
    context.waitTicks(SETTLE_TICKS);
    connection.waitForChunksRender();
    freeze(context, server);
    String prefix = label + "-" + view.name();
    setCache(context, false);
    Path off = shot(context, prefix + "-off");
    if (toggle == OptimizationToggles.ITEM_FRAME_CACHE
        && context.computeOnClient(minecraft -> ItemFrameGeometryCache.INSTANCE.cachedSubmits())
            != 0) {
      throw new AssertionError("the cache must be empty right after it is switched off");
    }
    setCache(context, true);
    Path capture = shot(context, prefix + "-on-capture");
    Path reuse = shot(context, prefix + "-on-reuse");
    setCache(context, false);
    Path control = shot(context, prefix + "-off-control");
    setCache(context, true);
    assertIdentical(off, control);
    assertIdentical(off, capture);
    assertIdentical(off, reuse);
    if (nudge) {
      context.runOnClient(ItemFrameCacheClientGameTest::nudgeCamera);
      Path moved = shot(context, prefix + "-on-moved");
      setCache(context, false);
      Path movedOff = shot(context, prefix + "-off-moved");
      setCache(context, true);
      assertIdentical(movedOff, moved);
    }
    unfreeze(context, server);
  }

  /**
   * Screenshots wait for ticks: freezes level ticks (animated textures, clocks, clouds) and holds
   * the lightmap flicker, so only the cache could change the picture.
   */
  private static void freeze(ClientGameTestContext context, TestServerContext server) {
    server.runCommand("tick freeze");
    LightFlicker.hold(true);
    context.waitTicks(FREEZE_TICKS);
  }

  private static void unfreeze(ClientGameTestContext context, TestServerContext server) {
    LightFlicker.hold(false);
    context.waitTick();
    server.runCommand("tick unfreeze");
  }

  private static void nudgeCamera(Minecraft minecraft) {
    LocalPlayer player = minecraft.player;
    player.snapTo(
        player.getX() + NUDGE,
        player.getY() + NUDGE,
        player.getZ() - NUDGE,
        player.getYRot(),
        player.getXRot());
  }

  /** With ticks frozen, only the wall-clock glint can change between two screenshots. */
  private void assertGlintAnimates(ClientGameTestContext context, TestServerContext server) {
    freeze(context, server);
    context.runOnClient(minecraft -> minecraft.options.glintSpeed().set(DEFAULT_GLINT_SPEED));
    Path first = shot(context, "glint-first");
    try {
      Thread.sleep(GLINT_WAIT_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted while waiting for the glint to move", e);
    }
    Path second = shot(context, "glint-second");
    if (differingPixels(first, second) == 0) {
      throw new AssertionError("enchantment glint did not animate with the cache on");
    }
    context.runOnClient(minecraft -> minecraft.options.glintSpeed().set(NO_GLINT_SPEED));
    unfreeze(context, server);
  }

  private static void assertCachePaths() {
    ItemFrameGeometryCache cache = ItemFrameGeometryCache.INSTANCE;
    LOGGER.info(
        "Item frame cache: {} captured, {} reused ({} with rewritten positions, {} frame wood)",
        cache.capturedSubmits(),
        cache.replayedSubmits(),
        cache.rewrittenSubmits(),
        cache.replayedMeshSubmits());
    if (cache.capturedSubmits() == 0
        || cache.replayedSubmits() == 0
        || cache.rewrittenSubmits() == 0
        || cache.replayedMeshSubmits() == 0) {
      throw new AssertionError(
          "capture, reuse, position rewrite and frame wood reuse must all run");
    }
  }

  /**
   * Kept, retained and vanilla states all occur: the scene has items whose models never change and
   * clocks and compasses, whose models must be resolved every frame.
   */
  private static void assertExtractPaths() {
    ItemFrameExtractCache cache = ItemFrameExtractCache.INSTANCE;
    LOGGER.info(
        "Item frame extract cache: {} kept, {} retained, {} left to vanilla",
        cache.keptStates(),
        cache.retainedStates(),
        cache.vanillaStates());
    if (cache.keptStates() == 0 || cache.retainedStates() == 0 || cache.vanillaStates() == 0) {
      throw new AssertionError("kept, retained and vanilla item frame states must all occur");
    }
  }

  /** Dev-only numbers: prepareFrame time for a 16x16 wall of frames, still and moving. */
  private void measure(
      ClientGameTestContext context,
      TestServerContext server,
      TestServerConnection connection,
      BlockPos origin) {
    BlockPos wallCenter = origin.offset(0, 0, -DENSE_DISTANCE);
    server.runOnServer(ignored -> buildDenseWall(connection.getServerLevel(), wallCenter));
    Vec3 eye =
        Vec3.atCenterOf(wallCenter.relative(Direction.SOUTH)).add(0, 0, DENSE_CAMERA_DISTANCE);
    server.runCommand(
        String.format(Locale.ROOT, "tp @a %.4f %.4f %.4f 180 0", eye.x, eye.y - EYE_HEIGHT, eye.z));
    context.waitTicks(SETTLE_TICKS);
    connection.waitForChunksRender();
    long frames =
        context.computeOnClient(minecraft -> ItemFrameGeometryCache.INSTANCE.cachedSubmits());
    LOGGER.info(
        "Dense wall: {} cached submits, {} KiB of vertices",
        frames,
        context.computeOnClient(minecraft -> ItemFrameGeometryCache.INSTANCE.cachedBytes()) / 1024);
    for (boolean cacheOn : new boolean[] {false, true}) {
      for (boolean moving : new boolean[] {false, true}) {
        setCache(context, cacheOn);
        timeTicks(context, moving, WARMUP_TICKS);
        PrepareFrameTimings.reset();
        timeTicks(context, moving, TIMED_TICKS);
        LOGGER.info(
            "prepareFrame, cache {}, camera {}: {}",
            cacheOn ? "on" : "off",
            moving ? "moving" : "still",
            PrepareFrameTimings.summary());
      }
    }
  }

  private static void buildDenseWall(ServerLevel level, BlockPos center) {
    List<ItemStack> items =
        List.of(
            Items.DIAMOND.getDefaultInstance(),
            Items.STONE.getDefaultInstance(),
            Items.ENCHANTED_GOLDEN_APPLE.getDefaultInstance(),
            Items.IRON_PICKAXE.getDefaultInstance(),
            Items.STAINED_GLASS.pick(DyeColor.RED).getDefaultInstance());
    int half = DENSE_SIDE / 2;
    for (int column = 0; column < DENSE_SIDE; column++) {
      for (int row = 0; row < DENSE_SIDE; row++) {
        BlockPos wall = center.offset(column - half, row - half, 0);
        level.setBlockAndUpdate(wall, Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState());
        ItemFrame frame = new ItemFrame(level, wall.relative(Direction.SOUTH), Direction.SOUTH);
        frame.setItem(items.get((column + row) % items.size()).copy(), false);
        level.addFreshEntity(frame);
      }
    }
  }

  private static void timeTicks(ClientGameTestContext context, boolean moving, int ticks) {
    for (int tick = 0; tick < ticks; tick++) {
      if (moving) {
        double step = tick % 2 == 0 ? MOVE_STEP : -MOVE_STEP;
        context.runOnClient(
            minecraft -> {
              LocalPlayer player = minecraft.player;
              player.snapTo(
                  player.getX() + step,
                  player.getY(),
                  player.getZ(),
                  player.getYRot(),
                  player.getXRot());
            });
      }
      context.waitTick();
    }
  }

  /** Switches {@link #toggle}; every other toggle stays at its default (on). */
  private void setCache(ClientGameTestContext context, boolean enabled) {
    String key = toggle.key();
    context.runOnClient(
        minecraft ->
            RelativitySettings.save(
                ToggleStates.resolve(OptimizationToggles.ALL, Map.of(key, enabled))));
  }

  private Path shot(ClientGameTestContext context, String name) {
    screenshotIndex++;
    return context.takeScreenshot(
        TestScreenshotOptions.of(
                String.format(Locale.ROOT, "frames-%03d-%s", screenshotIndex, name))
            .disableCounterPrefix()
            .withSize(SHOT_WIDTH, SHOT_HEIGHT));
  }

  private static void assertIdentical(Path expected, Path actual) {
    long different = differingPixels(expected, actual);
    if (different != 0) {
      throw new AssertionError(
          different
              + " pixels differ between "
              + expected.getFileName()
              + " and "
              + actual.getFileName());
    }
  }

  private static long differingPixels(Path first, Path second) {
    try (InputStream firstStream = Files.newInputStream(first);
        InputStream secondStream = Files.newInputStream(second);
        NativeImage firstImage = NativeImage.read(firstStream);
        NativeImage secondImage = NativeImage.read(secondStream)) {
      if (firstImage.getWidth() != secondImage.getWidth()
          || firstImage.getHeight() != secondImage.getHeight()) {
        throw new AssertionError("screenshot sizes differ");
      }
      long different = 0;
      for (int y = 0; y < firstImage.getHeight(); y++) {
        for (int x = 0; x < firstImage.getWidth(); x++) {
          if (firstImage.getPixel(x, y) != secondImage.getPixel(x, y)) {
            different++;
          }
        }
      }
      return different;
    } catch (IOException e) {
      throw new AssertionError("cannot read screenshots " + first + " / " + second, e);
    }
  }
}

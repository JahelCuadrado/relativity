package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * Storage-base scene floating at a fixed spot, built in one server task: a 32-wide block of chests
 * over hoppers (the hoppers pull from the chests every tick, as in sorting systems), then rows of
 * signs with text, patterned banners and a wall of item frames with items, all facing the camera.
 * Placed without neighbour updates so chests stay single and nothing pops off.
 */
final class DenseBaseScene {
  static final BlockPos ORIGIN = new BlockPos(0, 200, -3000);
  static final CameraPose CAMERA =
      new CameraPose(ORIGIN.getX() + 0.5, ORIGIN.getY() + 20, ORIGIN.getZ() - 10, 0f, 40f);

  static final int WIDTH = 32;
  static final int ROW_DEPTH = 16;
  static final int CHEST_LAYERS = 4;
  static final int FRAME_WALL_HEIGHT = 16;
  static final int EXPECTED_CHESTS = WIDTH * ROW_DEPTH * CHEST_LAYERS;
  static final int EXPECTED_HOPPERS = WIDTH * ROW_DEPTH;
  static final int EXPECTED_SIGNS = WIDTH * ROW_DEPTH;
  static final int EXPECTED_BANNERS = WIDTH * ROW_DEPTH;
  static final int EXPECTED_ITEM_FRAMES = WIDTH * FRAME_WALL_HEIGHT;

  /** Smallest render distance whose fog and section range hold the base (~64 blocks away). */
  static final int RENDER_DISTANCE = 5;

  private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
  private static final int FACING_NORTH_ROTATION = 8;
  private static final int SIGN_ROW_START = ROW_DEPTH;
  private static final int BANNER_ROW_START = 2 * ROW_DEPTH;
  private static final int FRAME_ROW = 3 * ROW_DEPTH;
  private static final int WALL_ROW = FRAME_ROW + 1;
  private static final int HOPPER_LAYER = 1;
  private static final int FIRST_CHEST_LAYER = 2;
  private static final List<Item> FRAME_ITEMS =
      List.of(Items.DIAMOND_SWORD, Items.APPLE, Items.COMPASS, Items.CLOCK, Items.IRON_PICKAXE);

  private DenseBaseScene() {}

  static void build(ServerLevel level) {
    BlockState floor = Blocks.STONE.defaultBlockState();
    BlockState hopper = Blocks.HOPPER.defaultBlockState();
    BlockState chest = Blocks.CHEST.defaultBlockState();
    BlockState sign =
        Blocks.OAK_SIGN
            .defaultBlockState()
            .setValue(BlockStateProperties.ROTATION_16, FACING_NORTH_ROTATION);
    BlockState banner =
        Blocks.BANNER
            .white()
            .defaultBlockState()
            .setValue(BlockStateProperties.ROTATION_16, FACING_NORTH_ROTATION);
    DataComponentMap bannerComponents =
        DataComponentMap.builder()
            .set(DataComponents.BANNER_PATTERNS, bannerPatterns(level))
            .build();
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    for (int column = 0; column < WIDTH; column++) {
      int x = firstX() + column;
      for (int row = 0; row <= WALL_ROW; row++) {
        level.setBlock(pos.set(x, ORIGIN.getY(), ORIGIN.getZ() + row), floor, PLACE_FLAGS);
      }
      for (int row = 0; row < ROW_DEPTH; row++) {
        int z = ORIGIN.getZ() + row;
        level.setBlock(pos.set(x, ORIGIN.getY() + HOPPER_LAYER, z), hopper, PLACE_FLAGS);
        for (int layer = 0; layer < CHEST_LAYERS; layer++) {
          level.setBlock(
              pos.set(x, ORIGIN.getY() + FIRST_CHEST_LAYER + layer, z), chest, PLACE_FLAGS);
        }
        placeSign(level, pos.set(x, ORIGIN.getY() + 1, ORIGIN.getZ() + SIGN_ROW_START + row), sign);
        placeBanner(
            level,
            pos.set(x, ORIGIN.getY() + 1, ORIGIN.getZ() + BANNER_ROW_START + row),
            banner,
            bannerComponents);
      }
      for (int height = 1; height <= FRAME_WALL_HEIGHT; height++) {
        level.setBlock(
            pos.set(x, ORIGIN.getY() + height, ORIGIN.getZ() + WALL_ROW), floor, PLACE_FLAGS);
        ItemFrame frame =
            new ItemFrame(
                level,
                new BlockPos(x, ORIGIN.getY() + height, ORIGIN.getZ() + FRAME_ROW),
                Direction.NORTH);
        frame.setItem(
            new ItemStack(FRAME_ITEMS.get((column + height) % FRAME_ITEMS.size())), false);
        level.addFreshEntity(frame);
      }
    }
  }

  private static void placeSign(ServerLevel level, BlockPos pos, BlockState state) {
    level.setBlock(pos, state, PLACE_FLAGS);
    if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
      List<Component> lines =
          List.of(
              Component.literal("Storage"),
              Component.literal("x " + pos.getX()),
              Component.literal("z " + pos.getZ()),
              Component.literal("Relativity"));
      sign.setText(new SignText(lines, lines, DyeColor.BLACK, false), SignTextSlot.FRONT);
    }
  }

  private static void placeBanner(
      ServerLevel level, BlockPos pos, BlockState state, DataComponentMap components) {
    level.setBlock(pos, state, PLACE_FLAGS);
    if (level.getBlockEntity(pos) instanceof BannerBlockEntity banner) {
      banner.applyComponents(components, DataComponentPatch.EMPTY);
      banner.setChanged();
      level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }
  }

  private static BannerPatternLayers bannerPatterns(ServerLevel level) {
    HolderGetter<BannerPattern> patterns =
        level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
    return new BannerPatternLayers.Builder()
        .add(patterns.getOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.RED)
        .add(patterns.getOrThrow(BannerPatterns.CROSS), DyeColor.BLUE)
        .add(patterns.getOrThrow(BannerPatterns.BORDER), DyeColor.BLACK)
        .build();
  }

  /** Counts what the scene really holds, so a mod that breaks the build shows in the report. */
  static JsonObject census(ServerLevel level) {
    int chests = 0;
    int hoppers = 0;
    int signs = 0;
    int banners = 0;
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    for (int column = 0; column < WIDTH; column++) {
      for (int row = 0; row < FRAME_ROW; row++) {
        for (int height = 1; height <= FIRST_CHEST_LAYER + CHEST_LAYERS; height++) {
          BlockEntity blockEntity =
              level.getBlockEntity(
                  pos.set(firstX() + column, ORIGIN.getY() + height, ORIGIN.getZ() + row));
          switch (blockEntity) {
            case ChestBlockEntity ignored -> chests++;
            case HopperBlockEntity ignored -> hoppers++;
            case SignBlockEntity ignored -> signs++;
            case BannerBlockEntity banner when !banner.getPatterns().layers().isEmpty() ->
                banners++;
            case null, default -> {}
          }
        }
      }
    }
    int frames =
        level
            .getEntitiesOfClass(ItemFrame.class, frameArea(), frame -> !frame.getItem().isEmpty())
            .size();
    JsonObject census = new JsonObject();
    census.addProperty("chests", chests);
    census.addProperty("hoppers", hoppers);
    census.addProperty("signsWithText", signs);
    census.addProperty("patternedBanners", banners);
    census.addProperty("itemFramesWithItems", frames);
    census.addProperty(
        "complete",
        chests == EXPECTED_CHESTS
            && hoppers == EXPECTED_HOPPERS
            && signs == EXPECTED_SIGNS
            && banners == EXPECTED_BANNERS
            && frames == EXPECTED_ITEM_FRAMES);
    return census;
  }

  /** What the client has and renders from the scene camera; must run on the render thread. */
  static JsonObject clientCensus(Minecraft minecraft) {
    return new ClientCensus(minecraft)
        .entities(
            "itemFrames",
            ItemFrame.class,
            frameArea(),
            frame -> !frame.getItem().isEmpty(),
            EXPECTED_ITEM_FRAMES)
        .sections(
            new AABB(
                firstX(),
                ORIGIN.getY(),
                ORIGIN.getZ(),
                firstX() + WIDTH,
                ORIGIN.getY() + FRAME_WALL_HEIGHT + 1,
                ORIGIN.getZ() + WALL_ROW + 1))
        .toJson();
  }

  private static AABB frameArea() {
    return new AABB(
        firstX(),
        ORIGIN.getY(),
        ORIGIN.getZ() + FRAME_ROW,
        firstX() + WIDTH,
        ORIGIN.getY() + FRAME_WALL_HEIGHT + 1,
        ORIGIN.getZ() + WALL_ROW);
  }

  private static int firstX() {
    return ORIGIN.getX() - WIDTH / 2;
  }
}

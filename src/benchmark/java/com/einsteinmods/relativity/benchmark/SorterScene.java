package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

/**
 * Hopper scene: a working {@value #SLICES}-item sorter of the standard comparator/hopper filter
 * design and {@value #LINES} long hopper lines, all with items flowing, so hopper ticks dominate
 * the server tick. Built in one server task: plain blocks and containers without neighbour updates,
 * redstone with full updates so dust shapes, comparator outputs and torch locks settle as in play.
 *
 * <p>Sorter slice {@code i} (x = 3i, stream along +x at y+3, z = 0), seen from the side:
 *
 * <pre>
 *   y+3  stream hopper (facing east)
 *   y+2  filter hopper F (faces south into air) ; comparator reading F at z-1 ; dust z-2, x and x+1 ;
 *        dust over W at (x+1, z-1)
 *   y+1  bottom hopper B (facing down)          ; redstone wall torch at (x+1, z) on block W
 *   y    output chest
 * </pre>
 *
 * F holds 41 filter items and one stick in each other slot (comparator output 2); a matching item
 * makes it 42 (output 3), the third dust then powers W, the torch turns off and unlocks B, which
 * pulls one item into the chest. Slices sit 3 apart so a torch only touches its own B. Feed: a
 * double chest over the first stream hopper with small mixed stacks (a new item kind every few
 * seconds) and every stream and line hopper starting with one item, so all flow from the first
 * tick; capacities outlast the scene with no refills.
 */
final class SorterScene {
  static final BlockPos ORIGIN = new BlockPos(3000, 200, -3000);
  static final int SLICES = 16;
  static final int SLICE_PITCH = 3;
  static final int LINES = 16;
  static final int LINE_LENGTH = 32;

  /** Lines run along +x from z = 4, two apart so their end chests never touch. */
  static final int FIRST_LINE_Z = 4;

  static final int LINE_PITCH = 2;
  static final int FIRST_STREAM_X = -1;
  static final int LAST_STREAM_X = SLICES * SLICE_PITCH - 1;
  static final int STREAM_HOPPERS = LAST_STREAM_X - FIRST_STREAM_X + 1;
  static final int EXPECTED_HOPPERS = STREAM_HOPPERS + 2 * SLICES + LINES * LINE_LENGTH;

  /** Smallest render distance whose sections hold the whole build from the camera above it. */
  static final int RENDER_DISTANCE = 4;

  static final CameraPose CAMERA =
      new CameraPose(
          ORIGIN.getX() + SLICES * SLICE_PITCH / 2.0,
          ORIGIN.getY() + 36,
          ORIGIN.getZ() + (FIRST_LINE_Z + LINES * LINE_PITCH) / 2.0,
          0f,
          90f);

  private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
  private static final int CHEST_Y = 0;
  private static final int LOCK_Y = 1;
  private static final int FILTER_Y = 2;
  private static final int STREAM_Y = 3;
  private static final int FEED_Y = 4;

  /** 41 of 64 plus four single sticks: comparator output 2; one more item gives 3. */
  private static final int FILTER_COUNT = 41;

  private static final int JUNK_SLOTS = 4;
  private static final int JUNK_COUNT = 1;
  private static final int FEED_STACK = 4;
  private static final int LINE_SOURCE_STACK = 64;
  private static final int PREFILL_COUNT = 1;
  private static final List<Item> FILTERS =
      List.of(
          Items.IRON_INGOT,
          Items.GOLD_INGOT,
          Items.COPPER_INGOT,
          Items.REDSTONE,
          Items.LAPIS_LAZULI,
          Items.COAL,
          Items.QUARTZ,
          Items.EMERALD,
          Items.DIAMOND,
          Items.AMETHYST_SHARD,
          Items.BONE,
          Items.STRING,
          Items.FEATHER,
          Items.GUNPOWDER,
          Items.SLIME_BALL,
          Items.BRICK);

  /** Not sorted: rides the whole stream into the overflow chest. */
  private static final Item OVERFLOW = Items.COBBLESTONE;

  private static final Item JUNK = Items.STICK;

  /** Feed double chest over the first stream hopper, west half first (chests face north). */
  private static final List<ChestType> FEED_HALVES = List.of(ChestType.LEFT, ChestType.RIGHT);

  private static final int ITEM_KINDS = SLICES + 1;

  private SorterScene() {}

  static void build(ServerLevel level) {
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    BlockState stone = Blocks.STONE.defaultBlockState();
    BlockState streamHopper = hopper(Direction.EAST);
    for (int x = FIRST_STREAM_X; x <= LAST_STREAM_X; x++) {
      level.setBlock(at(pos, x, STREAM_Y, 0), streamHopper, PLACE_FLAGS);
      fill(level, pos, streamItem(x), PREFILL_COUNT, 0);
    }
    int feedSlot = 0;
    for (int half = 0; half < FEED_HALVES.size(); half++) {
      placeChest(level, at(pos, FIRST_STREAM_X - 1 + half, FEED_Y, 0), FEED_HALVES.get(half));
      if (level.getBlockEntity(pos) instanceof Container feed) {
        for (int slot = 0; slot < feed.getContainerSize(); slot++) {
          feed.setItem(slot, new ItemStack(itemKind(feedSlot++), FEED_STACK));
        }
      }
    }
    placeChest(level, at(pos, LAST_STREAM_X + 1, STREAM_Y, 0), ChestType.SINGLE);
    for (int slice = 0; slice < SLICES; slice++) {
      buildSlice(level, pos, slice, stone);
    }
    BlockState lineHopper = hopper(Direction.EAST);
    for (int line = 0; line < LINES; line++) {
      int z = FIRST_LINE_Z + line * LINE_PITCH;
      placeChest(level, at(pos, 0, FEED_Y, z), ChestType.SINGLE);
      if (level.getBlockEntity(pos) instanceof Container source) {
        for (int slot = 0; slot < source.getContainerSize(); slot++) {
          source.setItem(slot, new ItemStack(OVERFLOW, LINE_SOURCE_STACK));
        }
      }
      for (int x = 0; x < LINE_LENGTH; x++) {
        level.setBlock(at(pos, x, STREAM_Y, z), lineHopper, PLACE_FLAGS);
        fill(level, pos, OVERFLOW, PREFILL_COUNT, 0);
      }
      placeChest(level, at(pos, LINE_LENGTH, STREAM_Y, z), ChestType.SINGLE);
    }
  }

  private static void buildSlice(
      ServerLevel level, BlockPos.MutableBlockPos pos, int slice, BlockState stone) {
    int x = slice * SLICE_PITCH;
    placeChest(level, at(pos, x, CHEST_Y, 0), ChestType.SINGLE);
    level.setBlock(at(pos, x, LOCK_Y, 0), hopper(Direction.DOWN), PLACE_FLAGS);
    level.setBlock(at(pos, x, FILTER_Y, 0), hopper(Direction.SOUTH), PLACE_FLAGS);
    level.setBlock(at(pos, x, LOCK_Y, -1), stone, PLACE_FLAGS);
    level.setBlock(at(pos, x, LOCK_Y, -2), stone, PLACE_FLAGS);
    level.setBlock(at(pos, x + 1, LOCK_Y, -2), stone, PLACE_FLAGS);
    level.setBlock(at(pos, x + 1, LOCK_Y, -1), stone, PLACE_FLAGS);
    placeWithUpdates(
        level,
        at(pos, x + 1, LOCK_Y, 0),
        Blocks.REDSTONE_WALL_TORCH
            .defaultBlockState()
            .setValue(RedstoneWallTorchBlock.FACING, Direction.SOUTH));
    placeWithUpdates(
        level,
        at(pos, x, FILTER_Y, -1),
        Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.SOUTH));
    BlockState dust = Blocks.REDSTONE_WIRE.defaultBlockState();
    placeWithUpdates(level, at(pos, x, FILTER_Y, -2), dust);
    placeWithUpdates(level, at(pos, x + 1, FILTER_Y, -2), dust);
    placeWithUpdates(level, at(pos, x + 1, FILTER_Y, -1), dust);
    BlockPos filter = at(pos, x, FILTER_Y, 0).immutable();
    if (level.getBlockEntity(filter) instanceof HopperBlockEntity hopper) {
      hopper.setItem(0, new ItemStack(FILTERS.get(slice), FILTER_COUNT));
      for (int slot = 1; slot <= JUNK_SLOTS; slot++) {
        hopper.setItem(slot, new ItemStack(JUNK, JUNK_COUNT));
      }
    }
  }

  /** Full updates: neighbours react (hopper locks, comparator reads) and dust takes its shape. */
  private static void placeWithUpdates(ServerLevel level, BlockPos pos, BlockState state) {
    level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), Block.UPDATE_ALL);
  }

  private static BlockState hopper(Direction facing) {
    return Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing);
  }

  private static void placeChest(ServerLevel level, BlockPos pos, ChestType type) {
    level.setBlock(
        pos,
        Blocks.CHEST
            .defaultBlockState()
            .setValue(ChestBlock.FACING, Direction.NORTH)
            .setValue(ChestBlock.TYPE, type),
        PLACE_FLAGS);
  }

  private static void fill(ServerLevel level, BlockPos pos, Item item, int count, int slot) {
    if (level.getBlockEntity(pos) instanceof HopperBlockEntity hopper) {
      hopper.setItem(slot, new ItemStack(item, count));
    }
  }

  /** The stream hopper over slice {@code i} starts with that slice's item, so sorting starts. */
  private static Item streamItem(int x) {
    return x >= 0 && x % SLICE_PITCH == 0 ? FILTERS.get(x / SLICE_PITCH) : itemKind(x + 1);
  }

  private static Item itemKind(int index) {
    int kind = Math.floorMod(index, ITEM_KINDS);
    return kind < SLICES ? FILTERS.get(kind) : OVERFLOW;
  }

  private static BlockPos.MutableBlockPos at(BlockPos.MutableBlockPos pos, int x, int y, int z) {
    return pos.set(ORIGIN.getX() + x, ORIGIN.getY() + y, ORIGIN.getZ() + z);
  }

  /**
   * Hoppers present, intact filters, items sorted into the right chest (never a wrong one), and
   * items that reached the overflow and line end chests. Complete needs every hopper, every filter
   * intact, no missorted item and flowing lines; sorted items grow between the census before and
   * after the rounds (the first matches take a while to reach their slice).
   */
  static JsonObject census(ServerLevel level) {
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    int hoppers = 0;
    for (int x = FIRST_STREAM_X; x <= LAST_STREAM_X; x++) {
      hoppers += isHopper(level, at(pos, x, STREAM_Y, 0)) ? 1 : 0;
    }
    int intactFilters = 0;
    int lockedBottoms = 0;
    long sorted = 0;
    long missorted = 0;
    for (int slice = 0; slice < SLICES; slice++) {
      int x = slice * SLICE_PITCH;
      Item filterItem = FILTERS.get(slice);
      hoppers += isHopper(level, at(pos, x, LOCK_Y, 0)) ? 1 : 0;
      if (!level.getBlockState(pos).getOptionalValue(HopperBlock.ENABLED).orElse(true)) {
        lockedBottoms++;
      }
      hoppers += isHopper(level, at(pos, x, FILTER_Y, 0)) ? 1 : 0;
      if (level.getBlockEntity(pos) instanceof HopperBlockEntity filter
          && isIntactFilter(filter, filterItem)) {
        intactFilters++;
      }
      if (level.getBlockEntity(at(pos, x, CHEST_Y, 0)) instanceof Container output) {
        for (int slot = 0; slot < output.getContainerSize(); slot++) {
          ItemStack stack = output.getItem(slot);
          if (stack.is(filterItem)) {
            sorted += stack.getCount();
          } else {
            missorted += stack.getCount();
          }
        }
      }
    }
    long lineEnds = 0;
    for (int line = 0; line < LINES; line++) {
      int z = FIRST_LINE_Z + line * LINE_PITCH;
      for (int x = 0; x < LINE_LENGTH; x++) {
        hoppers += isHopper(level, at(pos, x, STREAM_Y, z)) ? 1 : 0;
      }
      lineEnds += itemCount(level.getBlockEntity(at(pos, LINE_LENGTH, STREAM_Y, z)));
    }
    long overflow = itemCount(level.getBlockEntity(at(pos, LAST_STREAM_X + 1, STREAM_Y, 0)));
    JsonObject census = new JsonObject();
    census.addProperty("hoppers", hoppers);
    census.addProperty("intactFilters", intactFilters);
    census.addProperty("lockedBottomHoppers", lockedBottoms);
    census.addProperty("sortedItems", sorted);
    census.addProperty("missortedItems", missorted);
    census.addProperty("overflowItems", overflow);
    census.addProperty("lineEndItems", lineEnds);
    census.addProperty(
        "complete",
        hoppers == EXPECTED_HOPPERS && intactFilters == SLICES && missorted == 0 && lineEnds > 0);
    return census;
  }

  private static boolean isHopper(ServerLevel level, BlockPos pos) {
    return level.getBlockEntity(pos) instanceof HopperBlockEntity;
  }

  private static boolean isIntactFilter(HopperBlockEntity filter, Item filterItem) {
    ItemStack first = filter.getItem(0);
    if (!first.is(filterItem) || first.getCount() < FILTER_COUNT) {
      return false;
    }
    for (int slot = 1; slot <= JUNK_SLOTS; slot++) {
      ItemStack junk = filter.getItem(slot);
      if (!junk.is(JUNK) || junk.getCount() != JUNK_COUNT) {
        return false;
      }
    }
    return true;
  }

  private static long itemCount(BlockEntity blockEntity) {
    long count = 0;
    if (blockEntity instanceof Container container) {
      for (int slot = 0; slot < container.getContainerSize(); slot++) {
        count += container.getItem(slot).getCount();
      }
    }
    return count;
  }

  /** What the client renders from the scene camera; must run on the render thread. */
  static JsonObject clientCensus(Minecraft minecraft) {
    return new ClientCensus(minecraft)
        .sections(
            new AABB(
                ORIGIN.getX() + FIRST_STREAM_X - 1,
                ORIGIN.getY() + CHEST_Y,
                ORIGIN.getZ() - 2,
                ORIGIN.getX() + LAST_STREAM_X + 2,
                ORIGIN.getY() + FEED_Y + 1,
                ORIGIN.getZ() + FIRST_LINE_Z + LINES * LINE_PITCH))
        .toJson();
  }
}

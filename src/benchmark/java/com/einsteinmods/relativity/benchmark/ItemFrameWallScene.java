package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Item-frame scene: a floating lattice of stone blocks, each wearing an item frame on all six
 * faces, seen from a corner so ~1000 frames are in view at once. Frames cycle through normal, glow
 * and invisible frames, all eight rotations and a mix of items that render differently: block
 * items, flat items, enchanted (glint) items, animated textures, clock and compass, filled maps and
 * translucent blocks. Built in one server task without neighbour updates.
 */
final class ItemFrameWallScene {
  static final BlockPos ORIGIN = new BlockPos(-3000, 200, 0);
  static final int CELLS_X = 7;
  static final int CELLS_Y = 4;
  static final int CELLS_Z = 6;

  /** Blocks between two support blocks: room for both facing frames without overlap. */
  static final int CELL_PITCH = 3;

  static final int MAP_COUNT = 4;
  static final int EXPECTED_ITEM_FRAMES = CELLS_X * CELLS_Y * CELLS_Z * Direction.values().length;

  /** Smallest render distance whose fog and section range still hold the whole lattice. */
  static final int RENDER_DISTANCE = 4;

  static final CameraPose CAMERA =
      new CameraPose(ORIGIN.getX() - 14, ORIGIN.getY() + 22, ORIGIN.getZ() - 14, -47f, 29f);

  private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
  private static final int ROTATIONS = 8;
  private static final int ROTATION_STRIDE = 3;
  private static final int ITEM_STRIDE = 7;
  private static final byte MAP_SCALE = 0;
  private static final int SHARPNESS_LEVEL = 5;
  private static final int UNBREAKING_LEVEL = 3;

  private enum FrameKind {
    NORMAL,
    GLOW,
    INVISIBLE
  }

  private ItemFrameWallScene() {}

  static void build(ServerLevel level) {
    List<ItemStack> items = frameItems(level);
    BlockState support = Blocks.STONE.defaultBlockState();
    Direction[] faces = Direction.values();
    FrameKind[] kinds = FrameKind.values();
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    int cell = 0;
    for (int cellX = 0; cellX < CELLS_X; cellX++) {
      for (int cellY = 0; cellY < CELLS_Y; cellY++) {
        for (int cellZ = 0; cellZ < CELLS_Z; cellZ++) {
          BlockPos center =
              ORIGIN.offset(cellX * CELL_PITCH, cellY * CELL_PITCH, cellZ * CELL_PITCH);
          level.setBlock(pos.set(center), support, PLACE_FLAGS);
          for (int face = 0; face < faces.length; face++) {
            int index = cell * faces.length + face;
            FrameKind kind = kinds[(cell + face) % kinds.length];
            ItemFrame frame =
                kind == FrameKind.GLOW
                    ? new GlowItemFrame(level, center.relative(faces[face]), faces[face])
                    : new ItemFrame(level, center.relative(faces[face]), faces[face]);
            frame.setInvisible(kind == FrameKind.INVISIBLE);
            frame.setItem(items.get((cell * ITEM_STRIDE + face) % items.size()).copy(), false);
            frame.setRotation((index * ROTATION_STRIDE) % ROTATIONS);
            level.addFreshEntity(frame);
          }
          cell++;
        }
      }
    }
  }

  private static List<ItemStack> frameItems(ServerLevel level) {
    HolderGetter<Enchantment> enchantments =
        level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    sword.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), SHARPNESS_LEVEL);
    ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
    chestplate.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), UNBREAKING_LEVEL);
    ItemStack glintingBow = new ItemStack(Items.BOW);
    glintingBow.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

    List<ItemStack> items = new ArrayList<>();
    for (Item item :
        List.of(
            // Block items.
            Items.STONE,
            Items.OAK_LOG,
            // Flat items.
            Items.APPLE,
            Items.DIAMOND_SWORD,
            // Animated textures.
            Items.MAGMA_BLOCK,
            Items.PRISMARINE,
            Items.SEA_LANTERN,
            // Dynamic item models.
            Items.CLOCK,
            Items.COMPASS,
            Items.RECOVERY_COMPASS,
            // Translucent blocks.
            Items.STAINED_GLASS.pick(DyeColor.RED),
            Items.STAINED_GLASS.pick(DyeColor.BLUE),
            Items.SLIME_BLOCK,
            Items.HONEY_BLOCK,
            // Glint by default.
            Items.ENCHANTED_GOLDEN_APPLE)) {
      items.add(new ItemStack(item));
    }
    items.add(sword);
    items.add(chestplate);
    items.add(glintingBow);
    for (int map = 0; map < MAP_COUNT; map++) {
      items.add(MapItem.create(level, ORIGIN.getX(), ORIGIN.getZ(), MAP_SCALE, false, false));
    }
    return List.copyOf(items);
  }

  /** Frames with an item around the lattice. */
  static JsonObject census(ServerLevel level) {
    int frames =
        level
            .getEntitiesOfClass(ItemFrame.class, area(), frame -> !frame.getItem().isEmpty())
            .size();
    JsonObject census = new JsonObject();
    census.addProperty("itemFramesWithItems", frames);
    census.addProperty("complete", frames == EXPECTED_ITEM_FRAMES);
    return census;
  }

  /** What the client has and renders from the scene camera; must run on the render thread. */
  static JsonObject clientCensus(Minecraft minecraft) {
    return new ClientCensus(minecraft)
        .entities(
            "itemFrames",
            ItemFrame.class,
            area(),
            frame -> !frame.getItem().isEmpty(),
            EXPECTED_ITEM_FRAMES)
        .sections(area())
        .toJson();
  }

  /** The lattice with its outer frames (one block beyond the outer support blocks). */
  private static AABB area() {
    return new AABB(
        ORIGIN.getX() - 1,
        ORIGIN.getY() - 1,
        ORIGIN.getZ() - 1,
        ORIGIN.getX() + (CELLS_X - 1) * CELL_PITCH + 2,
        ORIGIN.getY() + (CELLS_Y - 1) * CELL_PITCH + 2,
        ORIGIN.getZ() + (CELLS_Z - 1) * CELL_PITCH + 2);
  }
}

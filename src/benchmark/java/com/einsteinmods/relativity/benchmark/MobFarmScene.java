package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Mob-farm scene: a glass pen crammed with husks (zombies that do not burn, so daylight cannot thin
 * the herd) and, out of their follow range, a pen of villagers. Mobs are persistent and skip {@code
 * finalizeSpawn}, so every session gets the same adults with no random equipment; the cramming
 * gamerule is 0 so the herd keeps its size while vanilla still computes every push.
 */
final class MobFarmScene {
  static final BlockPos ORIGIN = new BlockPos(0, 200, 3000);
  static final int HUSK_COUNT = 300;
  static final int VILLAGER_COUNT = 50;
  static final int HUSK_PEN_SIDE = 8;
  static final int VILLAGER_PEN_SIDE = 10;

  /** Villager pen offset along z: beyond the husks' 35-block follow range. */
  static final int VILLAGER_PEN_OFFSET = 48;

  /** Smallest render distance whose fog and section range hold the villager pen (~78 blocks). */
  static final int RENDER_DISTANCE = 6;

  static final CameraPose CAMERA =
      new CameraPose(
          ORIGIN.getX() + VILLAGER_PEN_SIDE / 2.0, ORIGIN.getY() + 24, ORIGIN.getZ() - 16, 0f, 35f);

  private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
  private static final int WALL_HEIGHT = 3;
  private static final double CELL_CENTER = 0.5;

  private MobFarmScene() {}

  static void build(ServerLevel level) {
    BlockPos huskPen = ORIGIN;
    BlockPos villagerPen = ORIGIN.offset(0, 0, VILLAGER_PEN_OFFSET);
    buildPen(level, huskPen, HUSK_PEN_SIDE);
    buildPen(level, villagerPen, VILLAGER_PEN_SIDE);
    spawn(level, EntityTypes.HUSK, HUSK_COUNT, huskPen, HUSK_PEN_SIDE);
    spawn(level, EntityTypes.VILLAGER, VILLAGER_COUNT, villagerPen, VILLAGER_PEN_SIDE);
  }

  private static void buildPen(ServerLevel level, BlockPos corner, int side) {
    BlockState glass = Blocks.GLASS.defaultBlockState();
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    for (int x = -1; x <= side; x++) {
      for (int z = -1; z <= side; z++) {
        level.setBlock(
            pos.set(corner.getX() + x, corner.getY(), corner.getZ() + z), glass, PLACE_FLAGS);
        boolean border = x == -1 || z == -1 || x == side || z == side;
        if (border) {
          for (int height = 1; height <= WALL_HEIGHT; height++) {
            level.setBlock(
                pos.set(corner.getX() + x, corner.getY() + height, corner.getZ() + z),
                glass,
                PLACE_FLAGS);
          }
        }
      }
    }
  }

  private static void spawn(
      ServerLevel level, EntityType<? extends Mob> type, int count, BlockPos corner, int side) {
    int cells = side * side;
    for (int index = 0; index < count; index++) {
      Mob mob = type.create(level, EntitySpawnReason.COMMAND);
      if (mob == null) {
        throw new IllegalStateException("Could not create " + type);
      }
      int cell = index % cells;
      mob.setPos(
          corner.getX() + cell % side + CELL_CENTER,
          corner.getY() + 1,
          corner.getZ() + cell / side + CELL_CENTER);
      mob.setPersistenceRequired();
      level.addFreshEntity(mob);
    }
  }

  /** Living husks and villagers inside the pens. */
  static JsonObject census(ServerLevel level) {
    AABB huskPen = penArea(ORIGIN, HUSK_PEN_SIDE);
    AABB villagerPen = penArea(ORIGIN.offset(0, 0, VILLAGER_PEN_OFFSET), VILLAGER_PEN_SIDE);
    int husks = level.getEntitiesOfClass(Husk.class, huskPen, Husk::isAlive).size();
    int villagers = level.getEntitiesOfClass(Villager.class, villagerPen, Villager::isAlive).size();
    JsonObject census = new JsonObject();
    census.addProperty("husks", husks);
    census.addProperty("villagers", villagers);
    census.addProperty("complete", husks == HUSK_COUNT && villagers == VILLAGER_COUNT);
    return census;
  }

  /** What the client has and renders from the scene camera; must run on the render thread. */
  static JsonObject clientCensus(Minecraft minecraft) {
    AABB huskPen = penArea(ORIGIN, HUSK_PEN_SIDE);
    AABB villagerPen = penArea(ORIGIN.offset(0, 0, VILLAGER_PEN_OFFSET), VILLAGER_PEN_SIDE);
    return new ClientCensus(minecraft)
        .entities("husks", Husk.class, huskPen, Husk::isAlive, HUSK_COUNT)
        .entities("villagers", Villager.class, villagerPen, Villager::isAlive, VILLAGER_COUNT)
        .sections(huskPen.minmax(villagerPen))
        .toJson();
  }

  private static AABB penArea(BlockPos corner, int side) {
    return new AABB(
        corner.getX() - 1,
        corner.getY(),
        corner.getZ() - 1,
        corner.getX() + side + 1,
        corner.getY() + WALL_HEIGHT + 1,
        corner.getZ() + side + 1);
  }
}

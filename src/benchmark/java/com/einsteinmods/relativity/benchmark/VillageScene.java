package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * Village scene: a glass-walled stone yard with {@value #VILLAGER_COUNT} villagers, as many beds
 * and as many job-site blocks (all thirteen kinds) and a bell, with walking lanes between the rows.
 * Time stays at noon (work schedule), so after the settle the villagers claim beds and jobs and
 * keep walking between job sites and the bell: pathfinding and brains at village scale. Villagers
 * are persistent and skip {@code finalizeSpawn} (same unemployed adults every session); they never
 * sleep at frozen noon, so no iron golem spawns and the census stays exact.
 */
final class VillageScene {
  static final BlockPos ORIGIN = new BlockPos(3000, 200, 3000);
  static final int COLUMNS = 20;
  static final int ROWS = 5;
  static final int VILLAGER_COUNT = COLUMNS * ROWS;
  static final int EXPECTED_BEDS = COLUMNS * ROWS;
  static final int EXPECTED_JOB_SITES = COLUMNS * ROWS;

  /** Blocks between two beds or job sites of a row: one free block between neighbours. */
  static final int COLUMN_PITCH = 2;

  /** Blocks between two rows: a bed (two blocks) and a two-block lane. */
  static final int ROW_PITCH = 4;

  static final int WIDTH = COLUMNS * COLUMN_PITCH;
  static final int BED_AREA_DEPTH = ROWS * ROW_PITCH;

  /** Lane between the bed rows and the job-site rows, where the bell stands. */
  static final int BELL_Z = BED_AREA_DEPTH + 1;

  static final int JOB_AREA_START_Z = BELL_Z + 2;
  static final int DEPTH = JOB_AREA_START_Z + ROWS * ROW_PITCH;

  /**
   * Villagers settle in this long before measuring: claiming beds and jobs takes many POI scans.
   */
  static final Duration SETTLE = Duration.ofSeconds(60);

  /** Smallest render distance whose sections hold the yard from the camera above it. */
  static final int RENDER_DISTANCE = 4;

  /** Straight down from above the yard's centre: every villager is inside the view frustum. */
  static final CameraPose CAMERA =
      new CameraPose(
          ORIGIN.getX() + WIDTH / 2.0, ORIGIN.getY() + 40, ORIGIN.getZ() + DEPTH / 2.0, 0f, 90f);

  private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
  private static final int WALL_HEIGHT = 3;
  private static final int LANE_OFFSET = 2;
  private static final double CELL_CENTER = 0.5;
  private static final List<Block> JOB_SITES =
      List.of(
          Blocks.COMPOSTER,
          Blocks.LECTERN,
          Blocks.BARREL,
          Blocks.SMOKER,
          Blocks.BLAST_FURNACE,
          Blocks.CARTOGRAPHY_TABLE,
          Blocks.FLETCHING_TABLE,
          Blocks.GRINDSTONE,
          Blocks.LOOM,
          Blocks.SMITHING_TABLE,
          Blocks.STONECUTTER,
          Blocks.BREWING_STAND,
          Blocks.CAULDRON);

  private VillageScene() {}

  static void build(ServerLevel level) {
    BlockState floor = Blocks.STONE.defaultBlockState();
    BlockState glass = Blocks.GLASS.defaultBlockState();
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    for (int x = -1; x <= WIDTH; x++) {
      for (int z = -1; z <= DEPTH; z++) {
        level.setBlock(at(pos, x, 0, z), floor, PLACE_FLAGS);
        if (x == -1 || z == -1 || x == WIDTH || z == DEPTH) {
          for (int height = 1; height <= WALL_HEIGHT; height++) {
            level.setBlock(at(pos, x, height, z), glass, PLACE_FLAGS);
          }
        }
      }
    }
    BlockState foot =
        Blocks.BED
            .white()
            .defaultBlockState()
            .setValue(BedBlock.FACING, Direction.SOUTH)
            .setValue(BedBlock.PART, BedPart.FOOT);
    BlockState head = foot.setValue(BedBlock.PART, BedPart.HEAD);
    for (int row = 0; row < ROWS; row++) {
      for (int column = 0; column < COLUMNS; column++) {
        int x = column * COLUMN_PITCH;
        int bedZ = row * ROW_PITCH;
        level.setBlock(at(pos, x, 1, bedZ), foot, PLACE_FLAGS);
        level.setBlock(at(pos, x, 1, bedZ + 1), head, PLACE_FLAGS);
        Block jobSite = JOB_SITES.get((row * COLUMNS + column) % JOB_SITES.size());
        level.setBlock(
            at(pos, x, 1, JOB_AREA_START_Z + row * ROW_PITCH),
            jobSite.defaultBlockState(),
            PLACE_FLAGS);
      }
    }
    level.setBlock(at(pos, WIDTH / 2, 1, BELL_Z), Blocks.BELL.defaultBlockState(), PLACE_FLAGS);
    for (int index = 0; index < VILLAGER_COUNT; index++) {
      Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
      if (villager == null) {
        throw new IllegalStateException("Could not create a villager");
      }
      villager.setPos(
          ORIGIN.getX() + (index % COLUMNS) * COLUMN_PITCH + CELL_CENTER,
          ORIGIN.getY() + 1,
          ORIGIN.getZ() + (index / COLUMNS) * ROW_PITCH + LANE_OFFSET + CELL_CENTER);
      villager.setPersistenceRequired();
      level.addFreshEntity(villager);
    }
  }

  private static BlockPos.MutableBlockPos at(BlockPos.MutableBlockPos pos, int x, int y, int z) {
    return pos.set(ORIGIN.getX() + x, ORIGIN.getY() + y, ORIGIN.getZ() + z);
  }

  /** Villagers, beds and job sites in the yard, and how many villagers hold a bed and a job. */
  static JsonObject census(ServerLevel level) {
    List<Villager> villagers = level.getEntitiesOfClass(Villager.class, yard(), Villager::isAlive);
    int employed = 0;
    int housed = 0;
    for (Villager villager : villagers) {
      if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
        employed++;
      }
      if (villager.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
        housed++;
      }
    }
    int beds = 0;
    int jobSites = 0;
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    for (int row = 0; row < ROWS; row++) {
      for (int column = 0; column < COLUMNS; column++) {
        int x = column * COLUMN_PITCH;
        BlockState bedHead = level.getBlockState(at(pos, x, 1, row * ROW_PITCH + 1));
        if (bedHead.getBlock() instanceof BedBlock
            && bedHead.getValue(BedBlock.PART) == BedPart.HEAD) {
          beds++;
        }
        BlockState jobSite = level.getBlockState(at(pos, x, 1, JOB_AREA_START_Z + row * ROW_PITCH));
        if (JOB_SITES.contains(jobSite.getBlock())) {
          jobSites++;
        }
      }
    }
    JsonObject census = new JsonObject();
    census.addProperty("villagers", villagers.size());
    census.addProperty("beds", beds);
    census.addProperty("jobSites", jobSites);
    census.addProperty("employedVillagers", employed);
    census.addProperty("housedVillagers", housed);
    census.addProperty(
        "complete",
        villagers.size() == VILLAGER_COUNT
            && beds == EXPECTED_BEDS
            && jobSites == EXPECTED_JOB_SITES);
    return census;
  }

  /** What the client has and renders from the scene camera; must run on the render thread. */
  static JsonObject clientCensus(Minecraft minecraft) {
    return new ClientCensus(minecraft)
        .entities("villagers", Villager.class, yard(), Villager::isAlive, VILLAGER_COUNT)
        .sections(yard())
        .toJson();
  }

  private static AABB yard() {
    return new AABB(
        ORIGIN.getX() - 1,
        ORIGIN.getY(),
        ORIGIN.getZ() - 1,
        ORIGIN.getX() + WIDTH + 1,
        ORIGIN.getY() + WALL_HEIGHT + 1,
        ORIGIN.getZ() + DEPTH + 1);
  }
}

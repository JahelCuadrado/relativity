package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of a scene census, so a lower render distance that drops part of a scene shows in the
 * report: entities the client holds and the ones vanilla would render this frame ({@code
 * LevelExtractor#isEntityVisible}: render distance, frustum, compiled section), and the scene's
 * chunk sections that are compiled and faded in. Must run on the render thread.
 */
final class ClientCensus {
  private static final float STILL_PARTIAL_TICK = 0f;

  /** Scene areas end one block past their last block (exclusive maximum). */
  private static final double EXCLUSIVE_EDGE = 1.0;

  private final Minecraft minecraft;
  private final JsonObject json = new JsonObject();
  private boolean complete = true;

  ClientCensus(Minecraft minecraft) {
    this.minecraft = minecraft;
  }

  <T extends Entity> ClientCensus entities(
      String label, Class<T> type, AABB area, Predicate<? super T> filter, int expected) {
    ClientLevel level = minecraft.level;
    if (level == null) {
      complete = false;
      return this;
    }
    Camera camera = minecraft.gameRenderer.mainCamera();
    Vec3 eye = camera.position();
    long fadeMillis = Util.toMillis(minecraft.options.chunkSectionFadeInTime().get());
    List<T> entities = level.getEntitiesOfClass(type, area, filter::test);
    int rendered = 0;
    for (T entity : entities) {
      if (minecraft.levelExtractor.isEntityVisible(
          entity,
          camera.getCullFrustum(),
          eye.x(),
          eye.y(),
          eye.z(),
          STILL_PARTIAL_TICK,
          fadeMillis)) {
        rendered++;
      }
    }
    json.addProperty(label + "OnClient", entities.size());
    json.addProperty(label + "Rendered", rendered);
    complete &= entities.size() == expected && rendered == expected;
    return this;
  }

  ClientCensus sections(AABB area) {
    long fadeMillis = Util.toMillis(minecraft.options.chunkSectionFadeInTime().get());
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    int total = 0;
    int ready = 0;
    for (int x = section(area.minX); x <= section(area.maxX - EXCLUSIVE_EDGE); x++) {
      for (int y = section(area.minY); y <= section(area.maxY - EXCLUSIVE_EDGE); y++) {
        for (int z = section(area.minZ); z <= section(area.maxZ - EXCLUSIVE_EDGE); z++) {
          total++;
          pos.set(
              SectionPos.sectionToBlockCoord(x),
              SectionPos.sectionToBlockCoord(y),
              SectionPos.sectionToBlockCoord(z));
          if (minecraft.levelRenderer.isSectionCompiledAndVisible(pos, fadeMillis)) {
            ready++;
          }
        }
      }
    }
    json.addProperty("sections", total);
    json.addProperty("sectionsCompiled", ready);
    complete &= ready == total;
    return this;
  }

  private static int section(double coordinate) {
    return SectionPos.blockToSectionCoord(coordinate);
  }

  JsonObject toJson() {
    json.addProperty("complete", complete);
    return json;
  }
}

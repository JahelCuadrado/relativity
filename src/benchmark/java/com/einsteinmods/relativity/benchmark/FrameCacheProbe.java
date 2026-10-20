package com.einsteinmods.relativity.benchmark;

import com.einsteinmods.relativity.client.frames.ItemFrameGeometryCache;
import com.google.gson.JsonObject;

/**
 * Dev-only view into {@code item_frame_cache} during a measured round: how item frame submits were
 * served (replayed, captured, vanilla) and the render-thread CPU spent submitting entities ({@code
 * LevelRenderer#submitFeatures}) and building their vertices ({@code
 * FeatureRenderDispatcher#prepareFrame}). Render thread only; read between rounds.
 */
public final class FrameCacheProbe {
  private static final double NANOS_PER_MILLI = 1_000_000.0;

  private static boolean recording;
  private static long frames;
  private static long itemFrameSubmits;
  private static long itemReplayed;
  private static long itemCapturing;
  private static long itemVanilla;
  private static long meshReplayed;
  private static long meshCapturing;
  private static long meshVanilla;
  private static long submitCpu;
  private static long prepareCpu;
  private static long stageStart;
  private static long capturedAtStart;
  private static long replayedAtStart;
  private static long rewrittenAtStart;

  private FrameCacheProbe() {}

  static void start() {
    frames = 0;
    itemFrameSubmits = 0;
    itemReplayed = 0;
    itemCapturing = 0;
    itemVanilla = 0;
    meshReplayed = 0;
    meshCapturing = 0;
    meshVanilla = 0;
    submitCpu = 0;
    prepareCpu = 0;
    ItemFrameGeometryCache cache = ItemFrameGeometryCache.INSTANCE;
    capturedAtStart = cache.capturedSubmits();
    replayedAtStart = cache.replayedSubmits();
    rewrittenAtStart = cache.rewrittenSubmits();
    recording = true;
  }

  static JsonObject stop() {
    recording = false;
    ItemFrameGeometryCache cache = ItemFrameGeometryCache.INSTANCE;
    JsonObject probe = new JsonObject();
    probe.addProperty("frames", frames);
    probe.addProperty("itemFrameSubmitsPerFrame", perFrame(itemFrameSubmits));
    probe.addProperty("itemReplayedPerFrame", perFrame(itemReplayed));
    probe.addProperty("itemCapturingPerFrame", perFrame(itemCapturing));
    probe.addProperty("itemVanillaPerFrame", perFrame(itemVanilla));
    probe.addProperty("meshReplayedPerFrame", perFrame(meshReplayed));
    probe.addProperty("meshCapturingPerFrame", perFrame(meshCapturing));
    probe.addProperty("meshVanillaPerFrame", perFrame(meshVanilla));
    probe.addProperty("capturedOkPerFrame", perFrame(cache.capturedSubmits() - capturedAtStart));
    probe.addProperty("replayedPerFrame", perFrame(cache.replayedSubmits() - replayedAtStart));
    probe.addProperty("rewrittenPerFrame", perFrame(cache.rewrittenSubmits() - rewrittenAtStart));
    probe.addProperty("cachedSubmits", cache.cachedSubmits());
    probe.addProperty("cachedKiB", cache.cachedBytes() / 1024.0);
    probe.addProperty("submitFeaturesCpuMsPerFrame", perFrame(submitCpu) / NANOS_PER_MILLI);
    probe.addProperty("prepareFrameCpuMsPerFrame", perFrame(prepareCpu) / NANOS_PER_MILLI);
    return probe;
  }

  private static double perFrame(long total) {
    return frames == 0 ? 0 : total / (double) frames;
  }

  public static void onItemFrameSubmit() {
    if (recording) {
      itemFrameSubmits++;
    }
  }

  public static void onItemOutcome(ItemFrameGeometryCache.Outcome outcome) {
    if (!recording) {
      return;
    }
    switch (outcome) {
      case REPLAYED -> itemReplayed++;
      case CAPTURING -> itemCapturing++;
      case VANILLA -> itemVanilla++;
    }
  }

  public static void onMeshOutcome(ItemFrameGeometryCache.Outcome outcome) {
    if (!recording) {
      return;
    }
    switch (outcome) {
      case REPLAYED -> meshReplayed++;
      case CAPTURING -> meshCapturing++;
      case VANILLA -> meshVanilla++;
    }
  }

  public static void onStageStart() {
    if (recording) {
      stageStart = ThreadCpuClock.now();
    }
  }

  public static void onSubmitFeaturesEnd() {
    if (recording) {
      frames++;
      submitCpu += ThreadCpuClock.now() - stageStart;
    }
  }

  public static void onPrepareFrameEnd() {
    if (recording) {
      prepareCpu += ThreadCpuClock.now() - stageStart;
    }
  }
}

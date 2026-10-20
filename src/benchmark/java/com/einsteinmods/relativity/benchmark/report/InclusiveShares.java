package com.einsteinmods.relativity.benchmark.report;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/**
 * Dev-only: inclusive shares of JFR samples (Java and native, idle waits excluded) by category: a
 * sample counts for a category when any frame of its stack belongs to it, so categories overlap and
 * show the most a subsystem could save of the sampled threads' time. Optionally cut into time
 * windows (startup phases). Stacks deeper than JFR's depth limit lose their bottom frames; the
 * truncated share is reported so caller-side categories can be read as lower bounds.
 */
final class InclusiveShares {
  private static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
  private static final String NATIVE_SAMPLE = "jdk.NativeMethodSample";
  private static final String SAMPLED_THREAD = "sampledThread";
  private static final String UNKNOWN = "<unknown>";
  private static final String ALL_THREADS = "all";
  private static final double PERCENT = 100.0;
  private static final Pattern THREAD_NUMBER_SUFFIX = Pattern.compile("[\\s#-]*\\d+$");
  private static final List<String> IDLE_NATIVE_PREFIXES =
      List.of(
          "sun.nio.ch.",
          "io.netty.channel.kqueue.",
          "java.net.",
          "java.lang.Thread",
          "jdk.internal.misc.Unsafe.park",
          "java.lang.Object.wait");

  /** A named group of frames: any frame whose "type.method" starts with one of the prefixes. */
  record Category(String name, List<String> prefixes) {
    boolean matches(String frame) {
      for (String prefix : prefixes) {
        if (frame.startsWith(prefix)) {
          return true;
        }
      }
      return false;
    }
  }

  /** A time window of the recording, as epoch milliseconds [from, to). */
  record Window(String name, long fromEpochMillis, long toEpochMillis) {}

  static final List<Category> STARTUP_CATEGORIES =
      List.of(
          new Category("mixinTransform", List.of("org.spongepowered.asm.mixin.transformer.")),
          new Category(
              "classLoading",
              List.of(
                  "java.lang.ClassLoader.defineClass",
                  "net.fabricmc.loader.impl.launch.knot.",
                  "jdk.internal.loader.")),
          new Category("fabricLoader", List.of("net.fabricmc.loader.")),
          new Category(
              "registriesBootstrap",
              List.of("net.minecraft.server.Bootstrap", "net.minecraft.core.registries.")),
          new Category(
              "dataFixers", List.of("com.mojang.datafixers.", "net.minecraft.util.datafix.")),
          new Category("resourceReload", List.of("net.minecraft.server.packs.resources.")),
          new Category("models", List.of("net.minecraft.client.resources.model.")),
          new Category("textures", List.of("net.minecraft.client.renderer.texture.")),
          new Category(
              "shaders",
              List.of("net.minecraft.client.renderer.ShaderManager", "com.mojang.blaze3d.")),
          new Category("fonts", List.of("net.minecraft.client.gui.font.")),
          new Category("sounds", List.of("net.minecraft.client.sounds.")),
          new Category("integratedServer", List.of("net.minecraft.server.MinecraftServer")));

  static final List<Category> WORLDGEN_CATEGORIES =
      List.of(
          new Category(
              "noisePrimitives",
              List.of(
                  "net.minecraft.world.level.levelgen.synth.",
                  "com.ishland.c2me.opts.natives_math.")),
          new Category(
              "densityFunctions",
              List.of(
                  "net.minecraft.world.level.levelgen.densityfunction.",
                  "net.minecraft.world.level.levelgen.NoiseChunk",
                  "net.minecraft.world.level.levelgen.NoiseRouter",
                  "com.ishland.c2me.opts.dfc.")),
          new Category(
              "fillNoise",
              List.of("net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.doFill")),
          new Category(
              "surfaceRules",
              List.of(
                  "net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.buildSurface",
                  "net.minecraft.world.level.levelgen.material.")),
          new Category(
              "carvers",
              List.of(
                  "net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.generateCarvers",
                  "net.minecraft.world.level.levelgen.carver.")),
          new Category(
              "biomes",
              List.of(
                  "net.minecraft.world.level.chunk.ChunkGenerator.doCreateBiomes",
                  "net.minecraft.world.level.biome.Climate")),
          new Category(
              "features",
              List.of(
                  "net.minecraft.world.level.chunk.ChunkGenerator.applyBiomeDecoration",
                  "net.minecraft.world.level.levelgen.feature.",
                  "net.minecraft.world.level.levelgen.placement.")),
          new Category(
              "lighting",
              List.of("net.minecraft.world.level.lighting.", "ca.spottedleaf.starlight.")),
          new Category(
              "chunkSaving",
              List.of(
                  "net.minecraft.world.level.chunk.storage.",
                  "net.minecraft.nbt.",
                  "com.ishland.c2me.rewrites.chunk_serializer.")));

  private final List<Category> categories;
  private final Map<String, Map<String, long[]>> counts = new LinkedHashMap<>();
  private final Map<String, Map<String, Long>> threadSamples = new LinkedHashMap<>();
  private final Map<String, Long> truncated = new LinkedHashMap<>();

  InclusiveShares(List<Category> categories) {
    this.categories = List.copyOf(categories);
  }

  /** Adds one recording; with no windows every sample counts in the window "all". */
  void add(Path recording, List<Window> windows) throws IOException {
    try (RecordingFile file = new RecordingFile(recording)) {
      while (file.hasMoreEvents()) {
        RecordedEvent event = file.readEvent();
        String type = event.getEventType().getName();
        if (EXECUTION_SAMPLE.equals(type) || NATIVE_SAMPLE.equals(type)) {
          addSample(event, NATIVE_SAMPLE.equals(type), windows);
        }
      }
    }
  }

  private void addSample(RecordedEvent event, boolean nativeSample, List<Window> windows) {
    RecordedStackTrace stack = event.getStackTrace();
    if (stack == null || stack.getFrames().isEmpty()) {
      return;
    }
    List<RecordedFrame> frames = stack.getFrames();
    if (nativeSample && isIdle(frameName(frames.getFirst()))) {
      return;
    }
    String window = window(event.getStartTime(), windows);
    if (window == null) {
      return;
    }
    String thread = threadGroup(event);
    Map<String, Long> threads = threadSamples.computeIfAbsent(window, key -> new LinkedHashMap<>());
    threads.merge(thread, 1L, Long::sum);
    threads.merge(ALL_THREADS, 1L, Long::sum);
    if (stack.isTruncated()) {
      truncated.merge(window, 1L, Long::sum);
    }
    Map<String, long[]> byCategory = counts.computeIfAbsent(window, key -> new LinkedHashMap<>());
    String[] names = new String[frames.size()];
    for (int index = 0; index < names.length; index++) {
      names[index] = frameName(frames.get(index));
    }
    for (int index = 0; index < categories.size(); index++) {
      Category category = categories.get(index);
      for (String name : names) {
        if (category.matches(name)) {
          byCategory.computeIfAbsent(thread, key -> new long[categories.size()])[index]++;
          byCategory.computeIfAbsent(ALL_THREADS, key -> new long[categories.size()])[index]++;
          break;
        }
      }
    }
  }

  private static String window(Instant time, List<Window> windows) {
    if (windows.isEmpty()) {
      return ALL_THREADS;
    }
    long millis = time.toEpochMilli();
    for (Window window : windows) {
      if (millis >= window.fromEpochMillis() && millis < window.toEpochMillis()) {
        return window.name();
      }
    }
    return null;
  }

  /**
   * Per window: samples per thread group, truncated-stack share, and per category the samples and
   * percent of each thread group's samples (and of all samples) whose stack contains it.
   */
  JsonObject toJson() {
    JsonObject json = new JsonObject();
    for (Map.Entry<String, Map<String, Long>> window : threadSamples.entrySet()) {
      Map<String, Long> threads = window.getValue();
      long total = threads.get(ALL_THREADS);
      JsonObject windowJson = new JsonObject();
      windowJson.addProperty("samples", total);
      windowJson.addProperty(
          "truncatedPercent", truncated.getOrDefault(window.getKey(), 0L) * PERCENT / total);
      JsonObject threadsJson = new JsonObject();
      threads.entrySet().stream()
          .filter(entry -> !entry.getKey().equals(ALL_THREADS))
          .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
          .forEach(entry -> threadsJson.addProperty(entry.getKey(), entry.getValue()));
      windowJson.add("threadSamples", threadsJson);
      JsonObject categoriesJson = new JsonObject();
      Map<String, long[]> byThread = counts.getOrDefault(window.getKey(), Map.of());
      for (int index = 0; index < categories.size(); index++) {
        JsonObject category = new JsonObject();
        for (Map.Entry<String, long[]> thread : byThread.entrySet()) {
          long samples = thread.getValue()[index];
          if (samples > 0) {
            category.addProperty(thread.getKey(), samples * PERCENT / threads.get(thread.getKey()));
          }
        }
        categoriesJson.add(categories.get(index).name(), category);
      }
      windowJson.add("percentOfThreadSamples", categoriesJson);
      json.add(window.getKey(), windowJson);
    }
    return json;
  }

  private static boolean isIdle(String frame) {
    return IDLE_NATIVE_PREFIXES.stream().anyMatch(frame::startsWith);
  }

  private static String frameName(RecordedFrame frame) {
    return frame.getMethod().getType().getName() + "." + frame.getMethod().getName();
  }

  private static String threadGroup(RecordedEvent event) {
    RecordedThread thread = event.getThread(SAMPLED_THREAD);
    if (thread == null) {
      return UNKNOWN;
    }
    String name = thread.getJavaName() != null ? thread.getJavaName() : thread.getOSName();
    if (name == null) {
      return UNKNOWN;
    }
    String group = name;
    String previous;
    do {
      previous = group;
      group = THREAD_NUMBER_SUFFIX.matcher(group).replaceAll("");
    } while (!group.equals(previous));
    return group.isEmpty() ? name : group;
  }
}

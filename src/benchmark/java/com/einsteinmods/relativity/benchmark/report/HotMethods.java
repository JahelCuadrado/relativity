package com.einsteinmods.relativity.benchmark.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/**
 * Aggregates JFR execution samples of one scenario over every session. Three views: the top frame
 * (self time), the first "game" frame (the first frame from the top outside the JDK and generic
 * libraries, so a {@code HashMap.get} is charged to the Minecraft or mod method that called it),
 * and that game frame's package, which names the vanilla subsystem or the mod paying. Mixin handler
 * methods merged into vanilla classes ({@code handler$...$modid$...}) are charged to their mod.
 * Native samples (render thread inside the graphics driver, for example) are counted apart, without
 * idle selector and sleep waits.
 */
final class HotMethods {
  private static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
  private static final String NATIVE_SAMPLE = "jdk.NativeMethodSample";
  private static final String SAMPLED_THREAD = "sampledThread";
  private static final int PACKAGE_DEPTH = 5;
  private static final int TOP_METHODS = 30;
  private static final int TOP_PACKAGES = 25;
  private static final int TOP_THREADS = 12;
  private static final int TOP_THREAD_GROUPS_DETAILED = 4;
  private static final int TOP_METHODS_PER_THREAD = 15;
  private static final double PERCENT = 100.0;
  private static final String UNKNOWN = "<unknown>";
  private static final Pattern THREAD_NUMBER_SUFFIX = Pattern.compile("[\\s#-]*\\d+$");
  private static final Pattern MIXIN_HANDLER =
      Pattern.compile("^[a-zA-Z]+\\$[a-z0-9]+\\$([a-z0-9_-]+)\\$");
  private static final List<String> IDLE_NATIVE_PREFIXES =
      List.of("sun.nio.ch.", "io.netty.channel.kqueue.", "java.net.", "java.lang.Thread");
  private static final List<String> NON_GAME_PREFIXES =
      List.of(
          "java.",
          "javax.",
          "jdk.",
          "sun.",
          "com.sun.",
          "it.unimi.dsi.fastutil.",
          "com.google.",
          "org.lwjgl.",
          "io.netty.",
          "org.joml.",
          "org.slf4j.",
          "org.apache.",
          "com.mojang.serialization.",
          "com.mojang.datafixers.",
          "com.mojang.brigadier.",
          "org.spongepowered.",
          "net.fabricmc.loader.");

  private final Map<String, Long> selfMethods = new HashMap<>();
  private final Map<String, Long> gameMethods = new HashMap<>();
  private final Map<String, Long> gamePackages = new HashMap<>();
  private final Map<String, Long> threads = new HashMap<>();
  private final Map<String, Map<String, Long>> gameMethodsByThread = new HashMap<>();
  private final Map<String, Long> nativeThreads = new HashMap<>();
  private final Map<String, Long> nativeMethods = new HashMap<>();
  private long javaSamples;
  private long nativeSamples;
  private int recordings;

  void add(Path recording) throws IOException {
    recordings++;
    try (RecordingFile file = new RecordingFile(recording)) {
      while (file.hasMoreEvents()) {
        RecordedEvent event = file.readEvent();
        String type = event.getEventType().getName();
        if (EXECUTION_SAMPLE.equals(type)) {
          addJavaSample(event);
        } else if (NATIVE_SAMPLE.equals(type)) {
          addNativeSample(event);
        }
      }
    }
  }

  boolean isEmpty() {
    return javaSamples == 0 && nativeSamples == 0;
  }

  private void addJavaSample(RecordedEvent event) {
    RecordedStackTrace stack = event.getStackTrace();
    if (stack == null || stack.getFrames().isEmpty()) {
      return;
    }
    javaSamples++;
    String thread = threadGroup(event);
    increment(threads, thread);
    List<RecordedFrame> frames = stack.getFrames();
    increment(selfMethods, thread + " | " + methodName(frames.getFirst()));
    RecordedFrame game = firstGameFrame(frames);
    String gameMethod = methodName(game);
    increment(gameMethods, thread + " | " + gameMethod);
    increment(gameMethodsByThread.computeIfAbsent(thread, key -> new HashMap<>()), gameMethod);
    increment(gamePackages, owner(game));
  }

  private void addNativeSample(RecordedEvent event) {
    RecordedStackTrace stack = event.getStackTrace();
    if (stack != null
        && !stack.getFrames().isEmpty()
        && isIdleWait(stack.getFrames().getFirst().getMethod().getType().getName())) {
      return;
    }
    nativeSamples++;
    String thread = threadGroup(event);
    increment(nativeThreads, thread);
    if (stack != null && !stack.getFrames().isEmpty()) {
      increment(nativeMethods, thread + " | " + methodName(stack.getFrames().getFirst()));
    }
  }

  /** Native samples of threads parked in a selector or sleep are idle, not work. */
  private static boolean isIdleWait(String type) {
    return IDLE_NATIVE_PREFIXES.stream().anyMatch(type::startsWith);
  }

  private static RecordedFrame firstGameFrame(List<RecordedFrame> frames) {
    for (RecordedFrame frame : frames) {
      String type = frame.getMethod().getType().getName();
      if (NON_GAME_PREFIXES.stream().noneMatch(type::startsWith)) {
        return frame;
      }
    }
    return frames.getFirst();
  }

  private static String owner(RecordedFrame frame) {
    String type = frame.getMethod().getType().getName();
    String packageName = truncatePackage(type);
    Matcher handler = MIXIN_HANDLER.matcher(frame.getMethod().getName());
    return handler.find() ? "mixin:" + handler.group(1) + " in " + packageName : packageName;
  }

  private static String truncatePackage(String type) {
    String[] parts = type.split("\\.");
    int depth = Math.min(PACKAGE_DEPTH, parts.length - 1);
    return depth <= 0 ? type : String.join(".", List.of(parts).subList(0, depth));
  }

  private static String methodName(RecordedFrame frame) {
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

  private static void increment(Map<String, Long> counts, String key) {
    counts.merge(key, 1L, Long::sum);
  }

  JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("recordings", recordings);
    json.addProperty("javaSamples", javaSamples);
    json.addProperty("nativeSamples", nativeSamples);
    json.add("threads", ranked(threads, javaSamples, TOP_THREADS));
    json.add("topSelfMethods", ranked(selfMethods, javaSamples, TOP_METHODS));
    json.add("topGameFrames", ranked(gameMethods, javaSamples, TOP_METHODS));
    json.add("gameFramePackages", ranked(gamePackages, javaSamples, TOP_PACKAGES));
    JsonObject perThread = new JsonObject();
    for (Map.Entry<String, Long> thread : top(threads, TOP_THREAD_GROUPS_DETAILED)) {
      perThread.add(
          thread.getKey(),
          ranked(gameMethodsByThread.get(thread.getKey()), javaSamples, TOP_METHODS_PER_THREAD));
    }
    json.add("topGameFramesByThread", perThread);
    json.add("nativeThreads", ranked(nativeThreads, nativeSamples, TOP_THREADS));
    json.add("topNativeMethods", ranked(nativeMethods, nativeSamples, TOP_METHODS_PER_THREAD));
    return json;
  }

  private static JsonArray ranked(Map<String, Long> counts, long total, int limit) {
    JsonArray array = new JsonArray();
    for (Map.Entry<String, Long> entry : top(counts, limit)) {
      JsonObject row = new JsonObject();
      row.addProperty("name", entry.getKey());
      row.addProperty("samples", entry.getValue());
      row.addProperty("percent", total == 0 ? 0 : entry.getValue() * PERCENT / total);
      array.add(row);
    }
    return array;
  }

  private static List<Map.Entry<String, Long>> top(Map<String, Long> counts, int limit) {
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
        .limit(limit)
        .toList();
  }
}

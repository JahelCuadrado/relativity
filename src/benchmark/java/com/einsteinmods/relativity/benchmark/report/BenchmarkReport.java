package com.einsteinmods.relativity.benchmark.report;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Dev-only: turns {@code sessions/<n>/session.json} and the per-scenario {@code .jfr} files of one
 * profile into {@code report.json} (every number) and {@code summary.txt} (metrics with their
 * repeatability, then each scenario's hottest methods grouped by thread and package).
 */
public final class BenchmarkReport {
  private static final String SESSIONS = "sessions";
  private static final String SESSION_FILE = "session.json";
  private static final String STATUS_OK = "ok";
  private static final int SUMMARY_ROWS = 15;
  private static final int SUMMARY_PACKAGE_ROWS = 15;
  private static final int SUMMARY_THREAD_ROWS = 8;
  private static final int SUMMARY_ROWS_PER_THREAD = 10;

  /** Scenario name in session.json, and the stem of its JFR file. */
  private record Scenario(String key, String jfrStem) {}

  /** Where a metric lives: scenario, optional array of rounds/exits, path inside each element. */
  private record MetricSpec(String scenario, String array, String path) {
    String name() {
      return scenario + "." + (array == null ? "" : array + ".") + path;
    }
  }

  private static final List<Scenario> SCENARIOS =
      List.of(
          new Scenario("startup", "startup"),
          new Scenario("chunkGeneration", "chunk-generation"),
          new Scenario("denseBase", "dense-base"),
          new Scenario("mobFarm", "mob-farm"),
          new Scenario("itemFrameWall", "item-frame-wall"),
          new Scenario("village", "village"),
          new Scenario("sorter", "sorter"),
          new Scenario("worldExit", "world-exit"));

  private static final List<String> SCENES =
      List.of("denseBase", "mobFarm", "itemFrameWall", "village", "sorter");

  private static final String STARTUP_JFR = "startup";
  private static final String CHUNK_GENERATION = "chunkGeneration";
  private static final String CHUNK_GENERATION_JFR = "chunk-generation";

  private static final List<MetricSpec> METRICS = metrics();

  private BenchmarkReport() {}

  private static List<String> roundScenarios() {
    List<String> scenarios = new ArrayList<>();
    scenarios.add(CHUNK_GENERATION);
    scenarios.addAll(SCENES);
    return List.copyOf(scenarios);
  }

  private static List<MetricSpec> metrics() {
    List<MetricSpec> specs = new ArrayList<>();
    specs.add(new MetricSpec("startup", null, "jvmToModInitMs"));
    specs.add(new MetricSpec("startup", null, "jvmToTitleScreenMs"));
    specs.add(new MetricSpec("startup", null, "createAndJoinWorldMs"));
    specs.add(new MetricSpec("chunkGeneration", "rounds", "generatedChunksPerSecond"));
    List<String> roundScenarios = roundScenarios();
    for (String scenario : roundScenarios) {
      specs.add(new MetricSpec(scenario, "rounds", "renderWorkCpu.p50Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "renderWorkCpu.p95Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "renderWorkCpu.p99Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "serverTickCpu.p50Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "serverTickCpu.p95Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "renderWork.p50Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "serverTick.p50Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "serverTick.p95Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "serverTick.meanMs"));
      specs.add(new MetricSpec(scenario, "rounds", "frameTime.meanMs"));
      specs.add(new MetricSpec(scenario, "rounds", "frameTime.p50Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "frameTime.p95Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "frameTime.p99Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "renderFrame.p50Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "renderFrame.p95Ms"));
      specs.add(new MetricSpec(scenario, "rounds", "renderFrame.p99Ms"));
    }
    for (String exits : List.of("exitsAfterSession", "exitsAfterRejoin")) {
      specs.add(new MetricSpec("worldExit", exits, "toTitleScreenMs"));
      specs.add(new MetricSpec("worldExit", exits, "disconnectCallMs"));
      specs.add(new MetricSpec("worldExit", exits, "serverStoppedMs"));
    }
    specs.add(new MetricSpec("worldExit", "rejoinMs", ""));
    return List.copyOf(specs);
  }

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 2) {
      throw new IllegalArgumentException("Usage: BenchmarkReport <profile directory> <profile>");
    }
    Path profileDirectory = Path.of(arguments[0]);
    String profile = arguments[1];
    List<Path> sessionDirectories = sessionDirectories(profileDirectory.resolve(SESSIONS));
    List<JsonObject> sessions = new ArrayList<>();
    for (Path directory : sessionDirectories) {
      Path file = directory.resolve(SESSION_FILE);
      if (Files.exists(file)) {
        sessions.add(
            JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject());
      }
    }

    JsonObject report = new JsonObject();
    report.addProperty("profile", profile);
    report.addProperty("generatedAt", Instant.now().toString());
    report.addProperty("sessionDirectories", sessionDirectories.size());
    report.add("sessions", sessionStatuses(sessions));
    JsonArray cpuClocks = new JsonArray();
    for (JsonObject session : sessions) {
      if (session.has("cpuClock")) {
        cpuClocks.add(session.get("cpuClock"));
      }
    }
    report.add("cpuClock", cpuClocks);
    if (!sessions.isEmpty() && sessions.getFirst().has("environment")) {
      report.add("environment", sessions.getFirst().get("environment"));
    }
    JsonObject metrics = new JsonObject();
    List<String> summaryMetrics = new ArrayList<>();
    for (MetricSpec spec : METRICS) {
      MetricSeries series = new MetricSeries();
      for (JsonObject session : sessions) {
        series.addSession(values(session, spec));
      }
      if (!series.isEmpty()) {
        metrics.add(spec.name(), series.toJson());
        summaryMetrics.add(metricLine(spec.name(), series));
      }
    }
    report.add("metrics", metrics);
    report.add("censuses", censuses(sessions));
    JsonObject abComparison = AbComparison.of(sessions, SCENES);
    report.add("abComparison", abComparison);
    JsonObject candidateCosts = CandidateCosts.of(sessions, roundScenarios());
    report.add("candidateCosts", candidateCosts);
    JsonObject jfrShares = jfrShares(sessionDirectories);
    report.add("jfrInclusiveShares", jfrShares);

    JsonObject hotMethods = new JsonObject();
    for (Scenario scenario : SCENARIOS) {
      HotMethods methods = new HotMethods();
      for (Path directory : sessionDirectories) {
        Path recording = directory.resolve(scenario.jfrStem() + ".jfr");
        if (Files.exists(recording)) {
          methods.add(recording);
        }
      }
      if (!methods.isEmpty()) {
        hotMethods.add(scenario.jfrStem(), methods.toJson());
      }
    }
    report.add("hotMethods", hotMethods);

    Files.writeString(
        profileDirectory.resolve("report.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(report),
        StandardCharsets.UTF_8);
    Files.writeString(
        profileDirectory.resolve("summary.txt"),
        summary(report, summaryMetrics, abComparison, candidateCosts, jfrShares, hotMethods),
        StandardCharsets.UTF_8);
    System.out.println("Benchmark report written to " + profileDirectory);
  }

  /**
   * Inclusive category shares of the startup recordings (cut into each session's startup phases)
   * and of the chunk-generation recordings.
   */
  private static JsonObject jfrShares(List<Path> sessionDirectories) throws IOException {
    InclusiveShares startup = new InclusiveShares(InclusiveShares.STARTUP_CATEGORIES);
    InclusiveShares worldgen = new InclusiveShares(InclusiveShares.WORLDGEN_CATEGORIES);
    for (Path directory : sessionDirectories) {
      Path sessionFile = directory.resolve(SESSION_FILE);
      Path startupRecording = directory.resolve(STARTUP_JFR + ".jfr");
      if (Files.exists(sessionFile) && Files.exists(startupRecording)) {
        JsonObject session =
            JsonParser.parseString(Files.readString(sessionFile, StandardCharsets.UTF_8))
                .getAsJsonObject();
        startup.add(startupRecording, CandidateCosts.startupWindows(session));
      }
      Path worldgenRecording = directory.resolve(CHUNK_GENERATION_JFR + ".jfr");
      if (Files.exists(worldgenRecording)) {
        worldgen.add(worldgenRecording, List.of());
      }
    }
    JsonObject json = new JsonObject();
    json.add(STARTUP_JFR, startup.toJson());
    json.add(CHUNK_GENERATION_JFR, worldgen.toJson());
    return json;
  }

  private static void appendShares(StringBuilder text, JsonObject shares) {
    text.append("\n== JFR inclusive shares (percent of the thread group's samples whose stack")
        .append(" contains the category; categories overlap)\n");
    for (String recording : shares.keySet()) {
      JsonObject windows = shares.getAsJsonObject(recording);
      for (String window : windows.keySet()) {
        JsonObject data = windows.getAsJsonObject(window);
        text.append(recording)
            .append(" / ")
            .append(window)
            .append(": ")
            .append(data.get("samples"))
            .append(" samples, truncated ")
            .append(format(data.get("truncatedPercent").getAsDouble()))
            .append("%, threads ")
            .append(data.get("threadSamples"))
            .append('\n');
        JsonObject categories = data.getAsJsonObject("percentOfThreadSamples");
        for (String category : categories.keySet()) {
          text.append("  ").append(category).append(": ");
          JsonObject byThread = categories.getAsJsonObject(category);
          for (String thread : byThread.keySet()) {
            text.append(thread)
                .append(' ')
                .append(format(byThread.get(thread).getAsDouble()))
                .append("% ");
          }
          text.append('\n');
        }
      }
    }
  }

  private static List<Path> sessionDirectories(Path sessionsDirectory) throws IOException {
    if (!Files.isDirectory(sessionsDirectory)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(sessionsDirectory)) {
      return entries
          .filter(Files::isDirectory)
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .toList();
    }
  }

  private static JsonArray sessionStatuses(List<JsonObject> sessions) {
    JsonArray statuses = new JsonArray();
    for (JsonObject session : sessions) {
      JsonObject status = new JsonObject();
      status.add("session", session.get("session"));
      status.add("status", session.get("status"));
      if (session.has("error")) {
        status.add("error", session.get("error"));
        System.err.println(
            "Benchmark session " + session.get("session") + " failed: " + session.get("error"));
      }
      statuses.add(status);
    }
    return statuses;
  }

  private static JsonObject censuses(List<JsonObject> sessions) {
    JsonObject censuses = new JsonObject();
    for (String scenario : SCENES) {
      JsonArray perSession = new JsonArray();
      for (JsonObject session : sessions) {
        if (session.has(scenario)) {
          JsonObject data = session.getAsJsonObject(scenario);
          JsonObject census = new JsonObject();
          census.add("before", data.get("censusBefore"));
          census.add("after", data.get("censusAfter"));
          census.add("client", data.get("clientCensus"));
          census.add("renderDistance", data.get("renderDistance"));
          perSession.add(census);
        }
      }
      censuses.add(scenario, perSession);
    }
    return censuses;
  }

  private static List<Double> values(JsonObject session, MetricSpec spec) {
    List<Double> values = new ArrayList<>();
    if (!session.has(spec.scenario())) {
      return values;
    }
    JsonObject scenario = session.getAsJsonObject(spec.scenario());
    if (spec.array() == null) {
      addValue(values, at(scenario, spec.path()));
      return values;
    }
    if (scenario.has(spec.array())) {
      for (JsonElement element : scenario.getAsJsonArray(spec.array())) {
        addValue(values, spec.path().isEmpty() ? element : at(element, spec.path()));
      }
    }
    return values;
  }

  private static void addValue(List<Double> values, JsonElement element) {
    if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
      double value = element.getAsDouble();
      if (value >= 0) {
        values.add(value);
      }
    }
  }

  static JsonElement at(JsonElement root, String path) {
    JsonElement current = root;
    for (String key : path.split("\\.")) {
      if (current == null || !current.isJsonObject() || !current.getAsJsonObject().has(key)) {
        return null;
      }
      current = current.getAsJsonObject().get(key);
    }
    return current;
  }

  private static String metricLine(String name, MetricSeries series) {
    StringBuilder sessionsText = new StringBuilder();
    for (double median : series.sessionMedians()) {
      sessionsText.append(sessionsText.isEmpty() ? "" : " ").append(format(median));
    }
    return String.format(
        Locale.ROOT,
        "%-48s median %10s | session medians [%s] spread %5.1f%% %s",
        name,
        format(series.median()),
        sessionsText,
        series.sessionSpreadPercent(),
        series.repeatable() ? "ok" : "NOT REPEATABLE");
  }

  private static String summary(
      JsonObject report,
      List<String> metricLines,
      JsonObject abComparison,
      JsonObject candidateCosts,
      JsonObject jfrShares,
      JsonObject hot) {
    StringBuilder text = new StringBuilder();
    text.append("Relativity reference benchmark, profile ")
        .append(report.get("profile").getAsString())
        .append('\n');
    text.append("Sessions: ").append(report.get("sessions")).append('\n');
    if (report.has("environment")) {
      JsonObject environment = report.getAsJsonObject("environment");
      text.append("JVM: ").append(environment.getAsJsonObject("jvm").get("version")).append(", ");
      text.append("CPU: ").append(environment.getAsJsonObject("cpu").get("name")).append(", ");
      text.append("GPU: ").append(environment.getAsJsonObject("gpu").get("name")).append(", ");
      text.append("mods: ").append(environment.getAsJsonArray("mods").size()).append('\n');
    }
    text.append("Censuses: ").append(report.get("censuses")).append('\n');
    text.append("Thread CPU clock per session: ").append(report.get("cpuClock")).append("\n\n");
    text.append("== Metrics (median of all rounds/exits/sessions; repeatability = spread of ")
        .append("per-session medians <= ")
        .append(format(MetricSeries.REPEATABILITY_LIMIT_PERCENT))
        .append("%)\n");
    metricLines.forEach(line -> text.append(line).append('\n'));
    AbComparison.appendSummary(text, abComparison);
    CandidateCosts.appendSummary(text, candidateCosts);
    appendShares(text, jfrShares);
    for (String scenario : hot.keySet()) {
      JsonObject methods = hot.getAsJsonObject(scenario);
      text.append("\n== Hot methods: ")
          .append(scenario)
          .append(" (")
          .append(methods.get("javaSamples"))
          .append(" Java samples, ")
          .append(methods.get("nativeSamples"))
          .append(" native samples, ")
          .append(methods.get("recordings"))
          .append(" recordings)\n");
      appendRows(
          text, "Threads (Java samples)", methods.getAsJsonArray("threads"), SUMMARY_THREAD_ROWS);
      appendRows(
          text,
          "Packages paying (first game frame)",
          methods.getAsJsonArray("gameFramePackages"),
          SUMMARY_PACKAGE_ROWS);
      appendRows(text, "Top game frames", methods.getAsJsonArray("topGameFrames"), SUMMARY_ROWS);
      appendRows(text, "Top self methods", methods.getAsJsonArray("topSelfMethods"), SUMMARY_ROWS);
      JsonObject byThread = methods.getAsJsonObject("topGameFramesByThread");
      for (String thread : byThread.keySet()) {
        appendRows(
            text,
            "Game frames on " + thread,
            byThread.getAsJsonArray(thread),
            SUMMARY_ROWS_PER_THREAD);
      }
      appendRows(
          text,
          "Native samples by thread",
          methods.getAsJsonArray("nativeThreads"),
          SUMMARY_THREAD_ROWS);
    }
    return text.toString();
  }

  private static void appendRows(StringBuilder text, String title, JsonArray rows, int limit) {
    text.append("  ").append(title).append(":\n");
    int count = 0;
    for (JsonElement element : rows) {
      if (count++ >= limit) {
        break;
      }
      JsonObject row = element.getAsJsonObject();
      text.append(
          String.format(
              Locale.ROOT,
              "    %5.1f%%  %s%n",
              row.get("percent").getAsDouble(),
              row.get("name").getAsString()));
    }
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }
}

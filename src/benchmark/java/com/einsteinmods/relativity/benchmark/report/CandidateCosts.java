package com.einsteinmods.relativity.benchmark.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dev-only: the candidates' inclusive cost per scene from the rounds' {@code candidates} probes:
 * milliseconds per frame (render) or tick (server) without the empty-probe bias, and percent of the
 * round's mean render-work CPU or server-tick CPU; the probes' overhead in the same units; the
 * chunk-generation stages' CPU per chunk and per second with all threads' CPU per second; and the
 * startup phases cut at the session's wall-clock marks. Repeatability here is the spread of the
 * per-session medians against {@value #REPEATABILITY_LIMIT_PERCENT}%, judged on the share of the
 * frame or tick (sessions on a shared machine differ in raw speed; both spreads are reported).
 */
final class CandidateCosts {
  static final double REPEATABILITY_LIMIT_PERCENT = 20.0;
  private static final double PERCENT = 100.0;
  private static final List<String> PROBE_THREADS = List.of("render", "server");
  private static final Map<String, String> UNIT_CPU =
      Map.of("render", "renderWorkCpu.meanMs", "server", "serverTickCpu.meanMs");
  private static final String OVERHEAD = "probeOverhead";
  private static final String WORLDGEN_SCENARIO = "chunkGeneration";

  /** Startup phases: name, mark it starts at, mark it ends at (epoch milliseconds). */
  private record Phase(String name, String from, String to) {}

  static final String JVM_START = "jvmStart";
  static final String WINDOW_READY = "load_time_pre_window_ms.end";
  static final String RELOAD_BEGIN = "load_time_loading_overlay_ms.begin";
  static final String RELOAD_END = "load_time_loading_overlay_ms.end";
  static final String TITLE_SCREEN = "titleScreen";
  static final String WORLD_CREATION = "worldCreation";
  static final String BOOTSTRAP_MS = "bootstrapMs";
  static final String WORLD_JOINED = "worldJoined";

  static final List<Phase> STARTUP_PHASES =
      List.of(
          new Phase("jvmToWindow", JVM_START, WINDOW_READY),
          new Phase("windowToResourceReload", WINDOW_READY, RELOAD_BEGIN),
          new Phase("resourceReload", RELOAD_BEGIN, RELOAD_END),
          new Phase("reloadToTitleScreen", RELOAD_END, TITLE_SCREEN),
          new Phase("createAndJoinWorld", WORLD_CREATION, WORLD_JOINED));

  private CandidateCosts() {}

  static JsonObject of(List<JsonObject> sessions, List<String> roundScenarios) {
    JsonObject json = new JsonObject();
    JsonObject scenes = new JsonObject();
    for (String scenario : roundScenarios) {
      JsonObject scene = new JsonObject();
      for (String thread : PROBE_THREADS) {
        JsonObject probes = threadCosts(sessions, scenario, thread);
        if (!probes.isEmpty()) {
          scene.add(thread, probes);
        }
      }
      JsonObject randomTicks = randomTicksAb(sessions, scenario);
      if (!randomTicks.isEmpty()) {
        scene.add("randomTicksAb", randomTicks);
      }
      if (scenario.equals(WORLDGEN_SCENARIO)) {
        scene.add("worldgen", worldgenCosts(sessions, scenario));
      }
      scenes.add(scenario, scene);
    }
    json.add("scenes", scenes);
    json.add("startupPhasesMs", startupPhases(sessions));
    return json;
  }

  private static JsonObject threadCosts(List<JsonObject> sessions, String scenario, String thread) {
    Map<String, Series> ms = new LinkedHashMap<>();
    Map<String, Series> percent = new LinkedHashMap<>();
    Map<String, Series> calls = new LinkedHashMap<>();
    Series unit = new Series();
    for (int index = 0; index < sessions.size(); index++) {
      JsonObject session = sessions.get(index);
      for (JsonObject round : rounds(session, scenario)) {
        JsonElement probes = BenchmarkReport.at(round, "candidates." + thread);
        JsonElement unitCpu = BenchmarkReport.at(round, UNIT_CPU.get(thread));
        if (probes == null || !probes.isJsonObject() || unitCpu == null) {
          continue;
        }
        double unitMs = unitCpu.getAsDouble();
        unit.add(index, unitMs);
        for (Map.Entry<String, JsonElement> probe : probes.getAsJsonObject().entrySet()) {
          JsonObject values = probe.getValue().getAsJsonObject();
          if (probe.getKey().equals(OVERHEAD)) {
            percent
                .computeIfAbsent(OVERHEAD, key -> new Series())
                .add(index, values.get("percentOfUnit").getAsDouble());
            ms.computeIfAbsent(OVERHEAD, key -> new Series())
                .add(index, values.get("msPerUnit").getAsDouble());
            continue;
          }
          double value = values.get("msPerUnit").getAsDouble();
          ms.computeIfAbsent(probe.getKey(), key -> new Series()).add(index, value);
          percent
              .computeIfAbsent(probe.getKey(), key -> new Series())
              .add(index, unitMs > 0 ? value / unitMs * PERCENT : 0);
          calls
              .computeIfAbsent(probe.getKey(), key -> new Series())
              .add(index, values.get("callsPerUnit").getAsDouble());
        }
      }
    }
    JsonObject json = new JsonObject();
    if (unit.isEmpty()) {
      return json;
    }
    json.add("unitCpuMs", unit.toJson());
    for (String probe : ms.keySet()) {
      JsonObject row = new JsonObject();
      row.add("ms", ms.get(probe).toJson());
      row.add("percent", percent.get(probe).toJson());
      if (calls.containsKey(probe)) {
        row.add("calls", calls.get(probe).toJson());
      }
      json.add(probe, row);
    }
    return json;
  }

  /**
   * Random ticks off vs on (experiment rounds after the measured ones): mean server-tick CPU and
   * the chunk-tick probe in each state; the saving is the direct ceiling of random-tick work.
   */
  private static JsonObject randomTicksAb(List<JsonObject> sessions, String scenario) {
    Series offTick = new Series();
    Series onTick = new Series();
    Series offChunk = new Series();
    Series onChunk = new Series();
    for (int index = 0; index < sessions.size(); index++) {
      JsonElement array = BenchmarkReport.at(sessions.get(index), scenario + ".randomTicksAb");
      if (array == null || !array.isJsonArray()) {
        continue;
      }
      for (JsonElement element : array.getAsJsonArray()) {
        JsonObject round = element.getAsJsonObject();
        boolean enabled = round.get("randomTicksEnabled").getAsBoolean();
        double tick = BenchmarkReport.at(round, UNIT_CPU.get("server")).getAsDouble();
        JsonElement chunk = BenchmarkReport.at(round, "candidates.server.CHUNK_TICK.msPerUnit");
        (enabled ? onTick : offTick).add(index, tick);
        if (chunk != null) {
          (enabled ? onChunk : offChunk).add(index, chunk.getAsDouble());
        }
      }
    }
    JsonObject json = new JsonObject();
    if (offTick.isEmpty() || onTick.isEmpty()) {
      return json;
    }
    json.add("serverTickCpuMsOff", offTick.toJson());
    json.add("serverTickCpuMsOn", onTick.toJson());
    if (!offChunk.isEmpty() && !onChunk.isEmpty()) {
      json.add("chunkTickMsOff", offChunk.toJson());
      json.add("chunkTickMsOn", onChunk.toJson());
    }
    double off = offTick.median();
    double on = onTick.median();
    json.addProperty("savingMs", on - off);
    json.addProperty("savingPercentOfTick", on > 0 ? (on - off) / on * PERCENT : 0);
    return json;
  }

  private static JsonObject worldgenCosts(List<JsonObject> sessions, String scenario) {
    Map<String, Series> perCall = new LinkedHashMap<>();
    Map<String, Series> perSecond = new LinkedHashMap<>();
    Map<String, Series> threads = new LinkedHashMap<>();
    for (int index = 0; index < sessions.size(); index++) {
      JsonObject session = sessions.get(index);
      for (JsonObject round : rounds(session, scenario)) {
        JsonElement stages = BenchmarkReport.at(round, "candidates.worldgen");
        JsonElement cpu = BenchmarkReport.at(round, "candidates.threadCpuMsPerSecond");
        if (stages != null && stages.isJsonObject()) {
          for (Map.Entry<String, JsonElement> stage : stages.getAsJsonObject().entrySet()) {
            JsonObject values = stage.getValue().getAsJsonObject();
            perCall
                .computeIfAbsent(stage.getKey(), key -> new Series())
                .add(index, values.get("cpuMsPerCall").getAsDouble());
            perSecond
                .computeIfAbsent(stage.getKey(), key -> new Series())
                .add(index, values.get("cpuMsPerSecond").getAsDouble());
          }
        }
        if (cpu != null && cpu.isJsonObject()) {
          for (Map.Entry<String, JsonElement> group : cpu.getAsJsonObject().entrySet()) {
            threads
                .computeIfAbsent(group.getKey(), key -> new Series())
                .add(index, group.getValue().getAsDouble());
          }
        }
      }
    }
    JsonObject json = new JsonObject();
    JsonObject stages = new JsonObject();
    for (String stage : perCall.keySet()) {
      JsonObject row = new JsonObject();
      row.add("cpuMsPerChunk", perCall.get(stage).toJson());
      row.add("cpuMsPerSecond", perSecond.get(stage).toJson());
      stages.add(stage, row);
    }
    json.add("stages", stages);
    JsonObject threadCpu = new JsonObject();
    threads.forEach((group, series) -> threadCpu.add(group, series.toJson()));
    json.add("threadCpuMsPerSecond", threadCpu);
    return json;
  }

  private static JsonObject startupPhases(List<JsonObject> sessions) {
    JsonObject json = new JsonObject();
    for (Phase phase : STARTUP_PHASES) {
      Series series = new Series();
      for (int index = 0; index < sessions.size(); index++) {
        JsonObject session = sessions.get(index);
        JsonElement from = mark(session, phase.from());
        JsonElement to = mark(session, phase.to());
        if (from != null && to != null) {
          series.add(index, to.getAsDouble() - from.getAsDouble());
        }
      }
      if (!series.isEmpty()) {
        json.add(phase.name(), series.toJson());
      }
    }
    Series bootstrap = new Series();
    for (int index = 0; index < sessions.size(); index++) {
      JsonObject session = sessions.get(index);
      JsonElement value = mark(session, BOOTSTRAP_MS);
      if (value != null) {
        bootstrap.add(index, value.getAsDouble());
      }
    }
    if (!bootstrap.isEmpty()) {
      json.add("registryBootstrapInsideJvmToWindow", bootstrap.toJson());
    }
    return json;
  }

  /** The session's startup phases as JFR windows; phases with a missing mark are left out. */
  static List<InclusiveShares.Window> startupWindows(JsonObject session) {
    List<InclusiveShares.Window> windows = new ArrayList<>();
    for (Phase phase : STARTUP_PHASES) {
      JsonElement from = mark(session, phase.from());
      JsonElement to = mark(session, phase.to());
      if (from != null && to != null) {
        windows.add(new InclusiveShares.Window(phase.name(), from.getAsLong(), to.getAsLong()));
      }
    }
    return windows;
  }

  /** A startup mark by name; mark names contain dots, so no dotted path lookup. */
  private static JsonElement mark(JsonObject session, String name) {
    JsonElement marks = BenchmarkReport.at(session, "startup.phaseMarks");
    return marks != null && marks.isJsonObject() ? marks.getAsJsonObject().get(name) : null;
  }

  private static List<JsonObject> rounds(JsonObject session, String scenario) {
    List<JsonObject> rounds = new ArrayList<>();
    JsonElement array = BenchmarkReport.at(session, scenario + ".rounds");
    if (array != null && array.isJsonArray()) {
      array.getAsJsonArray().forEach(element -> rounds.add(element.getAsJsonObject()));
    }
    return rounds;
  }

  static void appendSummary(StringBuilder text, JsonObject costs) {
    text.append("\n== Candidate costs (median of rounds; ms per frame or tick without probe bias;")
        .append(" % of mean render-work CPU or server-tick CPU; session spread <= ")
        .append(String.format(Locale.ROOT, "%.0f", REPEATABILITY_LIMIT_PERCENT))
        .append("%)\n");
    JsonObject scenes = costs.getAsJsonObject("scenes");
    for (String scene : scenes.keySet()) {
      JsonObject threads = scenes.getAsJsonObject(scene);
      for (String thread : PROBE_THREADS) {
        if (!threads.has(thread)) {
          continue;
        }
        JsonObject probes = threads.getAsJsonObject(thread);
        text.append(
            String.format(
                Locale.ROOT,
                "%s %s (unit CPU %.3f ms)%n",
                scene,
                thread,
                probes.getAsJsonObject("unitCpuMs").get("median").getAsDouble()));
        for (String probe : probes.keySet()) {
          if (probe.equals("unitCpuMs")) {
            continue;
          }
          JsonObject row = probes.getAsJsonObject(probe);
          JsonObject ms = row.getAsJsonObject("ms");
          JsonObject percent = row.getAsJsonObject("percent");
          text.append(
              String.format(
                  Locale.ROOT,
                  "  %-26s %9.4f ms %6.2f%% calls %9.1f | ms spread %5.1f%% | %% of unit"
                      + " sessions %s spread %5.1f%% %s%n",
                  probe,
                  ms.get("median").getAsDouble(),
                  percent.get("median").getAsDouble(),
                  row.has("calls") ? row.getAsJsonObject("calls").get("median").getAsDouble() : 0,
                  ms.get("sessionSpreadPercent").getAsDouble(),
                  percent.get("sessionMedians"),
                  percent.get("sessionSpreadPercent").getAsDouble(),
                  percent.get("repeatable").getAsBoolean() ? "ok" : "NOT REPEATABLE"));
        }
      }
      if (threads.has("randomTicksAb")) {
        JsonObject ab = threads.getAsJsonObject("randomTicksAb");
        text.append(
            String.format(
                Locale.ROOT,
                "%s random ticks off/on: server tick CPU %.3f / %.3f ms, saving %.3f ms (%.1f%%)%n",
                scene,
                ab.getAsJsonObject("serverTickCpuMsOff").get("median").getAsDouble(),
                ab.getAsJsonObject("serverTickCpuMsOn").get("median").getAsDouble(),
                ab.get("savingMs").getAsDouble(),
                ab.get("savingPercentOfTick").getAsDouble()));
      }
      if (threads.has("worldgen")) {
        JsonObject worldgen = threads.getAsJsonObject("worldgen");
        JsonObject stages = worldgen.getAsJsonObject("stages");
        text.append(scene).append(" worldgen stages (thread CPU)\n");
        for (String stage : stages.keySet()) {
          JsonObject row = stages.getAsJsonObject(stage);
          text.append(
              String.format(
                  Locale.ROOT,
                  "  %-16s %8.3f ms/chunk %9.1f ms/s | sessions %s%n",
                  stage,
                  row.getAsJsonObject("cpuMsPerChunk").get("median").getAsDouble(),
                  row.getAsJsonObject("cpuMsPerSecond").get("median").getAsDouble(),
                  row.getAsJsonObject("cpuMsPerSecond").get("sessionMedians")));
        }
        text.append(scene).append(" thread CPU ms per second\n");
        JsonObject threadCpu = worldgen.getAsJsonObject("threadCpuMsPerSecond");
        for (String group : threadCpu.keySet()) {
          text.append(
              String.format(
                  Locale.ROOT,
                  "  %-40s %8.1f%n",
                  group,
                  threadCpu.getAsJsonObject(group).get("median").getAsDouble()));
        }
      }
    }
    JsonObject phases = costs.getAsJsonObject("startupPhasesMs");
    text.append("Startup phases (wall ms)\n");
    for (String phase : phases.keySet()) {
      JsonObject series = phases.getAsJsonObject(phase);
      text.append(
          String.format(
              Locale.ROOT,
              "  %-36s %9.0f | sessions %s%n",
              phase,
              series.get("median").getAsDouble(),
              series.get("sessionMedians")));
    }
  }

  /** Values per session (in session order), for medians and the session spread. */
  private static final class Series {
    private final Map<Integer, List<Double>> bySession = new LinkedHashMap<>();

    void add(int session, double value) {
      bySession.computeIfAbsent(session, key -> new ArrayList<>()).add(value);
    }

    boolean isEmpty() {
      return bySession.isEmpty();
    }

    double median() {
      List<Double> all = new ArrayList<>();
      bySession.values().forEach(all::addAll);
      return MetricSeries.median(all);
    }

    JsonObject toJson() {
      List<Double> all = new ArrayList<>();
      List<Double> medians = new ArrayList<>();
      for (List<Double> values : bySession.values()) {
        all.addAll(values);
        medians.add(MetricSeries.median(values));
      }
      double min = Collections.min(medians);
      double max = Collections.max(medians);
      double spread = min > 0 ? (max - min) / min * PERCENT : 0;
      JsonObject json = new JsonObject();
      json.addProperty("median", MetricSeries.median(all));
      JsonArray sessionMedians = new JsonArray();
      medians.forEach(sessionMedians::add);
      json.add("sessionMedians", sessionMedians);
      json.addProperty("sessionSpreadPercent", spread);
      json.addProperty("repeatable", spread <= REPEATABILITY_LIMIT_PERCENT);
      return json;
    }
  }
}

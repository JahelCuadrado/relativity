package com.einsteinmods.relativity.benchmark.report;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dev-only: puts the candidate costs of the {@code pack} and {@code vanilla} reports side by side
 * in {@code candidates.txt}: per scene and candidate, milliseconds per frame or tick and percent of
 * the frame's or tick's CPU, with each profile's session spread; then the chunk-generation stages
 * and the startup phases. Run after both profiles' {@code runBenchmark}.
 */
public final class CandidateComparison {
  private static final List<String> PROFILES = List.of("pack", "vanilla");
  private static final String REPORT = "report.json";
  private static final String OUTPUT = "candidates.txt";
  private static final String MISSING = "-";

  private CandidateComparison() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 1) {
      throw new IllegalArgumentException("Usage: CandidateComparison <build/benchmark directory>");
    }
    Path root = Path.of(arguments[0]);
    JsonObject pack = costs(root.resolve(PROFILES.get(0)).resolve(REPORT));
    JsonObject vanilla = costs(root.resolve(PROFILES.get(1)).resolve(REPORT));
    StringBuilder text = new StringBuilder();
    text.append("Candidate ceilings: inclusive cost, pack vs vanilla (median of rounds;")
        .append(" spread = per-session medians of the % of frame/tick, ok <= ")
        .append(String.format(Locale.ROOT, "%.0f", CandidateCosts.REPEATABILITY_LIMIT_PERCENT))
        .append("%)\n\n");
    text.append(
        String.format(
            Locale.ROOT,
            "%-14s %-6s %-26s | %9s %7s %7s | %9s %7s %7s%n",
            "scene",
            "thread",
            "candidate",
            "pack ms",
            "pack %",
            "spread",
            "van ms",
            "van %",
            "spread"));
    JsonObject packScenes = object(pack, "scenes");
    JsonObject vanillaScenes = object(vanilla, "scenes");
    for (String scene : union(packScenes, vanillaScenes)) {
      for (String thread : List.of("render", "server")) {
        JsonObject packProbes = object(object(packScenes, scene), thread);
        JsonObject vanillaProbes = object(object(vanillaScenes, scene), thread);
        for (String probe : union(packProbes, vanillaProbes)) {
          text.append(
              String.format(
                  Locale.ROOT,
                  "%-14s %-6s %-26s | %s | %s%n",
                  scene,
                  thread,
                  probe,
                  cells(object(packProbes, probe)),
                  cells(object(vanillaProbes, probe))));
        }
      }
    }
    text.append("\nRandom ticks off vs on (experiment): server tick CPU saving\n");
    for (String scene : union(packScenes, vanillaScenes)) {
      JsonObject packAb = object(object(packScenes, scene), "randomTicksAb");
      JsonObject vanillaAb = object(object(vanillaScenes, scene), "randomTicksAb");
      if (packAb.has("savingMs") || vanillaAb.has("savingMs")) {
        text.append(
            String.format(
                Locale.ROOT,
                "  %-14s pack %s | vanilla %s%n",
                scene,
                saving(packAb),
                saving(vanillaAb)));
      }
    }
    text.append("\nChunk generation stages (thread CPU ms per chunk | ms per second of flight)\n");
    JsonObject packStages =
        object(object(object(packScenes, "chunkGeneration"), "worldgen"), "stages");
    JsonObject vanillaStages =
        object(object(object(vanillaScenes, "chunkGeneration"), "worldgen"), "stages");
    for (String stage : union(packStages, vanillaStages)) {
      text.append(
          String.format(
              Locale.ROOT,
              "  %-16s pack %s | vanilla %s%n",
              stage,
              stageCells(object(packStages, stage)),
              stageCells(object(vanillaStages, stage))));
    }
    text.append("\nStartup phases (wall ms, median of sessions)\n");
    JsonObject packPhases = object(pack, "startupPhasesMs");
    JsonObject vanillaPhases = object(vanilla, "startupPhasesMs");
    for (String phase : union(packPhases, vanillaPhases)) {
      text.append(
          String.format(
              Locale.ROOT,
              "  %-36s pack %8s | vanilla %8s%n",
              phase,
              median(object(packPhases, phase), "%.0f"),
              median(object(vanillaPhases, phase), "%.0f")));
    }
    Files.writeString(root.resolve(OUTPUT), text.toString(), StandardCharsets.UTF_8);
    System.out.println("Candidate comparison written to " + root.resolve(OUTPUT));
  }

  private static JsonObject costs(Path report) throws IOException {
    if (!Files.exists(report)) {
      return new JsonObject();
    }
    JsonObject json =
        JsonParser.parseString(Files.readString(report, StandardCharsets.UTF_8)).getAsJsonObject();
    return object(json, "candidateCosts");
  }

  private static String cells(JsonObject row) {
    if (row.has("median")) {
      return String.format(
          Locale.ROOT,
          "%9.4f %7s %6.1f%%",
          row.get("median").getAsDouble(),
          "(unit)",
          row.get("sessionSpreadPercent").getAsDouble());
    }
    if (!row.has("ms")) {
      return String.format(Locale.ROOT, "%9s %7s %7s", MISSING, MISSING, MISSING);
    }
    JsonObject ms = row.getAsJsonObject("ms");
    return String.format(
        Locale.ROOT,
        "%9.4f %6.2f%% %6.1f%%",
        ms.get("median").getAsDouble(),
        row.getAsJsonObject("percent").get("median").getAsDouble(),
        row.getAsJsonObject("percent").get("sessionSpreadPercent").getAsDouble());
  }

  private static String saving(JsonObject ab) {
    return ab.has("savingMs")
        ? String.format(
            Locale.ROOT,
            "%.3f ms (%.1f%%)",
            ab.get("savingMs").getAsDouble(),
            ab.get("savingPercentOfTick").getAsDouble())
        : MISSING;
  }

  private static String stageCells(JsonObject row) {
    return median(object(row, "cpuMsPerChunk"), "%.3f")
        + " ms | "
        + median(object(row, "cpuMsPerSecond"), "%.1f")
        + " ms/s";
  }

  private static String median(JsonObject series, String format) {
    return series.has("median")
        ? String.format(Locale.ROOT, format, series.get("median").getAsDouble())
        : MISSING;
  }

  private static JsonObject object(JsonObject parent, String key) {
    JsonElement child = parent.get(key);
    return child != null && child.isJsonObject() ? child.getAsJsonObject() : new JsonObject();
  }

  private static Set<String> union(JsonObject first, JsonObject second) {
    Set<String> keys = new LinkedHashSet<>(first.keySet());
    keys.addAll(second.keySet());
    return keys;
  }
}

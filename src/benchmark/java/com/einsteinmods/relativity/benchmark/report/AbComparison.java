package com.einsteinmods.relativity.benchmark.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A/B comparison of a toggle within sessions ({@code -Ptoggle}): scene rounds alternate off (A) and
 * on (B), so each A round and the B round right after it form a pair measured under the same
 * conditions. Per scene and metric: median of all A rounds, median of all B rounds, their relative
 * difference, and the spread of the per-pair differences (how much one pair can disagree).
 */
final class AbComparison {
  private static final String TOGGLE_ENABLED = "toggleEnabled";
  private static final double PERCENT = 100.0;
  private static final List<String> METRICS =
      List.of("renderWorkCpu.p50Ms", "renderWorkCpu.p95Ms", "serverTickCpu.p50Ms");

  private AbComparison() {}

  /** Empty when no session alternated a toggle. */
  static JsonObject of(List<JsonObject> sessions, List<String> scenes) {
    JsonObject comparison = new JsonObject();
    String toggle = null;
    for (JsonObject session : sessions) {
      if (session.has("toggle")) {
        toggle = session.get("toggle").getAsString();
      }
    }
    if (toggle == null) {
      return comparison;
    }
    comparison.addProperty("toggle", toggle);
    comparison.addProperty("a", "off");
    comparison.addProperty("b", "on");
    JsonObject byScene = new JsonObject();
    for (String scene : scenes) {
      JsonObject metrics = new JsonObject();
      for (String metric : METRICS) {
        compare(sessions, scene, metric).ifPresent(result -> metrics.add(metric, result));
      }
      if (!metrics.isEmpty()) {
        byScene.add(scene, metrics);
      }
    }
    comparison.add("scenes", byScene);
    return comparison;
  }

  private static Optional<JsonObject> compare(
      List<JsonObject> sessions, String scene, String metric) {
    List<Double> offValues = new ArrayList<>();
    List<Double> onValues = new ArrayList<>();
    List<Double> pairDifferences = new ArrayList<>();
    for (JsonObject session : sessions) {
      if (!session.has(scene) || !session.getAsJsonObject(scene).has("rounds")) {
        continue;
      }
      Double pendingOff = null;
      for (JsonElement element : session.getAsJsonObject(scene).getAsJsonArray("rounds")) {
        JsonObject round = element.getAsJsonObject();
        JsonElement value = BenchmarkReport.at(round, metric);
        if (!round.has(TOGGLE_ENABLED) || value == null || !value.isJsonPrimitive()) {
          pendingOff = null;
          continue;
        }
        double number = value.getAsDouble();
        if (round.get(TOGGLE_ENABLED).getAsBoolean()) {
          onValues.add(number);
          if (pendingOff != null && pendingOff > 0) {
            pairDifferences.add(relativePercent(pendingOff, number));
          }
          pendingOff = null;
        } else {
          offValues.add(number);
          pendingOff = number;
        }
      }
    }
    if (offValues.isEmpty() || onValues.isEmpty()) {
      return Optional.empty();
    }
    double medianOff = MetricSeries.median(offValues);
    double medianOn = MetricSeries.median(onValues);
    JsonObject result = new JsonObject();
    result.addProperty("medianA", medianOff);
    result.addProperty("medianB", medianOn);
    result.addProperty(
        "differencePercent", medianOff > 0 ? relativePercent(medianOff, medianOn) : 0);
    JsonArray pairs = new JsonArray();
    pairDifferences.forEach(pairs::add);
    result.add("pairDifferencesPercent", pairs);
    if (!pairDifferences.isEmpty()) {
      result.addProperty("pairDifferenceMedianPercent", MetricSeries.median(pairDifferences));
      result.addProperty("pairDifferenceMinPercent", Collections.min(pairDifferences));
      result.addProperty("pairDifferenceMaxPercent", Collections.max(pairDifferences));
    }
    return Optional.of(result);
  }

  private static double relativePercent(double off, double on) {
    return (on - off) / off * PERCENT;
  }

  static void appendSummary(StringBuilder text, JsonObject comparison) {
    if (!comparison.has("toggle")) {
      return;
    }
    text.append("\n== A/B toggle ")
        .append(comparison.get("toggle").getAsString())
        .append(" (A = off, B = on; difference = (B - A) / A; pairs = A round + next B round)\n");
    JsonObject scenes = comparison.getAsJsonObject("scenes");
    for (String scene : scenes.keySet()) {
      JsonObject metrics = scenes.getAsJsonObject(scene);
      for (String metric : metrics.keySet()) {
        JsonObject result = metrics.getAsJsonObject(metric);
        text.append(
            String.format(
                Locale.ROOT,
                "%-36s A %8.3f | B %8.3f | diff %+6.1f%% | pairs %s%n",
                scene + "." + metric,
                result.get("medianA").getAsDouble(),
                result.get("medianB").getAsDouble(),
                result.get("differencePercent").getAsDouble(),
                pairRange(result)));
      }
    }
  }

  private static String pairRange(JsonObject result) {
    if (!result.has("pairDifferenceMinPercent")) {
      return "none";
    }
    return String.format(
        Locale.ROOT,
        "%d, median %+.1f%% [%+.1f%% .. %+.1f%%]",
        result.getAsJsonArray("pairDifferencesPercent").size(),
        result.get("pairDifferenceMedianPercent").getAsDouble(),
        result.get("pairDifferenceMinPercent").getAsDouble(),
        result.get("pairDifferenceMaxPercent").getAsDouble());
  }
}

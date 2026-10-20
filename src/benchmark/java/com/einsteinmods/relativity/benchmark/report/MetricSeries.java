package com.einsteinmods.relativity.benchmark.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One metric across sessions: every value (round, exit or session), pooled and per session. The
 * session spread ({@code (max - min) / min} of the per-session medians) is the repeatability check.
 */
final class MetricSeries {
  static final double REPEATABILITY_LIMIT_PERCENT = 10.0;
  private static final double PERCENT = 100.0;
  private static final double HALF = 0.5;

  private final List<List<Double>> sessions = new ArrayList<>();

  void addSession(List<Double> values) {
    if (!values.isEmpty()) {
      sessions.add(List.copyOf(values));
    }
  }

  boolean isEmpty() {
    return sessions.isEmpty();
  }

  double median() {
    return median(pooled());
  }

  double sessionSpreadPercent() {
    List<Double> medians = sessionMedians();
    double min = Collections.min(medians);
    double max = Collections.max(medians);
    return min == 0 ? 0 : (max - min) / min * PERCENT;
  }

  boolean repeatable() {
    return sessions.size() < 2 || sessionSpreadPercent() <= REPEATABILITY_LIMIT_PERCENT;
  }

  List<Double> sessionMedians() {
    List<Double> medians = new ArrayList<>();
    for (List<Double> session : sessions) {
      medians.add(median(session));
    }
    return medians;
  }

  JsonObject toJson() {
    List<Double> values = pooled();
    double mean = 0;
    for (double value : values) {
      mean += value;
    }
    mean /= values.size();
    double squares = 0;
    for (double value : values) {
      squares += (value - mean) * (value - mean);
    }
    JsonObject json = new JsonObject();
    json.addProperty("median", median(values));
    json.addProperty("mean", mean);
    json.addProperty("sd", Math.sqrt(squares / values.size()));
    json.addProperty("min", Collections.min(values));
    json.addProperty("max", Collections.max(values));
    json.addProperty("samples", values.size());
    JsonArray medians = new JsonArray();
    sessionMedians().forEach(medians::add);
    json.add("sessionMedians", medians);
    json.addProperty("sessionSpreadPercent", sessionSpreadPercent());
    json.addProperty("repeatable", repeatable());
    return json;
  }

  private List<Double> pooled() {
    List<Double> values = new ArrayList<>();
    sessions.forEach(values::addAll);
    return values;
  }

  static double median(List<Double> values) {
    List<Double> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    int middle = sorted.size() / 2;
    return sorted.size() % 2 == 1
        ? sorted.get(middle)
        : (sorted.get(middle - 1) + sorted.get(middle)) * HALF;
  }
}

package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;
import java.util.Arrays;

/** Summary of one round of durations, in milliseconds (nearest-rank percentiles). */
record TimingStats(
    int count,
    double mean,
    double standardDeviation,
    double p50,
    double p95,
    double p99,
    double max) {
  private static final double NANOS_PER_MILLI = 1_000_000.0;
  private static final double P50 = 0.50;
  private static final double P95 = 0.95;
  private static final double P99 = 0.99;

  static TimingStats of(long[] nanos) {
    if (nanos.length == 0) {
      return new TimingStats(0, 0, 0, 0, 0, 0, 0);
    }
    long[] sorted = nanos.clone();
    Arrays.sort(sorted);
    double sum = 0;
    for (long value : sorted) {
      sum += value;
    }
    double mean = sum / sorted.length;
    double squares = 0;
    for (long value : sorted) {
      double delta = value - mean;
      squares += delta * delta;
    }
    return new TimingStats(
        sorted.length,
        mean / NANOS_PER_MILLI,
        Math.sqrt(squares / sorted.length) / NANOS_PER_MILLI,
        percentile(sorted, P50),
        percentile(sorted, P95),
        percentile(sorted, P99),
        sorted[sorted.length - 1] / NANOS_PER_MILLI);
  }

  private static double percentile(long[] sorted, double fraction) {
    int rank = (int) Math.ceil(fraction * sorted.length);
    return sorted[Math.max(0, rank - 1)] / NANOS_PER_MILLI;
  }

  JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("count", count);
    json.addProperty("meanMs", mean);
    json.addProperty("sdMs", standardDeviation);
    json.addProperty("p50Ms", p50);
    json.addProperty("p95Ms", p95);
    json.addProperty("p99Ms", p99);
    json.addProperty("maxMs", max);
    return json;
  }
}

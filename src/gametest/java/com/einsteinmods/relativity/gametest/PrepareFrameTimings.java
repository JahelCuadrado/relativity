package com.einsteinmods.relativity.gametest;

import java.util.Arrays;

/**
 * Dev-only timing of {@code FeatureRenderDispatcher#prepareFrame} (where entity vertices are
 * built), filled by {@code FeatureRenderDispatcherTimingMixin}. Render thread writes, test thread
 * reads between frames.
 */
public final class PrepareFrameTimings {
  private static final int CAPACITY = 4096;
  private static final long[] SAMPLES = new long[CAPACITY];
  private static volatile int count;
  private static long start;

  private PrepareFrameTimings() {}

  public static void begin() {
    start = System.nanoTime();
  }

  public static void end() {
    int index = count;
    if (index < CAPACITY) {
      SAMPLES[index] = System.nanoTime() - start;
      count = index + 1;
    }
  }

  public static void reset() {
    count = 0;
  }

  /** Average, median and 95th percentile in microseconds, plus the sample count. */
  public static String summary() {
    int samples = count;
    if (samples == 0) {
      return "no frames";
    }
    long[] sorted = Arrays.copyOf(SAMPLES, samples);
    Arrays.sort(sorted);
    long total = 0;
    for (long sample : sorted) {
      total += sample;
    }
    double toMicros = 1.0 / 1000.0;
    int p95 = Math.min(samples - 1, (int) Math.ceil(samples * 0.95) - 1);
    return String.format(
        "avg %.1f us, p50 %.1f us, p95 %.1f us over %d frames",
        total * toMicros / samples,
        sorted[samples / 2] * toMicros,
        sorted[p95] * toMicros,
        samples);
  }
}

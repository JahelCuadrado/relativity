package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * CPU time of the calling thread ({@link ThreadMXBean#getCurrentThreadCpuTime}), the benchmark's
 * main metric: unlike wall time it ignores waits for the GPU, the compositor and other processes,
 * so it repeats across sessions on a busy machine. One call reads the thread's CPU counter without
 * allocating; {@link #measureCallNanos()} times it so the report states the probe's own cost.
 */
final class ThreadCpuClock {
  /** Returned by {@link #now()} when the JVM cannot measure thread CPU time. */
  static final long UNSUPPORTED = -1L;

  private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
  private static final boolean SUPPORTED = enable();
  private static final int COST_PROBE_CALLS = 100_000;

  /** Keeps the cost probe's reads observable so the JIT cannot drop them. */
  private static volatile long probeSink;

  private ThreadCpuClock() {}

  private static boolean enable() {
    if (!THREADS.isCurrentThreadCpuTimeSupported()) {
      return false;
    }
    if (!THREADS.isThreadCpuTimeEnabled()) {
      THREADS.setThreadCpuTimeEnabled(true);
    }
    return true;
  }

  static boolean supported() {
    return SUPPORTED;
  }

  /** CPU nanoseconds used so far by the calling thread, or {@link #UNSUPPORTED}. */
  static long now() {
    return SUPPORTED ? THREADS.getCurrentThreadCpuTime() : UNSUPPORTED;
  }

  /** Wall nanoseconds per {@link #now()} call, averaged over a burst on the calling thread. */
  static double measureCallNanos() {
    long checksum = 0;
    long start = System.nanoTime();
    for (int call = 0; call < COST_PROBE_CALLS; call++) {
      checksum += now();
    }
    double nanosPerCall = (System.nanoTime() - start) / (double) COST_PROBE_CALLS;
    probeSink = checksum;
    return nanosPerCall;
  }

  static JsonObject describe() {
    JsonObject clock = new JsonObject();
    clock.addProperty("supported", SUPPORTED);
    clock.addProperty("nanosPerCall", measureCallNanos());
    return clock;
  }
}

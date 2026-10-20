package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Dev-only CPU time of chunk-generation stages on whatever thread runs them (vanilla workers or
 * C2ME's): thread CPU time ({@link ThreadCpuClock}) at entry and exit of each stage method, summed
 * in atomic counters. Stages are per chunk (milliseconds each), so two CPU-clock reads per call are
 * negligible. Always counting; the script reads deltas between rounds.
 */
public final class WorldgenProbes {
  /** A timed stage and the vanilla method that runs it. */
  public enum Stage {
    CREATE_BIOMES("ChunkGenerator#doCreateBiomes"),
    FILL_NOISE("NoiseBasedChunkGenerator#doFill"),
    BUILD_SURFACE("NoiseBasedChunkGenerator#buildSurface"),
    CARVERS("NoiseBasedChunkGenerator#generateCarvers"),
    FEATURES("ChunkGenerator#applyBiomeDecoration");

    private final String target;

    Stage(String target) {
      this.target = target;
    }
  }

  private static final Stage[] STAGES = Stage.values();
  private static final AtomicLongArray CPU_NANOS = new AtomicLongArray(STAGES.length);
  private static final AtomicLongArray CALLS = new AtomicLongArray(STAGES.length);
  private static final double NANOS_PER_MILLI = 1_000_000.0;

  private WorldgenProbes() {}

  /** Thread CPU nanoseconds of the calling thread, to pass back to {@link #end}. */
  public static long begin() {
    return ThreadCpuClock.now();
  }

  public static void end(Stage stage, long beginCpu) {
    long now = ThreadCpuClock.now();
    if (now != ThreadCpuClock.UNSUPPORTED) {
      CPU_NANOS.addAndGet(stage.ordinal(), now - beginCpu);
      CALLS.incrementAndGet(stage.ordinal());
    }
  }

  static Snapshot snapshot() {
    long[] cpu = new long[STAGES.length];
    long[] calls = new long[STAGES.length];
    for (int index = 0; index < STAGES.length; index++) {
      cpu[index] = CPU_NANOS.get(index);
      calls[index] = CALLS.get(index);
    }
    return new Snapshot(cpu, calls);
  }

  /** Counter values at one instant. */
  record Snapshot(long[] cpuNanos, long[] calls) {
    /** Per stage since {@code earlier}: calls, CPU milliseconds per call and per wall second. */
    JsonObject since(Snapshot earlier, double seconds) {
      JsonObject json = new JsonObject();
      for (Stage stage : STAGES) {
        int index = stage.ordinal();
        long calls = this.calls[index] - earlier.calls[index];
        double cpuMs = (cpuNanos[index] - earlier.cpuNanos[index]) / NANOS_PER_MILLI;
        JsonObject row = new JsonObject();
        row.addProperty("target", stage.target);
        row.addProperty("calls", calls);
        row.addProperty("cpuMsPerCall", calls == 0 ? 0 : cpuMs / calls);
        row.addProperty("cpuMsPerSecond", seconds <= 0 ? 0 : cpuMs / seconds);
        json.add(stage.name(), row);
      }
      return json;
    }
  }
}

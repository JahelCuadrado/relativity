package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;

/**
 * Dev-only inclusive timer of the {@link Candidate}s that run on one thread: {@code
 * System.nanoTime} at entry and exit, summed into preallocated arrays (no allocation per call).
 * Owned by that thread: the script starts, stops and calibrates it through {@code Minecraft#submit}
 * or {@code MinecraftServer#submit}. While idle a probe costs one boolean read.
 *
 * <p>Every call is counted; a candidate called thousands of times per frame or tick is timed on one
 * call in {@code 2^sampleShift} only ({@link Candidate#sampleShift()}), and its cost is the mean of
 * the timed calls times all calls, so the timer never costs more than the work it measures. An
 * entry without an exit (a pack mod cancelling the method) is counted apart. The probes' own cost
 * is measured by {@link #calibrate()} on the same thread, so the report subtracts what an empty
 * timed pair records and states the overhead per frame or tick.
 */
public final class ProbeClock {
  private static final Candidate[] CANDIDATES = Candidate.values();
  private static final int TIMED_CALIBRATION_SLOT = CANDIDATES.length;
  private static final int COUNTED_CALIBRATION_SLOT = CANDIDATES.length + 1;
  private static final int SLOTS = CANDIDATES.length + 2;
  private static final long[] SAMPLE_MASKS = sampleMasks();
  private static final int CALIBRATION_PAIRS = 200_000;
  private static final double NANOS_PER_MILLI = 1_000_000.0;
  private static final double PERCENT = 100.0;

  /**
   * Thread-confined clocks of the render thread and the integrated-server thread (declared after
   * the sizes they use).
   */
  public static final ProbeClock RENDER = new ProbeClock(Candidate.ProbeThread.RENDER);

  public static final ProbeClock SERVER = new ProbeClock(Candidate.ProbeThread.SERVER);

  private final Candidate.ProbeThread thread;
  private final long[] starts = new long[SLOTS];
  private final boolean[] open = new boolean[SLOTS];
  private final long[] totals = new long[SLOTS];
  private final long[] entries = new long[SLOTS];
  private final long[] exits = new long[SLOTS];
  private final long[] timed = new long[SLOTS];
  private boolean recording;
  private double timedNanosPerPair;
  private double countedNanosPerPair;
  private double recordedNanosPerPair;

  private ProbeClock(Candidate.ProbeThread thread) {
    this.thread = thread;
  }

  /** Mask on the entry count: a call is timed when {@code count & mask == 0}. */
  private static long[] sampleMasks() {
    long[] masks = new long[SLOTS];
    for (Candidate candidate : CANDIDATES) {
      masks[candidate.ordinal()] = (1L << candidate.sampleShift()) - 1;
    }
    masks[COUNTED_CALIBRATION_SLOT] = Long.MAX_VALUE;
    return masks;
  }

  public void begin(Candidate candidate) {
    if (recording) {
      beginSlot(candidate.ordinal());
    }
  }

  public void end(Candidate candidate) {
    if (recording) {
      endSlot(candidate.ordinal());
    }
  }

  private void beginSlot(int slot) {
    if ((entries[slot]++ & SAMPLE_MASKS[slot]) == 0) {
      open[slot] = true;
      starts[slot] = System.nanoTime();
    }
  }

  private void endSlot(int slot) {
    exits[slot]++;
    if (open[slot]) {
      totals[slot] += System.nanoTime() - starts[slot];
      timed[slot]++;
      open[slot] = false;
    }
  }

  /**
   * Runs {@value #CALIBRATION_PAIRS} empty entry/exit pairs on the owning thread through the same
   * code path as a probe, timed and counted-only: the wall cost of each kind of pair (overhead) and
   * what an empty timed pair records (the bias every timed call carries). Not while recording.
   */
  JsonObject calibrate() {
    clear();
    recording = true;
    long start = System.nanoTime();
    for (int pair = 0; pair < CALIBRATION_PAIRS; pair++) {
      beginSlot(TIMED_CALIBRATION_SLOT);
      endSlot(TIMED_CALIBRATION_SLOT);
    }
    long middle = System.nanoTime();
    for (int pair = 0; pair < CALIBRATION_PAIRS; pair++) {
      beginSlot(COUNTED_CALIBRATION_SLOT);
      endSlot(COUNTED_CALIBRATION_SLOT);
    }
    long end = System.nanoTime();
    recording = false;
    timedNanosPerPair = (middle - start) / (double) CALIBRATION_PAIRS;
    countedNanosPerPair = (end - middle) / (double) CALIBRATION_PAIRS;
    recordedNanosPerPair = totals[TIMED_CALIBRATION_SLOT] / (double) CALIBRATION_PAIRS;
    clear();
    JsonObject calibration = new JsonObject();
    calibration.addProperty("pairs", CALIBRATION_PAIRS);
    calibration.addProperty("timedNanosPerPair", timedNanosPerPair);
    calibration.addProperty("countedNanosPerPair", countedNanosPerPair);
    calibration.addProperty("recordedNanosPerPair", recordedNanosPerPair);
    return calibration;
  }

  void start() {
    clear();
    recording = true;
  }

  /** Stops recording and returns this round's raw sums; read on the owning thread. */
  Round stop() {
    recording = false;
    Round round =
        new Round(
            thread,
            totals.clone(),
            entries.clone(),
            exits.clone(),
            timed.clone(),
            new Calibration(timedNanosPerPair, countedNanosPerPair, recordedNanosPerPair));
    clear();
    return round;
  }

  /** The probe cost measured by the last {@link #calibrate()}, in nanoseconds per pair. */
  record Calibration(double timedPair, double countedPair, double recordedPerTimedPair) {}

  /** One round's sums for the candidates of one thread, with the calibration in force. */
  record Round(
      Candidate.ProbeThread thread,
      long[] totals,
      long[] entries,
      long[] exits,
      long[] timed,
      Calibration calibration) {
    /**
     * Each candidate of this thread per unit (frame or tick): calls, timed calls, raw milliseconds
     * of the timed calls and the estimated inclusive milliseconds of all calls without the
     * empty-probe bias; plus the probes' total overhead as milliseconds per unit and percent of
     * {@code unitMeanMs}.
     */
    JsonObject toJson(long units, double unitMeanMs) {
      JsonObject json = new JsonObject();
      double overheadNanos = 0;
      for (Candidate candidate : CANDIDATES) {
        if (candidate.thread() != thread) {
          continue;
        }
        int slot = candidate.ordinal();
        overheadNanos +=
            timed[slot] * calibration.timedPair()
                + (entries[slot] - timed[slot]) * calibration.countedPair();
        double unbiased =
            Math.max(0, totals[slot] - timed[slot] * calibration.recordedPerTimedPair());
        double estimated = timed[slot] == 0 ? 0 : unbiased / timed[slot] * exits[slot];
        JsonObject probe = new JsonObject();
        probe.addProperty("target", candidate.target());
        probe.addProperty("callsPerUnit", perUnit(exits[slot], units));
        probe.addProperty("timedCallsPerUnit", perUnit(timed[slot], units));
        probe.addProperty("unmatchedEntries", entries[slot] - exits[slot]);
        probe.addProperty("rawTimedMsPerUnit", perUnit(totals[slot] / NANOS_PER_MILLI, units));
        probe.addProperty("msPerUnit", perUnit(estimated / NANOS_PER_MILLI, units));
        json.add(candidate.name(), probe);
      }
      double overheadMs = perUnit(overheadNanos / NANOS_PER_MILLI, units);
      JsonObject overhead = new JsonObject();
      overhead.addProperty("msPerUnit", overheadMs);
      overhead.addProperty(
          "percentOfUnit", unitMeanMs <= 0 ? 0 : overheadMs / unitMeanMs * PERCENT);
      json.add("probeOverhead", overhead);
      return json;
    }
  }

  private static double perUnit(double total, long units) {
    return units == 0 ? 0 : total / units;
  }

  private void clear() {
    for (int slot = 0; slot < SLOTS; slot++) {
      totals[slot] = 0;
      entries[slot] = 0;
      exits[slot] = 0;
      timed[slot] = 0;
      open[slot] = false;
    }
  }
}

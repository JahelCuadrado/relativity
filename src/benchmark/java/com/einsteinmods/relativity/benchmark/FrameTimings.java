package com.einsteinmods.relativity.benchmark;

/**
 * Render-thread frame clock fed by {@code MinecraftFrameMixin}. Every method runs on the render
 * thread (the script starts and stops it through {@code Minecraft#submit}), so no field needs
 * synchronisation. Cost while idle: one static boolean read per hook; while recording: three {@code
 * System.nanoTime}, two thread-CPU reads (cost in the report's {@code cpuClock}) and four array
 * stores per frame, no allocation.
 *
 * <p>"Render work" is {@code renderFrame} up to the frame limiter ({@code FramerateLimiter}), which
 * parks and then spins until the FPS cap's deadline: its CPU time would measure the cap, not the
 * frame.
 */
public final class FrameTimings {
  private static final int CAPACITY = 1 << 18;
  private static final long NOT_STARTED = 0L;

  private static final TimingBuffer FRAME_INTERVALS = new TimingBuffer(CAPACITY);
  private static final TimingBuffer RENDER_DURATIONS = new TimingBuffer(CAPACITY);
  private static final TimingBuffer RENDER_WORK_DURATIONS = new TimingBuffer(CAPACITY);
  private static final TimingBuffer RENDER_WORK_CPU = new TimingBuffer(CAPACITY);
  private static boolean recording;
  private static long lastFrameStartNanos = NOT_STARTED;
  private static long renderStartNanos = NOT_STARTED;
  private static long workStartNanos = NOT_STARTED;
  private static long workStartCpuNanos;

  private FrameTimings() {}

  public static void onFrameStart() {
    if (!recording) {
      return;
    }
    long now = System.nanoTime();
    if (lastFrameStartNanos != NOT_STARTED) {
      FRAME_INTERVALS.add(now - lastFrameStartNanos);
    }
    lastFrameStartNanos = now;
  }

  public static void onRenderStart() {
    if (recording) {
      workStartCpuNanos = ThreadCpuClock.now();
      renderStartNanos = System.nanoTime();
      workStartNanos = renderStartNanos;
    }
  }

  /** Before the frame limiter, or at the end of the frame when there is no FPS cap. */
  public static void onRenderWorkEnd() {
    if (recording && workStartNanos != NOT_STARTED) {
      RENDER_WORK_DURATIONS.add(System.nanoTime() - workStartNanos);
      long cpu = ThreadCpuClock.now();
      if (cpu != ThreadCpuClock.UNSUPPORTED) {
        RENDER_WORK_CPU.add(cpu - workStartCpuNanos);
      }
      workStartNanos = NOT_STARTED;
    }
  }

  public static void onRenderEnd() {
    onRenderWorkEnd();
    if (recording && renderStartNanos != NOT_STARTED) {
      RENDER_DURATIONS.add(System.nanoTime() - renderStartNanos);
      renderStartNanos = NOT_STARTED;
    }
  }

  static void start() {
    FRAME_INTERVALS.clear();
    RENDER_DURATIONS.clear();
    RENDER_WORK_DURATIONS.clear();
    RENDER_WORK_CPU.clear();
    lastFrameStartNanos = NOT_STARTED;
    renderStartNanos = NOT_STARTED;
    workStartNanos = NOT_STARTED;
    recording = true;
  }

  static Samples stop() {
    recording = false;
    return new Samples(
        FRAME_INTERVALS.snapshot(),
        RENDER_DURATIONS.snapshot(),
        RENDER_WORK_DURATIONS.snapshot(),
        RENDER_WORK_CPU.snapshot(),
        FRAME_INTERVALS.dropped()
            + RENDER_DURATIONS.dropped()
            + RENDER_WORK_DURATIONS.dropped()
            + RENDER_WORK_CPU.dropped());
  }

  /**
   * One round, in nanoseconds: frame intervals, whole {@code renderFrame} wall time, render work
   * (up to the frame limiter) wall time and render-thread CPU time of that work.
   */
  record Samples(
      long[] frameIntervals,
      long[] renderDurations,
      long[] renderWorkDurations,
      long[] renderWorkCpu,
      int dropped) {}
}

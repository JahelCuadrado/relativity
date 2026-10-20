package com.einsteinmods.relativity.benchmark;

import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Integrated-server tick clock (Fabric's tick events wrap {@code MinecraftServer#tickServer}, the
 * span vanilla reports as MSPT), with the server thread's CPU time per tick, and newly generated
 * chunk counter. Tick fields are touched only on the server thread (the script starts and stops
 * them through {@code MinecraftServer#submit}); the chunk counter is atomic because chunk mods may
 * finish chunks off the server thread. Cost while recording: two {@code System.nanoTime}, two
 * thread-CPU reads and two array stores per tick, no allocation.
 */
final class ServerTimings {
  private static final int CAPACITY = 1 << 16;
  private static final long NOT_STARTED = 0L;

  private static final TimingBuffer TICK_DURATIONS = new TimingBuffer(CAPACITY);
  private static final TimingBuffer TICK_CPU = new TimingBuffer(CAPACITY);
  private static final AtomicLong GENERATED_CHUNKS = new AtomicLong();
  private static boolean recording;
  private static long tickStartNanos = NOT_STARTED;
  private static long tickStartCpuNanos;

  private ServerTimings() {}

  static void register() {
    ServerTickEvents.START_SERVER_TICK.register(server -> onTickStart());
    ServerTickEvents.END_SERVER_TICK.register(server -> onTickEnd());
    ServerChunkEvents.CHUNK_LOAD.register(
        (level, chunk, generated) -> {
          if (generated) {
            GENERATED_CHUNKS.incrementAndGet();
          }
        });
  }

  private static void onTickStart() {
    if (recording) {
      tickStartCpuNanos = ThreadCpuClock.now();
      tickStartNanos = System.nanoTime();
    }
  }

  private static void onTickEnd() {
    if (recording && tickStartNanos != NOT_STARTED) {
      TICK_DURATIONS.add(System.nanoTime() - tickStartNanos);
      long cpu = ThreadCpuClock.now();
      if (cpu != ThreadCpuClock.UNSUPPORTED) {
        TICK_CPU.add(cpu - tickStartCpuNanos);
      }
      tickStartNanos = NOT_STARTED;
    }
  }

  static void start() {
    TICK_DURATIONS.clear();
    TICK_CPU.clear();
    tickStartNanos = NOT_STARTED;
    recording = true;
  }

  static Samples stop() {
    recording = false;
    return new Samples(TICK_DURATIONS.snapshot(), TICK_CPU.snapshot());
  }

  static long generatedChunks() {
    return GENERATED_CHUNKS.get();
  }

  /** Tick wall durations and server-thread CPU time per tick of one round, in nanoseconds. */
  record Samples(long[] tickDurations, long[] tickCpu) {}
}

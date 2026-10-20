package com.einsteinmods.relativity.benchmark;

import java.util.Arrays;

/**
 * Preallocated buffer of durations in nanoseconds, owned by one thread. Adding never allocates;
 * samples beyond the capacity are counted as dropped instead of growing the buffer.
 */
final class TimingBuffer {
  private final long[] samples;
  private int size;
  private int dropped;

  TimingBuffer(int capacity) {
    this.samples = new long[capacity];
  }

  void add(long nanos) {
    if (size < samples.length) {
      samples[size++] = nanos;
    } else {
      dropped++;
    }
  }

  void clear() {
    size = 0;
    dropped = 0;
  }

  long[] snapshot() {
    return Arrays.copyOf(samples, size);
  }

  int dropped() {
    return dropped;
  }
}

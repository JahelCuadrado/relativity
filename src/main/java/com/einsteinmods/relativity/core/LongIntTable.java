package com.einsteinmods.relativity.core;

import java.util.Arrays;

/**
 * Open-addressing map from {@code long} keys to non-negative {@code int} values, with no boxing and
 * no allocation except when it grows. Not thread-safe.
 */
public final class LongIntTable {
  /** Returned by {@link #get} for a missing key. */
  public static final int MISSING = -1;

  private static final int MINIMUM_CAPACITY = 16;
  private static final int MAX_LOAD_NUMERATOR = 1;
  private static final int MAX_LOAD_DENOMINATOR = 2;
  private static final long HASH_MULTIPLIER = 0x9E3779B97F4A7C15L;
  private static final int HASH_SHIFT = 32;

  private long[] keys;
  private int[] values;
  private boolean[] used;
  private int size;

  public LongIntTable() {
    allocate(MINIMUM_CAPACITY);
  }

  public int size() {
    return size;
  }

  /** Value of {@code key}, or {@link #MISSING}. */
  public int get(long key) {
    int mask = keys.length - 1;
    for (int slot = slotOf(key, mask); used[slot]; slot = (slot + 1) & mask) {
      if (keys[slot] == key) {
        return values[slot];
      }
    }
    return MISSING;
  }

  /** Maps {@code key} to {@code value} (non-negative), replacing any previous value. */
  public void put(long key, int value) {
    if (value < 0) {
      throw new IllegalArgumentException("Negative value " + value);
    }
    if ((size + 1) * MAX_LOAD_DENOMINATOR > keys.length * MAX_LOAD_NUMERATOR) {
      grow();
    }
    int mask = keys.length - 1;
    int slot = slotOf(key, mask);
    while (used[slot]) {
      if (keys[slot] == key) {
        values[slot] = value;
        return;
      }
      slot = (slot + 1) & mask;
    }
    used[slot] = true;
    keys[slot] = key;
    values[slot] = value;
    size++;
  }

  /** Removes {@code key} if present (backward-shift deletion, no tombstones). */
  public void remove(long key) {
    int mask = keys.length - 1;
    int slot = slotOf(key, mask);
    while (used[slot]) {
      if (keys[slot] == key) {
        shiftBack(slot, mask);
        size--;
        return;
      }
      slot = (slot + 1) & mask;
    }
  }

  public void clear() {
    if (size == 0) {
      return;
    }
    Arrays.fill(used, false);
    size = 0;
  }

  private void shiftBack(int emptied, int mask) {
    int hole = emptied;
    int slot = (hole + 1) & mask;
    while (used[slot]) {
      int home = slotOf(keys[slot], mask);
      // Move the entry into the hole when its home is not cyclically within (hole, slot].
      boolean homeAfterHole = ((slot - home) & mask) >= ((slot - hole) & mask);
      if (homeAfterHole) {
        keys[hole] = keys[slot];
        values[hole] = values[slot];
        hole = slot;
      }
      slot = (slot + 1) & mask;
    }
    used[hole] = false;
  }

  private void grow() {
    long[] oldKeys = keys;
    int[] oldValues = values;
    boolean[] oldUsed = used;
    allocate(oldKeys.length * 2);
    size = 0;
    for (int slot = 0; slot < oldKeys.length; slot++) {
      if (oldUsed[slot]) {
        put(oldKeys[slot], oldValues[slot]);
      }
    }
  }

  private void allocate(int capacity) {
    keys = new long[capacity];
    values = new int[capacity];
    used = new boolean[capacity];
  }

  private static int slotOf(long key, int mask) {
    long mixed = key * HASH_MULTIPLIER;
    return (int) (mixed ^ (mixed >>> HASH_SHIFT)) & mask;
  }
}

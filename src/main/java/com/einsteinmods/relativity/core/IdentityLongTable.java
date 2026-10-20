package com.einsteinmods.relativity.core;

import java.util.Arrays;

/**
 * Open-addressing map from object identity to {@code long}, with no allocation except when it
 * grows. Meant to be filled and cleared once per frame; there is no single-key removal. Not
 * thread-safe.
 */
public final class IdentityLongTable {
  /** Returned by {@link #get} for a missing key. */
  public static final long MISSING = Long.MIN_VALUE;

  private static final int MINIMUM_CAPACITY = 16;
  private static final int MAX_LOAD_NUMERATOR = 1;
  private static final int MAX_LOAD_DENOMINATOR = 2;
  private static final int HASH_MULTIPLIER = 0x9E3779B9;
  private static final int HASH_SHIFT = 16;

  private Object[] keys = new Object[MINIMUM_CAPACITY];
  private long[] values = new long[MINIMUM_CAPACITY];
  private int size;

  public boolean isEmpty() {
    return size == 0;
  }

  /** Value stored for exactly this object, or {@link #MISSING}. */
  public long get(Object key) {
    int mask = keys.length - 1;
    for (int slot = slotOf(key, mask); keys[slot] != null; slot = (slot + 1) & mask) {
      if (keys[slot] == key) {
        return values[slot];
      }
    }
    return MISSING;
  }

  /** Maps exactly this object to {@code value}, replacing any previous value. */
  public void put(Object key, long value) {
    if (value == MISSING) {
      throw new IllegalArgumentException("Reserved value");
    }
    if ((size + 1) * MAX_LOAD_DENOMINATOR > keys.length * MAX_LOAD_NUMERATOR) {
      grow();
    }
    int mask = keys.length - 1;
    int slot = slotOf(key, mask);
    while (keys[slot] != null) {
      if (keys[slot] == key) {
        values[slot] = value;
        return;
      }
      slot = (slot + 1) & mask;
    }
    keys[slot] = key;
    values[slot] = value;
    size++;
  }

  /** Forgets every key (drops the references, keeps the capacity). */
  public void clear() {
    if (size == 0) {
      return;
    }
    Arrays.fill(keys, null);
    size = 0;
  }

  private void grow() {
    Object[] oldKeys = keys;
    long[] oldValues = values;
    keys = new Object[oldKeys.length * 2];
    values = new long[oldKeys.length * 2];
    size = 0;
    for (int slot = 0; slot < oldKeys.length; slot++) {
      if (oldKeys[slot] != null) {
        put(oldKeys[slot], oldValues[slot]);
      }
    }
  }

  private static int slotOf(Object key, int mask) {
    int mixed = System.identityHashCode(key) * HASH_MULTIPLIER;
    return (mixed ^ (mixed >>> HASH_SHIFT)) & mask;
  }
}

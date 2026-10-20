package com.einsteinmods.relativity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LongIntTableTest {
  private static final long SEED = 42L;
  private static final int OPERATIONS = 200_000;
  private static final int KEY_RANGE = 3_000;
  private static final int PUT_PERCENT = 50;
  private static final int REMOVE_PERCENT = 30;
  private static final int PERCENT = 100;

  @Test
  void behavesLikeAHashMapUnderRandomPutsAndRemoves() {
    Random random = new Random(SEED);
    LongIntTable table = new LongIntTable();
    Map<Long, Integer> expected = new HashMap<>();
    for (int operation = 0; operation < OPERATIONS; operation++) {
      // Spread keys over the whole long range so that probe chains wrap around.
      long key = (random.nextInt(KEY_RANGE) - KEY_RANGE / 2) * 0x1000_0000_0001L;
      int roll = random.nextInt(PERCENT);
      if (roll < PUT_PERCENT) {
        int value = random.nextInt(Integer.MAX_VALUE);
        table.put(key, value);
        expected.put(key, value);
      } else if (roll < PUT_PERCENT + REMOVE_PERCENT) {
        table.remove(key);
        expected.remove(key);
      } else {
        assertEquals(expected.getOrDefault(key, LongIntTable.MISSING), table.get(key));
      }
      assertEquals(expected.size(), table.size());
    }
    for (Map.Entry<Long, Integer> entry : expected.entrySet()) {
      assertEquals(entry.getValue(), table.get(entry.getKey()));
    }
    table.clear();
    assertEquals(0, table.size());
    assertEquals(LongIntTable.MISSING, table.get(expected.keySet().iterator().next()));
  }

  @Test
  void rejectsNegativeValues() {
    assertThrows(IllegalArgumentException.class, () -> new LongIntTable().put(1L, -1));
  }
}

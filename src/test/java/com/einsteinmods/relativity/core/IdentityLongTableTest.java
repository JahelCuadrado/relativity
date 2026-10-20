package com.einsteinmods.relativity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityLongTableTest {
  private static final int KEYS = 10_000;

  @Test
  void distinguishesEqualButDistinctObjects() {
    IdentityLongTable table = new IdentityLongTable();
    String first = new String("pose");
    String second = new String("pose");
    table.put(first, 1L);
    assertEquals(1L, table.get(first));
    assertEquals(IdentityLongTable.MISSING, table.get(second));
    table.put(second, 2L);
    assertEquals(1L, table.get(first));
    assertEquals(2L, table.get(second));
  }

  @Test
  void keepsEveryValueWhileGrowingAndForgetsAllOnClear() {
    IdentityLongTable table = new IdentityLongTable();
    List<Object> keys = new ArrayList<>();
    for (int index = 0; index < KEYS; index++) {
      Object key = new Object();
      keys.add(key);
      table.put(key, index);
    }
    for (int index = 0; index < KEYS; index++) {
      assertEquals(index, table.get(keys.get(index)));
    }
    table.put(keys.get(0), -5L);
    assertEquals(-5L, table.get(keys.get(0)));
    table.clear();
    assertTrue(table.isEmpty());
    for (Object key : keys) {
      assertEquals(IdentityLongTable.MISSING, table.get(key));
    }
  }

  @Test
  void rejectsTheMissingMarker() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new IdentityLongTable().put(new Object(), IdentityLongTable.MISSING));
  }
}

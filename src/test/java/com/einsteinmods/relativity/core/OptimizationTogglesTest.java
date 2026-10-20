package com.einsteinmods.relativity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptimizationTogglesTest {
  @Test
  void keysAreUnique() {
    Set<String> keys = new HashSet<>();
    for (OptimizationToggle toggle : OptimizationToggles.ALL) {
      keys.add(toggle.key());
    }
    assertEquals(OptimizationToggles.ALL.size(), keys.size(), "duplicate optimization key");
  }

  @Test
  void keysOutsideTheWhitelistAreRejected() {
    for (String key : List.of("", "Upper", "with space", "dot.ted", "dash-ed")) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new OptimizationToggle(key, true, "config.relativity.x", false),
          key);
    }
  }
}

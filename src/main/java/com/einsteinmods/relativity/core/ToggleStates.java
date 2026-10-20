package com.einsteinmods.relativity.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable on/off value of every optimization, by {@link OptimizationToggle#key()}.
 *
 * @param enabledByKey values of the known optimizations in screen order; a missing key means its
 *     default
 */
public record ToggleStates(Map<String, Boolean> enabledByKey) {
  public ToggleStates {
    Map<String, Boolean> copy = new LinkedHashMap<>(enabledByKey);
    copy.values().forEach(Objects::requireNonNull);
    enabledByKey = Collections.unmodifiableMap(copy);
  }

  public static ToggleStates defaults(List<OptimizationToggle> toggles) {
    return resolve(toggles, null);
  }

  /**
   * Keeps only the saved values of {@code toggles} (whitelist): unknown keys are dropped, missing
   * or null values take the toggle's default.
   */
  public static ToggleStates resolve(
      List<OptimizationToggle> toggles, @Nullable Map<String, Boolean> saved) {
    Map<String, Boolean> resolved = new LinkedHashMap<>();
    for (OptimizationToggle toggle : toggles) {
      Boolean value = saved == null ? null : saved.get(toggle.key());
      resolved.put(toggle.key(), value == null ? toggle.enabledByDefault() : value);
    }
    return new ToggleStates(resolved);
  }

  public boolean isEnabled(OptimizationToggle toggle) {
    return enabledByKey.getOrDefault(toggle.key(), toggle.enabledByDefault());
  }
}

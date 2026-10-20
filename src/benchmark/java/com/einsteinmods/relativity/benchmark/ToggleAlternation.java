package com.einsteinmods.relativity.benchmark;

import com.einsteinmods.relativity.config.RelativitySettings;
import com.einsteinmods.relativity.core.OptimizationToggle;
import com.einsteinmods.relativity.core.OptimizationToggles;
import com.einsteinmods.relativity.core.ToggleStates;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A/B within one session ({@code -Ptoggle=<key>}): measured scene rounds alternate the toggle off
 * (A) and on (B), so both states share the same JVM, world, caches and machine load. Switching uses
 * the same {@link RelativitySettings#save} path as the settings screen; only toggles that apply
 * without a restart can be compared this way. The player's saved value is restored afterwards.
 */
final class ToggleAlternation {
  private final OptimizationToggle toggle;
  private final ToggleStates original;

  private ToggleAlternation(OptimizationToggle toggle, ToggleStates original) {
    this.toggle = toggle;
    this.original = original;
  }

  /**
   * Resolves {@code key} among the registered toggles.
   *
   * @throws IllegalArgumentException if the key is unknown or the toggle needs a restart
   */
  static ToggleAlternation forKey(String key) {
    OptimizationToggle toggle =
        OptimizationToggles.ALL.stream()
            .filter(candidate -> candidate.key().equals(key))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unknown Relativity toggle '"
                            + key
                            + "' (-Ptoggle). Registered keys: "
                            + OptimizationToggles.ALL.stream()
                                .map(OptimizationToggle::key)
                                .toList()));
    if (toggle.requiresRestart()) {
      throw new IllegalArgumentException(
          "Relativity toggle '"
              + key
              + "' requires a restart, so it cannot switch between rounds of one session");
    }
    return new ToggleAlternation(toggle, RelativitySettings.current());
  }

  String key() {
    return toggle.key();
  }

  /** Round 0, 2, 4... is A (off), round 1, 3, 5... is B (on). */
  static boolean enabledInRound(int round) {
    return round % 2 == 1;
  }

  /** Saves the toggle's state and checks the settings now report it. */
  void apply(boolean enabled) {
    Map<String, Boolean> states = new LinkedHashMap<>(RelativitySettings.current().enabledByKey());
    states.put(toggle.key(), enabled);
    RelativitySettings.save(new ToggleStates(states));
    if (RelativitySettings.current().isEnabled(toggle) != enabled) {
      throw new IllegalStateException(
          "Relativity toggle '" + toggle.key() + "' did not switch to " + enabled);
    }
  }

  void restore() {
    RelativitySettings.save(original);
  }
}

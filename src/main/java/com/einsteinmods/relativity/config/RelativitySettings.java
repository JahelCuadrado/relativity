package com.einsteinmods.relativity.config;

import com.einsteinmods.relativity.core.OptimizationToggles;
import com.einsteinmods.relativity.core.ToggleStates;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The settings file, read once when this class initialises (the main entrypoint, or a mixin that
 * asks first).
 *
 * <p>Optimizations with {@code requiresRestart} read {@link #startup()} once into a {@code static
 * final} field of their own, so the hot path pays one constant check. Optimizations that apply
 * without a restart read {@link #current()}.
 */
public final class RelativitySettings {
  private static final JsonConfigFile<SettingsFile> FILE =
      new JsonConfigFile<>(
          FabricLoader.getInstance().getConfigDir().resolve(SettingsFile.FILE_NAME),
          SettingsFile.class,
          SettingsFile.DEFAULTS);
  private static final ToggleStates STARTUP = FILE.load().toStates(OptimizationToggles.ALL);
  private static volatile ToggleStates current = STARTUP;

  private RelativitySettings() {}

  /** Values the game started with; never changes until restart. */
  public static ToggleStates startup() {
    return STARTUP;
  }

  /** Last saved values. */
  public static ToggleStates current() {
    return current;
  }

  /** Writes the file and makes {@code states} the current values. */
  public static void save(ToggleStates states) {
    FILE.save(SettingsFile.from(states));
    current = states;
  }
}

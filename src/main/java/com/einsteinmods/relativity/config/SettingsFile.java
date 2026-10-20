package com.einsteinmods.relativity.config;

import com.einsteinmods.relativity.core.OptimizationToggle;
import com.einsteinmods.relativity.core.OptimizationToggles;
import com.einsteinmods.relativity.core.ToggleStates;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * On-disk shape of {@code config/relativity.json}: one on/off value per optimization key. A missing
 * map or value (Gson reads both as null) takes the default; unknown keys are ignored.
 *
 * <p>Bump {@link #CURRENT_SCHEMA_VERSION} when a key is renamed or changes meaning, and migrate
 * older files in {@link #toStates}.
 */
public record SettingsFile(int schemaVersion, @Nullable Map<String, Boolean> optimizations) {
  public static final int CURRENT_SCHEMA_VERSION = 1;
  public static final String FILE_NAME = "relativity.json";

  public static final SettingsFile DEFAULTS = from(ToggleStates.defaults(OptimizationToggles.ALL));

  public static SettingsFile from(ToggleStates states) {
    return new SettingsFile(CURRENT_SCHEMA_VERSION, states.enabledByKey());
  }

  public ToggleStates toStates(List<OptimizationToggle> toggles) {
    return ToggleStates.resolve(toggles, optimizations);
  }
}

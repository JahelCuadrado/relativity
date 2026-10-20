package com.einsteinmods.relativity.core;

import java.util.regex.Pattern;

/**
 * One optimization the player can switch on or off in the settings screen.
 *
 * @param key stable id stored in the settings file; never rename it once released
 * @param enabledByDefault value used when the settings file has none
 * @param translationKey lang key of the name shown in the screen; the description is {@link
 *     #descriptionKey()}
 * @param requiresRestart true when the optimization is applied once at startup (mixins read the
 *     startup snapshot), so a change only takes effect after a restart
 */
public record OptimizationToggle(
    String key, boolean enabledByDefault, String translationKey, boolean requiresRestart) {
  private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_]+");
  private static final String DESCRIPTION_SUFFIX = ".description";

  public OptimizationToggle {
    if (!KEY_PATTERN.matcher(key).matches()) {
      throw new IllegalArgumentException("Invalid optimization key: " + key);
    }
    if (translationKey.isBlank()) {
      throw new IllegalArgumentException("Blank translation key for optimization " + key);
    }
  }

  public String descriptionKey() {
    return translationKey + DESCRIPTION_SUFFIX;
  }
}

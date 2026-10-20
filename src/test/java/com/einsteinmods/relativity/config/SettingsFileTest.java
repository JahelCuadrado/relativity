package com.einsteinmods.relativity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einsteinmods.relativity.core.OptimizationToggle;
import com.einsteinmods.relativity.core.ToggleStates;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsFileTest {
  private static final OptimizationToggle ON_BY_DEFAULT =
      new OptimizationToggle("on_by_default", true, "config.relativity.on_by_default", true);
  private static final OptimizationToggle OFF_BY_DEFAULT =
      new OptimizationToggle("off_by_default", false, "config.relativity.off_by_default", false);
  private static final List<OptimizationToggle> TOGGLES = List.of(ON_BY_DEFAULT, OFF_BY_DEFAULT);
  private static final ToggleStates DEFAULTS = ToggleStates.defaults(TOGGLES);

  private static JsonConfigFile<SettingsFile> file(Path directory) {
    return new JsonConfigFile<>(
        directory.resolve(SettingsFile.FILE_NAME), SettingsFile.class, SettingsFile.from(DEFAULTS));
  }

  private static ToggleStates loadFrom(Path directory, String json) throws IOException {
    Files.writeString(directory.resolve(SettingsFile.FILE_NAME), json, StandardCharsets.UTF_8);
    return file(directory).load().toStates(TOGGLES);
  }

  @Test
  void missingFileWritesAndGivesDefaults(@TempDir Path directory) {
    ToggleStates loaded = file(directory).load().toStates(TOGGLES);

    assertEquals(DEFAULTS, loaded);
    assertTrue(loaded.isEnabled(ON_BY_DEFAULT));
    assertFalse(loaded.isEnabled(OFF_BY_DEFAULT));
    assertTrue(Files.exists(directory.resolve(SettingsFile.FILE_NAME)));
    assertEquals(SettingsFile.CURRENT_SCHEMA_VERSION, file(directory).load().schemaVersion());
  }

  @Test
  void savedStatesLoadBack(@TempDir Path directory) {
    ToggleStates flipped =
        new ToggleStates(Map.of(ON_BY_DEFAULT.key(), false, OFF_BY_DEFAULT.key(), true));
    file(directory).save(SettingsFile.from(flipped));

    assertEquals(flipped, file(directory).load().toStates(TOGGLES));
  }

  @Test
  void brokenJsonGivesDefaultsAndIsReplaced(@TempDir Path directory) throws IOException {
    assertEquals(DEFAULTS, loadFrom(directory, "{ \"optimizations\": { \"on_by_default\": "));
    assertEquals(DEFAULTS, file(directory).load().toStates(TOGGLES));
  }

  @Test
  void wrongTypesGiveDefaults(@TempDir Path directory) throws IOException {
    assertEquals(DEFAULTS, loadFrom(directory, "{ \"optimizations\": [1, 2] }"));
  }

  @Test
  void missingNullAndUnknownKeysAreIgnored(@TempDir Path directory) throws IOException {
    ToggleStates loaded =
        loadFrom(
            directory,
            """
            {
              "schemaVersion": 1,
              "optimizations": {
                "off_by_default": true,
                "on_by_default": null,
                "removed_optimization": true
              }
            }
            """);

    assertTrue(loaded.isEnabled(ON_BY_DEFAULT));
    assertTrue(loaded.isEnabled(OFF_BY_DEFAULT));
    assertEquals(TOGGLES.size(), loaded.enabledByKey().size());
  }

  @Test
  void missingOptimizationsGiveDefaults(@TempDir Path directory) throws IOException {
    assertEquals(DEFAULTS, loadFrom(directory, "{ \"schemaVersion\": 1 }"));
  }
}

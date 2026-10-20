package com.einsteinmods.relativity.benchmark;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Session settings passed by the {@code runBenchmarkSession*} Gradle tasks as system properties.
 * Without {@code relativity.benchmark.out} the benchmark mod stays inert. {@code toggleKey} is the
 * Relativity optimization the scenes alternate off/on between measured rounds ({@code -Ptoggle}).
 */
record BenchmarkConfig(
    Path outputDirectory, String profile, int session, Optional<String> toggleKey) {
  private static final String PREFIX = "relativity.benchmark.";
  private static final String DEFAULT_PROFILE = "vanilla";
  private static final int DEFAULT_SESSION = 1;

  static Optional<BenchmarkConfig> fromSystemProperties() {
    String output = System.getProperty(PREFIX + "out");
    if (output == null || output.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(
        new BenchmarkConfig(
            Path.of(output),
            System.getProperty(PREFIX + "profile", DEFAULT_PROFILE),
            Integer.getInteger(PREFIX + "session", DEFAULT_SESSION),
            Optional.ofNullable(System.getProperty(PREFIX + "toggle"))
                .filter(key -> !key.isBlank())));
  }
}

package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.Bootstrap;

/**
 * Dev-only wall-clock marks of the startup phases vanilla already times for its load-time telemetry
 * ({@code GameLoadTimesEvent}: window created, loading overlay begin and end), plus the JVM start,
 * the benchmark's client entrypoint and the title screen, as epoch milliseconds so the report can
 * cut the startup JFR recording into the same phases. Marks arrive on the render thread during
 * startup (allocation is irrelevant there) and are read once on the title screen.
 */
public final class StartupPhases {
  private static final Map<String, Long> MARKS = new LinkedHashMap<>();

  private StartupPhases() {}

  public static synchronized void onStepBegin(String step) {
    MARKS.putIfAbsent(step + ".begin", System.currentTimeMillis());
  }

  public static synchronized void onStepEnd(String step) {
    MARKS.putIfAbsent(step + ".end", System.currentTimeMillis());
  }

  static synchronized void mark(String name, long epochMillis) {
    MARKS.putIfAbsent(name, epochMillis);
  }

  /**
   * Every mark as epoch milliseconds (step marks named after vanilla's {@code TelemetryProperty#id}
   * plus {@code .begin}/{@code .end}), with the JVM start first, and vanilla's registry bootstrap
   * duration ({@code Bootstrap#bootstrapDuration}).
   */
  static synchronized JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("jvmStart", ManagementFactory.getRuntimeMXBean().getStartTime());
    MARKS.forEach(json::addProperty);
    json.addProperty("bootstrapMs", Bootstrap.bootstrapDuration.get());
    return json;
  }
}

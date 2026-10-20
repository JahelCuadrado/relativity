package com.einsteinmods.relativity.benchmark;

import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

/**
 * In-process JFR: the JDK "profile" settings with Java execution samples every 10 ms. The startup
 * recording is started by the JVM flag {@code -XX:StartFlightRecording} (so it covers loading
 * before any mod code runs) and stopped here once the first world is joined.
 */
final class JfrRecordings {
  static final String STARTUP_RECORDING_NAME = "relativity-startup";
  private static final String SETTINGS = "profile";
  private static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
  private static final Duration EXECUTION_SAMPLE_PERIOD = Duration.ofMillis(10);

  private JfrRecordings() {}

  static Recording start(Path destination) throws IOException, ParseException {
    Recording recording = new Recording(Configuration.getConfiguration(SETTINGS));
    recording.enable(EXECUTION_SAMPLE).withPeriod(EXECUTION_SAMPLE_PERIOD);
    recording.setToDisk(true);
    recording.setDestination(destination);
    recording.start();
    return recording;
  }

  /** Stops the recording (which writes its destination file) and releases it. */
  static void finish(Recording recording) {
    recording.stop();
    recording.close();
  }

  /** Returns whether the JVM-started startup recording existed and was written. */
  static boolean finishStartupRecording() {
    for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
      if (STARTUP_RECORDING_NAME.equals(recording.getName())) {
        finish(recording);
        return true;
      }
    }
    return false;
  }
}

package com.einsteinmods.relativity.benchmark;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev-only reference benchmark entrypoint (source set {@code benchmark}, never in the published
 * jar). Inert unless the {@code runBenchmarkSession*} tasks pass {@code relativity.benchmark.out};
 * then it starts the scripted session and a watchdog that kills a stuck client.
 */
public final class BenchmarkClient implements ClientModInitializer {
  private static final Logger LOGGER = LoggerFactory.getLogger("relativity-benchmark");
  private static final Duration SESSION_LIMIT = Duration.ofMinutes(30);
  private static final Duration QUIT_LIMIT = Duration.ofMinutes(2);
  private static final int WATCHDOG_EXIT_CODE = 3;

  @Override
  public void onInitializeClient() {
    Optional<BenchmarkConfig> config = BenchmarkConfig.fromSystemProperties();
    if (config.isEmpty()) {
      return;
    }
    long modInitUptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
    UserInputGate.close();
    ServerTimings.register();
    Thread script =
        startDaemon(
            new SessionScript(config.get(), Minecraft.getInstance(), modInitUptimeMillis),
            "relativity-benchmark-script");
    startDaemon(() -> watch(script), "relativity-benchmark-watchdog");
  }

  private static void watch(Thread script) {
    try {
      script.join(SESSION_LIMIT);
      if (script.isAlive()) {
        LOGGER.error("Benchmark session exceeded {}, halting the client", SESSION_LIMIT);
      } else {
        Thread.sleep(QUIT_LIMIT);
        LOGGER.error("Client did not quit within {} after the session, halting it", QUIT_LIMIT);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.error("Benchmark watchdog interrupted, halting the client", e);
    }
    Runtime.getRuntime().halt(WATCHDOG_EXIT_CODE);
  }

  private static Thread startDaemon(Runnable task, String name) {
    Thread thread = new Thread(task, name);
    thread.setDaemon(true);
    thread.start();
    return thread;
  }
}

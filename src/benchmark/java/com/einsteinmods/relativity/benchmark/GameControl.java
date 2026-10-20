package com.einsteinmods.relativity.benchmark;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/** Runs script steps on the render thread or the integrated-server thread and waits for them. */
final class GameControl {
  private static final long POLL_MILLIS = 5L;
  private static final long NOT_YET = -1L;

  private final Minecraft minecraft;

  GameControl(Minecraft minecraft) {
    this.minecraft = minecraft;
  }

  Minecraft minecraft() {
    return minecraft;
  }

  <T> T callOnClient(Supplier<T> task) throws InterruptedException, ExecutionException {
    return minecraft.submit(task).get();
  }

  void runOnClient(Runnable task) throws InterruptedException, ExecutionException {
    minecraft.submit(task).get();
  }

  IntegratedServer server() throws InterruptedException, ExecutionException {
    IntegratedServer server = callOnClient(minecraft::getSingleplayerServer);
    if (server == null) {
      throw new IllegalStateException("No integrated server running");
    }
    return server;
  }

  <T> T callOnServer(Supplier<T> task) throws InterruptedException, ExecutionException {
    return server().submit(task).get();
  }

  void runOnServer(Runnable task) throws InterruptedException, ExecutionException {
    server().submit(task).get();
  }

  /**
   * Polls {@code condition} on the render thread (once per frame at most) and returns the {@link
   * System#nanoTime} of the frame where it first held.
   */
  long awaitClient(BooleanSupplier condition, Duration timeout)
      throws InterruptedException, ExecutionException, TimeoutException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      long metAt = callOnClient(() -> condition.getAsBoolean() ? System.nanoTime() : NOT_YET);
      if (metAt != NOT_YET) {
        return metAt;
      }
      if (System.nanoTime() > deadline) {
        throw new TimeoutException("Condition not met within " + timeout);
      }
      Thread.sleep(POLL_MILLIS);
    }
  }
}

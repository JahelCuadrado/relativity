package com.einsteinmods.relativity.benchmark;

/**
 * Keeps a running benchmark client deaf to the person at the machine. macOS focuses a newly
 * launched window, so keys and clicks meant for another app reach it: the mouse grab on world join
 * captures the cursor, and Escape (or any key opening a screen) pauses the integrated server
 * ({@code Screen#isPauseScreen} is true by default), which silently freezes every scene. Closed
 * only while a session runs; read on the render thread by {@code UserInputMixin}s.
 */
public final class UserInputGate {
  private static volatile boolean closed;

  private UserInputGate() {}

  static void close() {
    closed = true;
  }

  public static boolean isClosed() {
    return closed;
  }
}

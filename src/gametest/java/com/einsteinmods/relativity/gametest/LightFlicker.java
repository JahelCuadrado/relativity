package com.einsteinmods.relativity.gametest;

/**
 * Dev-only: holds the lightmap's block-light flicker still ({@code
 * LightmapRenderStateExtractor#tick} draws a new random flicker every client tick, even with level
 * ticks frozen), so two screenshots of a frozen scene can match exactly. Read by {@code
 * LightmapFlickerMixin}.
 */
public final class LightFlicker {
  private static volatile boolean held;

  private LightFlicker() {}

  public static boolean isHeld() {
    return held;
  }

  public static void hold(boolean hold) {
    held = hold;
  }
}

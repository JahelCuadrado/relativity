package com.einsteinmods.relativity.core;

import java.util.List;

/**
 * Every optimization, in settings screen order. Adding an optimization means adding its {@link
 * OptimizationToggle} constant here and to {@link #ALL}; the settings file and screen pick it up.
 */
public final class OptimizationToggles {
  /** Leaving a local world returns to the menu at once; the world keeps saving behind it. */
  public static final OptimizationToggle INSTANT_WORLD_EXIT =
      new OptimizationToggle(
          "instant_world_exit", true, "config.relativity.option.instant_world_exit", true);

  /**
   * Item frames that did not change since the last frame reuse their vertices instead of building
   * them again. Read every frame, so it applies without a restart.
   */
  public static final OptimizationToggle ITEM_FRAME_CACHE =
      new OptimizationToggle(
          "item_frame_cache", true, "config.relativity.option.item_frame_cache", false);

  /**
   * Item frames whose item, model and visibility did not change reuse last frame's prepared render
   * state instead of resolving their item and frame models again. Works with Sodium. Read every
   * frame, so it applies without a restart.
   */
  public static final OptimizationToggle ITEM_FRAME_EXTRACT_CACHE =
      new OptimizationToggle(
          "item_frame_extract_cache",
          true,
          "config.relativity.option.item_frame_extract_cache",
          false);

  public static final List<OptimizationToggle> ALL =
      List.of(INSTANT_WORLD_EXIT, ITEM_FRAME_CACHE, ITEM_FRAME_EXTRACT_CACHE);

  private OptimizationToggles() {}
}

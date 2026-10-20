package com.einsteinmods.relativity.benchmark;

/**
 * Mixin priority of the benchmark's timing probes: above the default (1000) so they apply after
 * pack mods. A later {@code @Overwrite} (BadOptimizations on {@code getRenderer}, for example)
 * would otherwise drop probes applied before it, and a later HEAD injector lands ahead of earlier
 * ones, so the probe also times other mods' handlers on the same method.
 */
public final class ProbeMixinPriority {
  public static final int AFTER_PACK_MODS = 2000;

  private ProbeMixinPriority() {}
}

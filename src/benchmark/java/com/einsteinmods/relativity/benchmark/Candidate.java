package com.einsteinmods.relativity.benchmark;

/**
 * Optimization candidates whose inclusive cost the benchmark measures (dev-only probes), with the
 * thread that runs them. Each is timed with {@code System.nanoTime} around the vanilla entry point
 * that still runs under the pack (pack mods that replace the body keep the entry, so their work is
 * included). Candidates nest: {@link #EXTRACT_ENTITY} runs inside {@link
 * #EXTRACT_VISIBLE_ENTITIES}, {@link #BLOCK_RANDOM_TICK} and {@link #FLUID_RANDOM_TICK} inside
 * {@link #CHUNK_TICK}, {@link #FIND_PATH} inside {@link #CREATE_PATH}, which may run inside {@link
 * #VILLAGER_BRAIN}, and {@link #HOPPER_TICK} inside {@link #BLOCK_ENTITY_TICKS}.
 */
public enum Candidate {
  /** {@code LevelExtractor#extractVisibleEntities}: visibility checks plus render-state extract. */
  EXTRACT_VISIBLE_ENTITIES(
      ProbeThread.RENDER, "LevelExtractor#extractVisibleEntities", Sampling.EVERY_CALL),
  /** The {@code extractEntity} call in that loop: renderer lookup and new render state. */
  EXTRACT_ENTITY(ProbeThread.RENDER, "LevelExtractor#extractEntity (call site)", Sampling.ONE_IN_4),
  /** {@code EntityRenderDispatcher#getRenderer(Entity)}, from visibility checks and extract. */
  GET_RENDERER(
      ProbeThread.RENDER, "EntityRenderDispatcher#getRenderer(Entity)", Sampling.ONE_IN_32),
  /** {@code ServerLevel#tickChunk}: precipitation and the random-tick loop of one chunk. */
  CHUNK_TICK(ProbeThread.SERVER, "ServerLevel#tickChunk", Sampling.ONE_IN_8),
  /** {@code BlockStateBase#randomTick}: the block half of a random tick (grass, leaves, crops). */
  BLOCK_RANDOM_TICK(ProbeThread.SERVER, "BlockStateBase#randomTick", Sampling.ONE_IN_8),
  /** {@code FluidState#randomTick}: the fluid half of a random tick (lava). */
  FLUID_RANDOM_TICK(ProbeThread.SERVER, "FluidState#randomTick", Sampling.EVERY_CALL),
  /** {@code Brain#tick} of villagers only. */
  VILLAGER_BRAIN(ProbeThread.SERVER, "Brain#tick (villagers)", Sampling.EVERY_CALL),
  /** {@code PathNavigation#createPath}: path region snapshot plus A* search, any mob. */
  CREATE_PATH(ProbeThread.SERVER, "PathNavigation#createPath", Sampling.EVERY_CALL),
  /** {@code PathFinder#findPath}: the A* search alone. */
  FIND_PATH(ProbeThread.SERVER, "PathFinder#findPath", Sampling.EVERY_CALL),
  /**
   * {@code HopperBlockEntity#pushItemsTick}: one hopper's tick (Lithium's sleeping hoppers skip
   * it).
   */
  HOPPER_TICK(ProbeThread.SERVER, "HopperBlockEntity#pushItemsTick", Sampling.ONE_IN_8),
  /** The {@code tickBlockEntities} call in {@code ServerLevel#tick}: every ticking block entity. */
  BLOCK_ENTITY_TICKS(
      ProbeThread.SERVER, "ServerLevel tickBlockEntities (call site)", Sampling.EVERY_CALL);

  private final ProbeThread thread;
  private final String target;
  private final int sampleShift;

  Candidate(ProbeThread thread, String target, int sampleShift) {
    this.thread = thread;
    this.target = target;
    this.sampleShift = sampleShift;
  }

  /**
   * One call in {@code 2^sampleShift} is timed (all are counted): candidates called hundreds or
   * thousands of times per frame or tick, where a timer pair on every call would cost more than 2%
   * of the frame or tick (or more than the work itself, for {@code getRenderer}).
   */
  int sampleShift() {
    return sampleShift;
  }

  ProbeThread thread() {
    return thread;
  }

  String target() {
    return target;
  }

  /** Sampling shifts: every call, one in 4, one in 8, one in 32. */
  private static final class Sampling {
    static final int EVERY_CALL = 0;
    static final int ONE_IN_4 = 2;
    static final int ONE_IN_8 = 3;
    static final int ONE_IN_32 = 5;
  }

  /** The only thread that runs a candidate's probes; each has its own accumulators. */
  enum ProbeThread {
    RENDER,
    SERVER
  }
}

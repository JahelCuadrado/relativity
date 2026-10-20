package com.einsteinmods.relativity.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Saves still running in the background, at most one per world folder. Thread-safe: saves are added
 * on the render thread, finished on their own thread and queried from any thread.
 *
 * <p>A finished save is dropped as soon as it is noticed (by {@link #remove}, {@link #pendingFor}
 * or {@link #pending}), so it never keeps its world in memory longer than needed.
 *
 * @param <K> world folder key
 * @param <S> one running save
 */
public final class PendingSaves<K, S> {
  private final ConcurrentMap<K, S> savesByWorld = new ConcurrentHashMap<>();
  private final Predicate<? super S> isFinished;

  /**
   * @param isFinished true once a save is complete and its world folder is free again
   */
  public PendingSaves(Predicate<? super S> isFinished) {
    this.isFinished = isFinished;
  }

  /**
   * Tracks {@code save} for {@code world}. A previous save of the same world is replaced: it is
   * already finished, since a world folder can only be reopened once its save is done.
   */
  public void add(K world, S save) {
    savesByWorld.put(world, save);
  }

  /** Forgets {@code save}, wherever it is tracked. */
  public void remove(S save) {
    savesByWorld.values().remove(save);
  }

  /** Cheap check before any lookup: true when no save is tracked at all. */
  public boolean isEmpty() {
    return savesByWorld.isEmpty();
  }

  /** The unfinished save of {@code world}, or null when its folder is free. */
  public @Nullable S pendingFor(K world) {
    S save = savesByWorld.get(world);
    if (save == null || !finished(world, save)) {
      return save;
    }
    return null;
  }

  /** Every unfinished save, in no particular order. */
  public List<S> pending() {
    List<S> unfinished = new ArrayList<>(savesByWorld.size());
    for (Map.Entry<K, S> entry : savesByWorld.entrySet()) {
      if (!finished(entry.getKey(), entry.getValue())) {
        unfinished.add(entry.getValue());
      }
    }
    return unfinished;
  }

  private boolean finished(K world, S save) {
    if (!isFinished.test(save)) {
      return false;
    }
    savesByWorld.remove(world, save);
    return true;
  }
}

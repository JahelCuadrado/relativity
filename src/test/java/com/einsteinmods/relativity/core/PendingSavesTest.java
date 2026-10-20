package com.einsteinmods.relativity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PendingSavesTest {
  private static final String WORLD = "New World";
  private static final String OTHER_WORLD = "Other World";

  private final PendingSaves<String, FakeSave> saves = new PendingSaves<>(FakeSave::isFinished);

  @Test
  void nothingIsPendingAtFirst() {
    assertTrue(saves.isEmpty());
    assertNull(saves.pendingFor(WORLD));
    assertTrue(saves.pending().isEmpty());
  }

  @Test
  void aRunningSaveMakesItsWorldWait() {
    FakeSave save = new FakeSave();
    saves.add(WORLD, save);

    assertSame(save, saves.pendingFor(WORLD));
    assertEquals(List.of(save), saves.pending());
  }

  @Test
  void otherWorldsDoNotWait() {
    saves.add(WORLD, new FakeSave());

    assertNull(saves.pendingFor(OTHER_WORLD));
  }

  @Test
  void aFinishedSaveIsDroppedWhenQueried() {
    FakeSave save = new FakeSave();
    saves.add(WORLD, save);
    save.finished = true;

    assertNull(saves.pendingFor(WORLD));
    assertTrue(saves.isEmpty());
  }

  @Test
  void pendingListsOnlyRunningSavesAndDropsFinishedOnes() {
    FakeSave running = new FakeSave();
    FakeSave done = new FakeSave();
    done.finished = true;
    saves.add(WORLD, running);
    saves.add(OTHER_WORLD, done);

    assertEquals(List.of(running), saves.pending());
    assertNull(saves.pendingFor(OTHER_WORLD));
    assertSame(running, saves.pendingFor(WORLD));
  }

  @Test
  void removeForgetsTheSave() {
    FakeSave save = new FakeSave();
    saves.add(WORLD, save);

    saves.remove(save);

    assertTrue(saves.isEmpty());
    assertNull(saves.pendingFor(WORLD));
  }

  @Test
  void removingAnUntrackedSaveKeepsTheOthers() {
    FakeSave save = new FakeSave();
    saves.add(WORLD, save);

    saves.remove(new FakeSave());

    assertSame(save, saves.pendingFor(WORLD));
  }

  @Test
  void aNewSaveOfTheSameWorldReplacesTheOldOne() {
    FakeSave first = new FakeSave();
    first.finished = true;
    FakeSave second = new FakeSave();
    saves.add(WORLD, first);
    saves.add(WORLD, second);

    assertSame(second, saves.pendingFor(WORLD));
    assertEquals(List.of(second), saves.pending());
  }

  /** A save the test finishes by hand. */
  private static final class FakeSave {
    private boolean finished;

    private boolean isFinished() {
      return finished;
    }
  }
}

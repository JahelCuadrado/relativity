package com.einsteinmods.relativity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PoseChangeTest {
  private static final int TRANSLATION_X = 12;
  private static final int TRANSLATION_Z = 14;
  private static final int LAST_ELEMENT = 15;

  private static float[] sample() {
    float[] matrix = new float[PoseChange.MATRIX_ELEMENTS];
    for (int element = 0; element < matrix.length; element++) {
      matrix[element] = element * 0.25F - 1.0F;
    }
    return matrix;
  }

  @Test
  void identicalBitsAreSame() {
    assertEquals(PoseChange.SAME, PoseChange.between(sample(), sample()));
  }

  @Test
  void onlyTranslationColumnIsTranslationOnly() {
    for (int element = TRANSLATION_X; element <= TRANSLATION_Z; element++) {
      float[] moved = sample();
      moved[element] += 1.0F;
      assertEquals(PoseChange.TRANSLATION_ONLY, PoseChange.between(sample(), moved));
    }
  }

  @Test
  void anyOtherElementIsOther() {
    for (int element = 0; element < PoseChange.MATRIX_ELEMENTS; element++) {
      if (element >= TRANSLATION_X && element <= TRANSLATION_Z) {
        continue;
      }
      float[] changed = sample();
      changed[element] = Math.nextUp(changed[element]);
      changed[TRANSLATION_X] += 1.0F;
      assertEquals(PoseChange.OTHER, PoseChange.between(sample(), changed), "element " + element);
    }
  }

  @Test
  void signedZeroIsADifference() {
    float[] positive = sample();
    float[] negative = sample();
    positive[LAST_ELEMENT] = 0.0F;
    negative[LAST_ELEMENT] = -0.0F;
    assertEquals(PoseChange.OTHER, PoseChange.between(positive, negative));
    assertFalse(PoseChange.sameBits(positive, negative, PoseChange.MATRIX_ELEMENTS));
    assertTrue(PoseChange.sameBits(positive, sample(), LAST_ELEMENT));
  }
}

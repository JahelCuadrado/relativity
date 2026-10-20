package com.einsteinmods.relativity.core;

/**
 * How a 4x4 column-major pose matrix differs from a cached one, compared bit for bit (so {@code
 * -0.0 != 0.0} and every NaN payload counts).
 */
public enum PoseChange {
  /** Every element has the same bits. */
  SAME,
  /** Only the translation column (elements 12, 13, 14) differs. */
  TRANSLATION_ONLY,
  /** Any other element differs. */
  OTHER;

  /** Elements of a 4x4 matrix. */
  public static final int MATRIX_ELEMENTS = 16;

  private static final int TRANSLATION_X = 12;
  private static final int TRANSLATION_Y = 13;
  private static final int TRANSLATION_Z = 14;

  /** Compares two column-major 4x4 matrices of {@link #MATRIX_ELEMENTS} floats. */
  public static PoseChange between(float[] cached, float[] current) {
    for (int element = 0; element < MATRIX_ELEMENTS; element++) {
      if (element != TRANSLATION_X
          && element != TRANSLATION_Y
          && element != TRANSLATION_Z
          && Float.floatToRawIntBits(cached[element])
              != Float.floatToRawIntBits(current[element])) {
        return OTHER;
      }
    }
    return sameBits(cached[TRANSLATION_X], current[TRANSLATION_X])
            && sameBits(cached[TRANSLATION_Y], current[TRANSLATION_Y])
            && sameBits(cached[TRANSLATION_Z], current[TRANSLATION_Z])
        ? SAME
        : TRANSLATION_ONLY;
  }

  /** True when both arrays hold the same bits in their first {@code length} elements. */
  public static boolean sameBits(float[] cached, float[] current, int length) {
    for (int element = 0; element < length; element++) {
      if (!sameBits(cached[element], current[element])) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameBits(float cached, float current) {
    return Float.floatToRawIntBits(cached) == Float.floatToRawIntBits(current);
  }
}

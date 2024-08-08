/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.battery.metrics.core;

import static org.assertj.core.api.Java6Assertions.assertThat;

import com.facebook.infer.annotation.Nullsafe;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Utilities for applying reflection on {@link com.facebook.battery.metrics.core.SystemMetrics}
 * classes.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
public class MetricsUtil {
  public static <T> T createInitializedInstance(Class<T> clazz) throws Exception {
    T t = clazz.newInstance();
    int i = 0;
    for (Field field : clazz.getFields()) {
      if (isNumericField(field)) {
        field.set(t, ++i);
      }
    }
    return t;
  }

  // Note: rename to isModifiableNumericField after landing dependent diffs
  public static boolean isNumericField(Field field) {
    Class fieldClass = field.getType();
    return !Modifier.isFinal(field.getModifiers())
        && (fieldClass.isAssignableFrom(int.class)
            || fieldClass.isAssignableFrom(float.class)
            || fieldClass.isAssignableFrom(long.class)
            || fieldClass.isAssignableFrom(double.class));
  }

  /** Handles casting the integer to the expected values, making it easy to check. */
  public static <T> void testValue(T t, Field field, int value) throws Exception {
    Class clazz = field.getType();
    Object typedValue = null;
    if (clazz == int.class) {
      typedValue = value;
    } else if (clazz == float.class) {
      typedValue = (float) value;
    } else if (clazz == double.class) {
      typedValue = (double) value;
    } else if (clazz == long.class) {
      typedValue = (long) value;
    }

    // NULLSAFE_FIXME[Parameter Not Nullable, Not Vetted Third-Party]
    assertThat(field.get(t)).isEqualTo(typedValue);
  }
}

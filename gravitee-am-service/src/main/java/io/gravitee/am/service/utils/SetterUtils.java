/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.service.utils;

import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class SetterUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetterUtils.class);

    // These values are used to manage primitive types. There's 8 primitive in Java.
    private static boolean DEFAULT_BOOLEAN;
    private static byte DEFAULT_BYTE;
    private static char DEFAULT_CHAR;
    private static short DEFAULT_SHORT;
    private static int DEFAULT_INT;
    private static long DEFAULT_LONG;
    private static float DEFAULT_FLOAT;
    private static double DEFAULT_DOUBLE;

    /**
     * Safe setter, apply setter only if Optional is not null.
     * If Optional is empty, set field to null, else apply the value.
     * @param setter Consumer setter method.
     * @param value Optional value
     * @param <T> value class
     */
    public static <T> void safeSet(final Consumer<T> setter, final Optional<T> value) {
        if (value != null) {
            setter.accept(value.orElse(null));
        }
    }

    /**
     * Safe setter, apply setter only if Optional is not null.
     * If Optional is empty, set field to null, else apply the value.
     * @param setter Consumer setter method.
     * @param value Optional value
     * @param <T> value class
     */
    public static <T> void safeSet(final Consumer<T> setter, final Optional<T> value, final Class primitive) {
        if (value != null) {
            if (value.isPresent()) {
                setter.accept(value.get());
            } else {
                setter.accept((T) getDefaultValue(primitive));
            }
        }
    }

    private static Object getDefaultValue(Class primitive) {
        if (primitive.equals(boolean.class)) {
            return DEFAULT_BOOLEAN;
        } else if (primitive.equals(byte.class)) {
            return DEFAULT_BYTE;
        } else if (primitive.equals(char.class)) {
            return DEFAULT_CHAR;
        } else if (primitive.equals(short.class)) {
            return DEFAULT_SHORT;
        } else if (primitive.equals(int.class)) {
            return DEFAULT_INT;
        } else if (primitive.equals(long.class)) {
            return DEFAULT_LONG;
        } else if (primitive.equals(float.class)) {
            return DEFAULT_FLOAT;
        } else if (primitive.equals(double.class)) {
            return DEFAULT_DOUBLE;
        }

        LOGGER.warn("should never happen except if class is not a primitive:" + primitive.isPrimitive());
        return null;
    }

    public static <T> void set(final Consumer<T> setter, final Optional<T> value) {
        if (value == null) {
            setter.accept(null);
        } else {
            setter.accept(value.orElse(null));
        }
    }
}

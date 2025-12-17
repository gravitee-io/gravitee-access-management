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
package io.gravitee.am.repository.common;

import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic enum parsing helpers to safely handle values coming from future versions.
 * Unknown values are logged once per value; discards are logged once per entity.
 */
public final class EnumParsingUtils {

    private static final Set<String> LOGGED_UNKNOWN_VALUES = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_DISCARDED_IDS = ConcurrentHashMap.newKeySet();

    private EnumParsingUtils() {
    }

    /**
     * Safely parse an enum value.
     * Returns null if the raw value is null OR if the value is unknown.
     */
    public static <E extends Enum<E>> E safeValueOf(Class<E> enumClass, String rawValue, String entityId, String fieldName, Logger logger) {
        if (rawValue == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, rawValue);
        } catch (IllegalArgumentException iae) {
            if (logger != null) {
                String key = enumClass.getSimpleName() + ":" + rawValue;
                if (LOGGED_UNKNOWN_VALUES.add(key)) {
                    logger.warn("Unknown {} value '{}' for {}.{} - likely from a newer version.", enumClass.getSimpleName(), rawValue, entityId, fieldName);
                }
            }
            return null;
        }
    }

    /**
     * @return true ONLY if rawValue was present but parsing failed.
     */
    public static boolean isUnknown(String rawValue, Enum<?> parsedValue) {
        return rawValue != null && parsedValue == null;
    }

    /**
     * Helper to log the discard action only once per Entity ID.
     */
    public static void logDiscard(String entityId, Logger logger, String reason) {
        if (logger == null) {
            return;
        }
        if (LOGGED_DISCARDED_IDS.add(entityId)) {
            logger.warn("Discarding entity id={} - {}. Future discards for this ID logged at DEBUG.", entityId, reason);
        } else {
            logger.debug("Discarding entity id={} - {}", entityId, reason);
        }
    }
}

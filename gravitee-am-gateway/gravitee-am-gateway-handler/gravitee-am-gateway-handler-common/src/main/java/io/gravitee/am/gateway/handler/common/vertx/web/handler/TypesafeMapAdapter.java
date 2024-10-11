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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import java.util.Objects;
import java.util.function.Function;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNullElse;

/**
 * Simple wrapper for working with things that look like Map&lt;String, Object&gt; in a more typesafe way
 */
abstract class TypesafeMapAdapter {

    private final Function<String, Object> rawGetter;

    TypesafeMapAdapter(Function<String, Object> rawGetter) {
        this.rawGetter = Objects.requireNonNull(rawGetter, "rawGetter");
    }


    protected final <T> T typesafeGet(String key, Class<T> type) {
        var rawValue = rawGetter.apply(key);
        if (rawValue == null) {
            return null;
        }
        if (type.isInstance(rawValue)) {
            return type.cast(rawValue);
        } else {
            throw new IllegalStateException("value of '%s' is not of expected type '%s' (got '%s')".formatted(key, type.getName(), rawValue.getClass().getName()));
        }
    }

    protected final boolean getBoolean(String key) {
        var rawValue = rawGetter.apply(key);
        return TRUE.equals(rawValue);
    }

    protected final int getInt(String key) {
        var rawValue = rawGetter.apply(key);
        return requireNonNullElse((Integer) rawValue, 0);
    }
}

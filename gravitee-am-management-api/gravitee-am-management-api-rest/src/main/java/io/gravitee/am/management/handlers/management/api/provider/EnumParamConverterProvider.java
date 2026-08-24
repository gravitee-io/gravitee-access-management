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
package io.gravitee.am.management.handlers.management.api.provider;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Locale;

/**
 * Without this, Jersey reports an unrecognised enum parameter as a 404, because a failed parameter
 * conversion is a {@code NotFoundException} by default. It also accepts the lower case spelling the
 * API itself serialises enums with, so a value read back from a response can be passed straight
 * into a query parameter.
 *
 * <p>This claims every enum, so an enum that needs its own parsing must not rely on the
 * {@code fromString} or {@code valueOf} method JAX-RS would otherwise call.
 */
public class EnumParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (!rawType.isEnum()) {
            return null;
        }
        final String name = parameterName(annotations, rawType);
        return (ParamConverter<T>) new ParamConverter<Enum>() {

            @Override
            public Enum fromString(String value) {
                if (value == null || value.isBlank()) {
                    return null;
                }
                try {
                    return Enum.valueOf((Class<Enum>) rawType, value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid " + name + ": " + value
                            + ". Available values: " + Arrays.toString(rawType.getEnumConstants()));
                }
            }

            @Override
            public String toString(Enum value) {
                return value == null ? null : value.name();
            }
        };
    }

    /**
     * The declared parameter name reads better in the error than the enum class name, which is
     * often a nested {@code Type}. Falls back to the class name for an unannotated parameter.
     */
    private static String parameterName(Annotation[] annotations, Class<?> rawType) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof QueryParam queryParam) {
                return queryParam.value();
            }
            if (annotation instanceof PathParam pathParam) {
                return pathParam.value();
            }
        }
        return rawType.getSimpleName();
    }
}

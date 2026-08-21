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

import io.gravitee.am.model.ProtectedResource;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Without this, Jersey reports an unrecognised {@code type} query parameter as a 404, because a
 * failed parameter conversion is a {@code NotFoundException} by default.
 */
public class ProtectedResourceTypeParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType != ProtectedResource.Type.class) {
            return null;
        }
        return (ParamConverter<T>) new ParamConverter<ProtectedResource.Type>() {

            @Override
            public ProtectedResource.Type fromString(String value) {
                if (value == null || value.isBlank()) {
                    return null;
                }
                try {
                    return ProtectedResource.Type.valueOf(value.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid protected resource type: " + value
                            + ". Available types: " + Arrays.toString(ProtectedResource.Type.values()));
                }
            }

            @Override
            public String toString(ProtectedResource.Type type) {
                return type == null ? null : type.name();
            }
        };
    }
}

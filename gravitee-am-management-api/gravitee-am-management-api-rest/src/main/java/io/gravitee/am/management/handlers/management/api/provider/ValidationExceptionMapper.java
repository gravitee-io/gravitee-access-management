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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.management.handlers.management.api.model.ErrorEntity;
import io.gravitee.common.http.HttpStatusCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.validator.internal.engine.path.PathImpl;

import java.lang.reflect.Field;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {

    @Override
    public Response toResponse(ValidationException e) {
        if (e instanceof ConstraintViolationException constraintViolationException) {
            return buildResponse(
                    "[" +
                            constraintViolationException
                                    .getConstraintViolations()
                                    .stream()
                                    .map(constraint -> {
                                        Object value = constraint.getInvalidValue() == null ? resolveFieldName(constraint) : constraint.getInvalidValue();
                                        return value + ": " + constraint.getMessage();
                                    })
                                    .collect(Collectors.joining(","))
                        +
                    "]");
        } else {
            return buildResponse(e.getMessage());
        }
    }

    private String resolveFieldName(ConstraintViolation<?> constraint) {
        String leafName = ((PathImpl) constraint.getPropertyPath()).getLeafNode().asString();
        try {
            Field field = findField(constraint.getRootBeanClass(), leafName);
            if (field == null) return leafName;
            JsonProperty jp = field.getAnnotation(JsonProperty.class);
            if (jp != null && !jp.value().isEmpty()) return jp.value();
        } catch (Exception ignored) {
        }
        return leafName;
    }

    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private Response buildResponse(String message) {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorEntity(message, HttpStatusCode.BAD_REQUEST_400))
                .build();
    }
}

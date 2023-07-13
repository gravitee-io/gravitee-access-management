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

import io.gravitee.am.management.handlers.management.api.model.ErrorEntity;
import io.gravitee.common.http.HttpStatusCode;
import org.hibernate.validator.internal.engine.path.PathImpl;

import javax.validation.ConstraintViolationException;
import javax.validation.ValidationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {

    @Override
    public Response toResponse(ValidationException e) {
        if (e instanceof ConstraintViolationException) {
            ConstraintViolationException constraintViolationException = (ConstraintViolationException) e;
            return buildResponse(
                    "[" +
                            constraintViolationException
                                    .getConstraintViolations()
                                    .stream()
                                    .map(constraint -> {
                                        Object value = constraint.getInvalidValue() == null ? ((PathImpl) constraint.getPropertyPath()).getLeafNode().asString() : constraint.getInvalidValue();
                                        return value + ": " + constraint.getMessage();
                                    })
                                    .collect(Collectors.joining(","))
                        +
                    "]");
        } else {
            return buildResponse(e.getMessage());
        }
    }

    private Response buildResponse(String message) {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorEntity(message, HttpStatusCode.BAD_REQUEST_400))
                .build();
    }
}

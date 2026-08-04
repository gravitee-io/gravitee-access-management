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
package io.gravitee.am.management.handlers.internalapi.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.node.management.http.endpoint.ManagementEndpoint;
import io.vertx.ext.web.RoutingContext;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * Response plumbing shared by the technical API endpoints: JSON encoding and the mapping from a
 * {@link AbstractManagementException} to its HTTP status.
 *
 * @author GraviteeSource Team
 */
@CustomLog
@RequiredArgsConstructor
abstract class AbstractInternalApiEndpoint implements ManagementEndpoint {

    protected final ObjectMapper objectMapper;

    protected void respond(RoutingContext context, int statusCode, Object body) {
        try {
            context.response()
                    .setStatusCode(statusCode)
                    .putHeader("content-type", MediaType.APPLICATION_JSON)
                    .end(objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            log.error("Unable to serialize the response", e);
            context.response().setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500).end();
        }
    }

    protected void respondError(RoutingContext context, int statusCode, String message) {
        respond(context, statusCode, Map.of("message", message));
    }

    /**
     * Management exceptions carry a caller-safe message; anything else is reduced to a 500.
     */
    protected void respondFailure(RoutingContext context, Throwable throwable, String fallbackMessage) {
        if (throwable instanceof AbstractManagementException managementException) {
            respondError(context, managementException.getHttpStatusCode(), managementException.getMessage());
        } else {
            log.error(fallbackMessage, throwable);
            respondError(context, HttpStatusCode.INTERNAL_SERVER_ERROR_500, fallbackMessage);
        }
    }
}

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
package io.gravitee.am.gateway.handler.root.resources.endpoint.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.root.resources.endpoint.agent.model.AccessCheckRequest;
import io.gravitee.am.gateway.handler.root.resources.endpoint.agent.model.AccessCheckResponse;
import io.gravitee.am.service.OpenFGAService;
import io.gravitee.am.service.model.OpenFGATuple;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author GraviteeSource Team
 */
//TODO: get list of resources from OpenFGA
public class ResourcesEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(ResourcesEndpoint.class);

    private final ObjectMapper objectMapper;
    private final OpenFGAService openFGAService;

    public ResourcesEndpoint(ObjectMapper objectMapper, OpenFGAService openFGAService) {
        this.objectMapper = objectMapper;
        this.openFGAService = openFGAService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            final String bodyString = routingContext.getBodyAsString();
            if (bodyString == null || bodyString.trim().isEmpty()) {
                sendError(routingContext, HttpStatusCode.BAD_REQUEST_400, "Request body is required");
                return;
            }

            final AccessCheckRequest request = objectMapper.readValue(bodyString, AccessCheckRequest.class);

            if (request.getUser() == null || request.getUser().trim().isEmpty()) {
                sendError(routingContext, HttpStatusCode.BAD_REQUEST_400, "user is required");
                return;
            }
            if (request.getRelation() == null || request.getRelation().trim().isEmpty()) {
                sendError(routingContext, HttpStatusCode.BAD_REQUEST_400, "relation is required");
                return;
            }
            if (request.getObject() == null || request.getObject().trim().isEmpty()) {
                sendError(routingContext, HttpStatusCode.BAD_REQUEST_400, "object is required");
                return;
            }

            // Get the OpenFGA resource
            if (openFGAService == null) {
                sendResponse(routingContext, AccessCheckResponse.deny("resource_not_found"));
                return;
            }

            openFGAService.connect("http://localhost:8080").blockingGet();
            openFGAService.checkPermission(null, new OpenFGATuple(request.getUser(), request.getRelation(), request.getObject())).map(result -> {
                if (result.isAllowed()) {
                    return AccessCheckResponse.ok();
                } else {
                    return AccessCheckResponse.deny("tuple_missing");
                }
            }).onErrorReturn(throwable -> {
                        logger.error("Error checking permission for user {} on object {} with relation {}",
                                request.getUser(), request.getObject(), request.getRelation(), throwable);
                        return AccessCheckResponse.deny("upstream_error");
                    })
                    .subscribe(
                            response -> sendResponse(routingContext, response),
                            error -> {
                                logger.error("Unexpected error during permission check", error);
                                sendResponse(routingContext, AccessCheckResponse.deny("upstream_error"));
                            }
                    );


        } catch (Exception e) {
            logger.error("Error processing permission check request", e);
            sendError(routingContext, HttpStatusCode.BAD_REQUEST_400, "Invalid request format");
        }
    }

    private void sendResponse(RoutingContext routingContext, AccessCheckResponse response) {
        try {
            routingContext.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .setStatusCode(HttpStatusCode.OK_200)
                    .end(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            logger.error("Error sending response", e);
            sendError(routingContext, HttpStatusCode.INTERNAL_SERVER_ERROR_500, "Internal server error");
        }
    }

    private void sendError(RoutingContext routingContext, int statusCode, String message) {
        try {
            final AccessCheckResponse errorResponse = AccessCheckResponse.deny(message);
            routingContext.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .setStatusCode(statusCode)
                    .end(objectMapper.writeValueAsString(errorResponse));
        } catch (Exception e) {
            logger.error("Error sending error response", e);
            routingContext.response()
                    .setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500)
                    .end("{\"allowed\":false,\"reason\":\"internal_error\"}");
        }
    }
}
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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.validation;

import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.resources.request.AuthorizationRequestFactory;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.common.utils.ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY;

/**
 * Handler for validating resource parameters in OAuth2 authorization requests according to RFC 8707.
 * This handler validates that requested resources are recognized by the authorization server.
 *
 * @author GraviteeSource Team
 */
@Slf4j
public class AuthorizationRequestResourceValidationHandler implements Handler<RoutingContext> {

    private final ResourceValidationService resourceValidationService;
    private final AuthorizationRequestFactory authorizationRequestFactory = new AuthorizationRequestFactory();

    public AuthorizationRequestResourceValidationHandler(ResourceValidationService resourceValidationService) {
        this.resourceValidationService = resourceValidationService;
    }

    @Override
    public void handle(RoutingContext context) {
        // Get or create authorization request
        final AuthorizationRequest authorizationRequest = resolveAuthorizationRequest(context);

        // Validate resources
        resourceValidationService.validate(authorizationRequest)
                .subscribe(
                        () -> {
                            log.debug("Resource validation successful for authorization request");
                            context.next();
                        },
                        error -> {
                            log.debug("Resource validation failed for authorization request: {}", error.getMessage());
                            context.fail(error);
                        }
                );
    }

    private AuthorizationRequest resolveAuthorizationRequest(RoutingContext context) {
        AuthorizationRequest authorizationRequest = context.get(AUTHORIZATION_REQUEST_CONTEXT_KEY);
        if (authorizationRequest != null) {
            return authorizationRequest;
        }
        // Create authorization request from context if not already present
        return authorizationRequestFactory.create(context);
    }
}

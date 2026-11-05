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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailsValidator;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.common.oauth2.Parameters.AUTHORIZATION_DETAILS;
import static io.gravitee.am.common.utils.ConstantKeys.AUTHORIZATION_DETAILS_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.AUTH_FLOW_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.REQUEST_OBJECT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.REQUEST_PARAMETERS_KEY;

/**
 * Validates the {@code authorization_details} query parameter and keeps the result for the authorization request.
 * Skips requests that use a request object (JAR or PAR).
 *
 * See <a href="https://datatracker.ietf.org/doc/html/rfc9396#section-3">RFC 9396 Section 3</a>
 */
public class AuthorizationRequestParseAuthorizationDetailsHandler implements Handler<RoutingContext> {

    private final AuthorizationDetailsValidator authorizationDetailsValidator;

    public AuthorizationRequestParseAuthorizationDetailsHandler(AuthorizationDetailsValidator authorizationDetailsValidator) {
        this.authorizationDetailsValidator = authorizationDetailsValidator;
    }

    @Override
    public void handle(RoutingContext context) {
        final String authorizationDetails = context.request().getParam(AUTHORIZATION_DETAILS);
        if (authorizationDetails == null || usesRequestObject(context)) {
            context.next();
            return;
        }
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        try {
            context.put(AUTHORIZATION_DETAILS_CONTEXT_KEY, authorizationDetailsValidator.validate(authorizationDetails, client));
        } catch (RuntimeException e) {
            context.fail(e);
            return;
        }
        context.next();
    }

    private static boolean usesRequestObject(RoutingContext context) {
        if (context.get(REQUEST_OBJECT_KEY) != null) {
            return true;
        }
        AuthenticationFlowContext authFlowContext = context.get(AUTH_FLOW_CONTEXT_KEY);
        return authFlowContext != null && authFlowContext.getData() != null && authFlowContext.getData().containsKey(REQUEST_PARAMETERS_KEY);
    }
}

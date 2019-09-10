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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.approval;

import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserApprovalSubmissionEndpoint implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserApprovalSubmissionEndpoint.class);
    private static final String AUTHORIZATION_REQUEST_CONTEXT_KEY = "authorizationRequest";
    private Domain domain;

    public UserApprovalSubmissionEndpoint(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        // retrieve authorization request
        AuthorizationRequest authorizationRequest = context.get(AUTHORIZATION_REQUEST_CONTEXT_KEY);

        // user denied access
        if (!authorizationRequest.isApproved()) {
            context.fail(new AccessDeniedException("User denied access"));
            return;
        }

        try {
            // user approved access, replay authorization request
            final String authorizationRequestUrl = UriBuilderRequest.resolveProxyRequest(context.request(), "/" + domain.getPath() + "/oauth/authorize", authorizationRequest.parameters().toSingleValueMap());
            doRedirect(context.response(), authorizationRequestUrl);
        } catch (Exception e) {
            LOGGER.error("An error occurs while handling authorization approval request", e);
            context.fail(503);
        }
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }
}

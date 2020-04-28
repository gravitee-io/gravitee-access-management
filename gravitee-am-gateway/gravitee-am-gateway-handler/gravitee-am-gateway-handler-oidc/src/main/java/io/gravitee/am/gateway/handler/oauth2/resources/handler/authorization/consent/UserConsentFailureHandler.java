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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent;

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentFailureHandler implements Handler<RoutingContext> {
    private static final Logger logger = LoggerFactory.getLogger(UserConsentFailureHandler.class);
    private static final String CLIENT_CONTEXT_KEY = "client";
    private Domain domain;

    public UserConsentFailureHandler(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        if (context.failed()) {
            // logout the user
            // but keep the session intact with the original OAuth 2.0 authorization request in order to replay the whole login process
            context.clearUser();

            // handle exception
            Throwable throwable = context.failure();
            if (throwable instanceof PolicyChainException) {
                PolicyChainException policyChainException = (PolicyChainException) throwable;
                handleException(context, policyChainException.key(), policyChainException.getMessage());
            } else {
                handleException(context, "internal_server_error", "Unexpected error");
            }
        }
    }

    private void handleException(RoutingContext context, String errorCode, String errorDescription) {
        try {
            Map<String, String> params = new LinkedHashMap<>();

            // retrieve client
            Client client = context.get(CLIENT_CONTEXT_KEY);
            if (client != null) {
                params.put(Parameters.CLIENT_ID, client.getClientId());
            }

            // add error messages
            params.put("error", "user_consent_failed");
            if (errorCode != null) {
                params.put("error_code", errorCode);
            }
            if (errorDescription != null) {
                params.put("error_description", errorDescription);
            }

            // go back to login page
            String uri = UriBuilderRequest.resolveProxyRequest(context.request(), "/" + domain.getPath() + "/login", params);
            doRedirect(context.response(), uri);
        } catch (Exception ex) {
            logger.error("An error occurs while redirecting to {}", context.request().absoluteURI(), ex);
            context.fail(503);
        }
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }
}

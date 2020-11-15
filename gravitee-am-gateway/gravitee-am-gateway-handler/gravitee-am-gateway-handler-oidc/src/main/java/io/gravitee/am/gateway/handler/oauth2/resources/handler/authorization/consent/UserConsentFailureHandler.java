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

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentFailureHandler implements Handler<RoutingContext> {
    private static final Logger logger = LoggerFactory.getLogger(UserConsentFailureHandler.class);

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
            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());

            // add error messages
            queryParams.set(ConstantKeys.ERROR_PARAM_KEY, "user_consent_failed");
            if (errorCode != null) {
                queryParams.set(ConstantKeys.ERROR_CODE_PARAM_KEY, errorCode);
            }
            if (errorDescription != null) {
                queryParams.set(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
            }

            // go back to login page
            String uri = UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/login", queryParams);
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

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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import com.google.common.net.HttpHeaders;
import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginFailureHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginFailureHandler.class);

    private AuthenticationFlowContextService authenticationFlowContextService;

    public LoginFailureHandler(AuthenticationFlowContextService authenticationFlowContextService) {
        this.authenticationFlowContextService = authenticationFlowContextService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            Throwable throwable = routingContext.failure();
            if (throwable instanceof PolicyChainException) {
                PolicyChainException policyChainException = (PolicyChainException) throwable;
                handleException(routingContext, policyChainException.key(), policyChainException.getMessage());
            } else if (throwable instanceof AuthenticationException) {
                handleException(routingContext, "invalid_user", "Invalid or unknown user");
            } else {
                handleException(routingContext, "internal_server_error", "Unexpected error");
            }
        }
    }

    private void handleException(RoutingContext context, String errorCode, String errorDescription) {
        final HttpServerRequest req = context.request();
        final HttpServerResponse resp = context.response();

        // logout user if exists
        if (context.user() != null) {
            // clear AuthenticationFlowContext. data of this context have a TTL so we can fire and forget in case on error.
            authenticationFlowContextService.clearContext(context.session().get(ConstantKeys.TRANSACTION_ID_KEY))
                    .doOnError((error) -> LOGGER.info("Deletion of some authentication flow data fails '{}'", error.getMessage()))
                    .subscribe();

            context.clearUser();
            context.session().destroy();
        }

        // redirect to the login page with error message
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(req);
        // add error messages
        queryParams.set(ConstantKeys.ERROR_PARAM_KEY, "login_failed");
        if (errorCode != null) {
            queryParams.set(ConstantKeys.ERROR_CODE_PARAM_KEY, errorCode);
        }
        if (errorDescription != null) {
            queryParams.set(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
        }
        String uri = UriBuilderRequest.resolveProxyRequest(req, req.path(), queryParams, true);
        doRedirect(resp, uri);
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response
                .putHeader(HttpHeaders.LOCATION, url)
                .setStatusCode(302)
                .end();
    }
}

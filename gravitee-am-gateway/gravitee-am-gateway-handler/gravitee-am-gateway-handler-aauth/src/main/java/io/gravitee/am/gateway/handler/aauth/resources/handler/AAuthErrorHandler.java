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
package io.gravitee.am.gateway.handler.aauth.resources.handler;

import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Failure handler for AAUTH browser-facing routes (/interact, /consent).
 * Redirects to the /error page, following the same pattern as
 * RootProvider's ErrorHandler.
 *
 * @author GraviteeSource Team
 */
public class AAuthErrorHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AAuthErrorHandler.class);
    private static final String PATH_ERROR = "/error";

    @Override
    public void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            Throwable throwable = routingContext.failure();
            if (throwable instanceof OAuth2Exception oAuth2Exception) {
                handleException(routingContext, oAuth2Exception.getOAuth2ErrorCode(), oAuth2Exception.getMessage());
            } else if (throwable instanceof PolicyChainException policyChainException) {
                handleException(routingContext, policyChainException.key(), policyChainException.getMessage());
            } else if (throwable instanceof HttpException httpException) {
                handleException(routingContext, String.valueOf(httpException.getStatusCode()), httpException.getPayload());
            } else if (throwable instanceof AuthenticationException authException) {
                handleException(routingContext, authException.getErrorCode(), authException.getMessage());
            } else {
                logger.error("An error occurred during AAUTH interaction", throwable);
                handleException(routingContext, "server_error", "An unexpected error has occurred");
            }
        }
    }

    private void handleException(RoutingContext routingContext, String error, String errorDetail) {
        String errorPageURL = routingContext.get(CONTEXT_PATH) + PATH_ERROR;

        try {
            MultiMap parameters = RequestUtils.getCleanedQueryParams(routingContext.request());
            parameters.set(ConstantKeys.ERROR_PARAM_KEY, error);
            StringBuilder toHash = new StringBuilder(error);
            if (errorDetail != null) {
                parameters.set(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDetail);
                toHash.append("$").append(errorDetail);
            }

            String errorHash = HashUtil.generateSHA256(toHash.toString());
            if (routingContext.session() != null) {
                routingContext.session().put(ERROR_HASH, errorHash);
            }

            String proxiedErrorPage = UriBuilderRequest.resolveProxyRequest(
                    routingContext.request(), errorPageURL, parameters, true);
            doRedirect(routingContext, proxiedErrorPage);
        } catch (Exception e) {
            logger.error("Unable to handle AAUTH error response", e);
            doRedirect(routingContext, errorPageURL);
        }
    }

    private void doRedirect(RoutingContext context, String url) {
        context.response()
                .putHeader(HttpHeaders.LOCATION, url)
                .setStatusCode(302)
                .end();
    }
}

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
package io.gravitee.am.gateway.handler.root.resources.handler.error;

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.vertx.ext.auth.webauthn.impl.attestation.AttestationException;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.TOKEN_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static java.util.Objects.isNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ErrorHandler extends AbstractErrorHandler {

    public ErrorHandler(String errorPage) {
        super(errorPage);
    }

    @Override
    protected void doHandle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            Throwable throwable = routingContext.failure();
            // management exception (resource not found, server error, ...)
            if (throwable instanceof AbstractManagementException) {
                AbstractManagementException technicalManagementException = (AbstractManagementException) throwable;
                handleException(routingContext, "technical_error", technicalManagementException.getMessage());
                // oauth2 exception (token invalid exception)
            } else if (throwable instanceof OAuth2Exception) {
                OAuth2Exception oAuth2Exception = (OAuth2Exception) throwable;
                handleException(routingContext, oAuth2Exception.getOAuth2ErrorCode(), oAuth2Exception.getMessage());
            } else if (throwable instanceof PolicyChainException) {
                PolicyChainException policyChainException = (PolicyChainException) throwable;
                handleException(routingContext, policyChainException.key(), policyChainException.getMessage());
            } else if (throwable instanceof HttpException) {
                HttpException httpStatusException = (HttpException) throwable;
                handleException(routingContext, httpStatusException.getMessage(), httpStatusException.getPayload());
            }  else if (throwable instanceof AttestationException) {
                handleException(routingContext, "technical_error", "Invalid WebAuthn attestation, make sure your device is compliant with the platform requirements");
            } else {
                logger.error("An exception occurs while handling incoming request", throwable);
                handleException(routingContext, "unexpected_error", "An unexpected error has occurred");
            }
        }
    }

    private void handleException(RoutingContext routingContext, String errorCode, String errorDetail) {

        String errorPageURL = routingContext.get(CONTEXT_PATH) + errorPage;

        try {
            final HttpServerRequest request = routingContext.request();
            // prepare query parameters
            MultiMap parameters = RequestUtils.getCleanedQueryParams(routingContext.request());
            // get client if exists
            Client client = routingContext.get(CLIENT_CONTEXT_KEY);
            if (client != null) {
                parameters.set(Parameters.CLIENT_ID, client.getClientId());
            } else if (request.getParam(Parameters.CLIENT_ID) != null) {
                parameters.set(Parameters.CLIENT_ID, (request.getParam(Parameters.CLIENT_ID)));
            }
            // append error information
            parameters.set(ConstantKeys.ERROR_PARAM_KEY, errorCode);
            if (errorDetail != null) {
                parameters.set(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDetail);
            }

            final String token = request.getParam(TOKEN_CONTEXT_KEY);
            if (isNull(parameters.get(TOKEN_CONTEXT_KEY)) && token != null) {
                parameters.set(TOKEN_CONTEXT_KEY, token);
            }

            // redirect
            String proxiedErrorPage = UriBuilderRequest.resolveProxyRequest(request, errorPageURL, parameters, true);
            doRedirect(routingContext.response(), proxiedErrorPage);
        } catch (Exception e) {
            logger.error("Unable to handle root error response", e);
            doRedirect(routingContext.response(), errorPageURL);
        }
    }

}

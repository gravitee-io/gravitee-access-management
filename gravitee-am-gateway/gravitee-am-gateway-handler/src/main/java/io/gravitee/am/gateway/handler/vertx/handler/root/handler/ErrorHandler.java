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
package io.gravitee.am.gateway.handler.vertx.handler.root.handler;

import io.gravitee.am.common.oauth2.exception.OAuth2Exception;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.utils.UriBuilder;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ErrorHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);
    private String errorPage;

    public ErrorHandler(String errorPage) {
        this.errorPage = errorPage;
    }

    @Override
    public void handle(RoutingContext routingContext) {
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
            } else {
                logger.error(throwable.getMessage(), throwable);
                if (routingContext.statusCode() != -1) {
                    routingContext
                            .response()
                            .setStatusCode(routingContext.statusCode())
                            .end();
                } else {
                    routingContext
                            .response()
                            .setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500)
                            .end();
                }
            }
        }
    }

    private void handleException(RoutingContext routingContext, String errorCode, String errorDetail) {
        try {
            String proxiedErrorPage = UriBuilderRequest.resolveProxyRequest(routingContext.request(),  errorPage, null);
            doRedirect(routingContext.response(), buildRedirectUri(proxiedErrorPage, errorCode, errorDetail));
        } catch (Exception e) {
            logger.error("Unable to handle root error response", e);
            doRedirect(routingContext.response(),  errorPage);
        }
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    private String buildRedirectUri(String redirectUri, String errorCode, String errorDetail) throws URISyntaxException {
        // prepare query
        Map<String, String> query = new LinkedHashMap<>();
        query.put("error", errorCode);
        if (errorDetail != null) {
            query.put("error_description", errorDetail);
        }

        return append(redirectUri, query);
    }

    private String append(String base, Map<String, String> query) throws URISyntaxException {
        // prepare final redirect uri
        UriBuilder template = UriBuilder.newInstance();

        // get URI from the redirect_uri parameter
        UriBuilder builder = UriBuilder.fromURIString(base);
        URI redirectUri = builder.build();

        // create final redirect uri
        template.scheme(redirectUri.getScheme())
                .host(redirectUri.getHost())
                .port(redirectUri.getPort())
                .userInfo(redirectUri.getUserInfo())
                .path(redirectUri.getPath());

        // append error parameters in "application/x-www-form-urlencoded" format
        query.forEach((k, v) -> template.addParameter(k, UriBuilder.encodeURIComponent(v)));

        return template.build().toString();
    }
}

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
package io.gravitee.am.gateway.handler.vertx.utils;

import io.gravitee.am.service.utils.UriBuilder;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Map;

/**
 * Handle proxy specific things such as resolving external url via X-Forwarded-* proxy headers
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UriBuilderRequest {

    private static final String X_FORWARDED_PREFIX = "X-Forwarded-Prefix";
    private static final Logger LOGGER = LoggerFactory.getLogger(UriBuilderRequest.class);

    public static String extractBasePath(final io.vertx.ext.web.RoutingContext context) {
        return extractBasePath(new io.vertx.reactivex.core.http.HttpServerRequest(context.request()));
    }

    public static String extractBasePath(final RoutingContext context) {
        return extractBasePath(context.request());
    }

    private static String extractBasePath(final HttpServerRequest request) {
        try {
            return UriBuilderRequest.resolveProxyRequest(request, "/", null);
        } catch (URISyntaxException e) {
            LOGGER.error("Unable to resolve OpenID Connect provider configuration endpoint", e);
        }
        return "/";
    }

    /**
     * Resolve proxy request
     * @param request original request
     * @param path request path
     * @param parameters request query params
     * @return request uri representation
     * @throws URISyntaxException
     */
    public static String resolveProxyRequest(final HttpServerRequest request, final String path, final Map<String, String> parameters) throws URISyntaxException {
        UriBuilder builder = UriBuilder.newInstance();

        // scheme
        String scheme = request.getHeader(HttpHeaders.X_FORWARDED_PROTO);
        if (scheme != null && !scheme.isEmpty()) {
            builder.scheme(scheme);
        } else {
            builder.scheme(request.scheme());
        }

        // host + port
        String host = request.getHeader(HttpHeaders.X_FORWARDED_HOST);
        if (host != null && !host.isEmpty()) {
            handleHost(builder, host);
        } else {
            handleHost(builder, request.host());
        }

        // handle forwarded path for redirect_uri query param
        String forwardedPath = request.getHeader(X_FORWARDED_PREFIX);
        if (forwardedPath != null && !forwardedPath.isEmpty()) {
            // remove trailing slash
            forwardedPath = forwardedPath.substring(0, forwardedPath.length() - (forwardedPath.endsWith("/") ? 1 : 0));
            builder.path(forwardedPath + path);
        } else {
            builder.path(path);
        }

        builder.parameters(parameters);
        return builder.build().toString();
    }

    private static void handleHost(UriBuilder builder, String host) {
        if (host.contains(":")) {
            // host contains both host and port
            String [] parts = host.split(":");
            builder.host(parts[0]);
            builder.port(Integer.valueOf(parts[1]));
        } else {
            builder.host(host);
        }
    }
}

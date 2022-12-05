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
package io.gravitee.am.gateway.handler.common.vertx.utils;

import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;

/**
 * Handle proxy specific things such as resolving external url via X-Forwarded-* proxy headers
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UriBuilderRequest {
    public static final Logger LOGGER = LoggerFactory.getLogger(UriBuilderRequest.class);

    public static final String CONTEXT_PATH = "contextPath";

    private static final String X_FORWARDED_PREFIX = "X-Forwarded-Prefix";

    public static String resolveProxyRequest(final RoutingContext context) {

        return resolveProxyRequest(context.request(), context.get(CONTEXT_PATH));
    }

    /**
     * Resolve proxy request
     * @param request original request
     * @param path request path
     * @param parameters request query params
     * @return request uri representation
     */
    public static String resolveProxyRequest(final HttpServerRequest request, final String path, final Map<String, String> parameters) {
        return resolveProxyRequest(request, path, parameters, false);
    }

    public static String resolveProxyRequest(final HttpServerRequest request, final String path) {
        return resolveProxyRequest(request, path, (MultiMap) null, false);
    }

    /**
     * Resolve proxy request
     * @param request original request
     * @param path request path
     * @param parameters request query params
     * @param encoded if request query params should be encoded
     * @return request uri representation
     */
    public static String resolveProxyRequest(final HttpServerRequest request, final String path, final Map<String, String> parameters, boolean encoded) {

        final MultiMap queryParameters;

        if (parameters != null) {
            queryParameters = MultiMap.caseInsensitiveMultiMap();
            queryParameters.addAll(getSafeParameters(parameters));
        } else {
            queryParameters = null;
        }

        return resolveProxyRequest(request, path, queryParameters, encoded);
    }

    private static Map<String, String> getSafeParameters(Map<String, String> parameters) {
        return parameters.entrySet()
                .stream().filter(entry -> nonNull(entry.getValue()))
                .collect(toMap(Entry::getKey, Entry::getValue));
    }

    public static String resolveProxyRequest(final HttpServerRequest request, final String path, final MultiMap parameters) {
        return resolveProxyRequest(request, path, parameters, false);
    }

    public static String resolveProxyRequest(final HttpServerRequest request, final String path, final MultiMap parameters, boolean encoded) {
        return resolve(request, path, parameters, encoded);
    }

    private static String resolve(final HttpServerRequest request, final String path, final MultiMap parameters, boolean encoded) {
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
        String port = request.getHeader(HttpHeaders.X_FORWARDED_PORT);
        if (host != null && !host.isEmpty()) {
            handleHost(builder, host, port);
        } else {
            handleHost(builder, request.host(), port);
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

        if (!encoded) {
            builder.parameters(parameters);
        } else {
            if (parameters != null) {
                parameters.forEach(entry -> {
                    // some parameters can be already URL encoded, decode first
                    builder.addParameter(entry.getKey(), UriBuilder.encodeURIComponent(UriBuilder.decodeURIComponent(entry.getValue())));
                });
            }
        }
        return builder.buildString();
    }

    private static void handleHost(UriBuilder builder, String host, String port) {
        if (host != null) {
            if (host.contains(":")) {
                // host contains both host and port
                String[] parts = host.split(":");
                builder.host(parts[0]);
                handlePort(builder, port, parts[1]);
            } else {
                builder.host(host);
                handlePort(builder, port, null);
            }
        }
    }

    private static void handlePort(UriBuilder builder, String port, String defaultPort) {
        if (!isNullOrEmpty(port)) {
            try {
                builder.port(Integer.parseInt(port));
                return;
            } catch (NumberFormatException ex) {
                LOGGER.warn("X-Forward-Port contains a invalid port value : {}", port);
            }
        }

        if (!isNullOrEmpty(defaultPort)) {
            try {
                builder.port(Integer.parseInt(defaultPort));
            } catch (NumberFormatException ex) {
                LOGGER.warn("X-Forwarded-Host contains a invalid port value : {}", defaultPort);
            }
        }
    }
}

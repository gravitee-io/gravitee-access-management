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
import io.gravitee.am.gateway.handler.common.vertx.core.http.GraviteeVertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.utils.StaticEnvironmentProvider;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.gateway.api.Request;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Map.Entry;

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

    public static String resolveProxyRequest(final Request request) {
        HttpServerRequest httpServerRequest = new HttpServerRequest(new GraviteeVertxHttpServerRequest(request));
        return resolve(httpServerRequest, httpServerRequest.path(), httpServerRequest.params(), false);
    }

    public static String resolveProxyRequest(final Request request, final String path, final Map<String, String> parameters, boolean encoded) {
        HttpServerRequest httpServerRequest = new HttpServerRequest(new GraviteeVertxHttpServerRequest(request));
        return resolveProxyRequest(httpServerRequest, path, parameters, encoded);
    }

    public static String resolveProxyRequest(final HttpServerRequest request, final String path, final Map<String, String> parameters) {
        return resolveProxyRequest(request, path, parameters, false);
    }

    public static String resolveProxyRequest(final HttpServerRequest request, final String path) {
        return resolveProxyRequest(request, path, (MultiMap) null, false);
    }

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

    public static String resolveProxyRequest(final HttpServerRequest request, final String path, final MultiMap parameters) {
        return resolveProxyRequest(request, path, parameters, false);
    }

    public static String resolveProxyRequest(final HttpServerRequest request, final String path, final MultiMap parameters, boolean encoded) {
        return resolve(request, path, parameters, encoded);
    }

    private static String resolve(final HttpServerRequest request, final String path, final MultiMap parameters, boolean encoded) {
        UriBuilder builder = UriBuilder.newInstance();

        // Resolve scheme first - needed for default port checks
        String scheme = resolveScheme(request);
        builder.scheme(scheme);

        // Resolve host and port with proper precedence
        resolveHostAndPort(builder, request, scheme);

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
                    var parameter = entry.getValue();
                    if (StaticEnvironmentProvider.sanitizeParametersEncoding()) {
                        // some parameters can be already URL encoded, decode first
                        parameter = UriBuilder.decodeURIComponent(parameter);
                    }
                    builder.addParameter(entry.getKey(), UriBuilder.encodeURIComponent(parameter));
                });
            }
        }
        return builder.buildString();
    }

    private static Map<String, String> getSafeParameters(Map<String, String> parameters) {
        return parameters.entrySet()
                .stream().filter(entry -> nonNull(entry.getValue()))
                .collect(toMap(Entry::getKey, Entry::getValue));
    }

    /**
     * Resolves the scheme from X-Forwarded-Proto header or falls back to request scheme
     */
    private static String resolveScheme(HttpServerRequest request) {
        String scheme = request.getHeader(HttpHeaders.X_FORWARDED_PROTO);
        return (scheme != null && !scheme.isEmpty()) ? scheme : request.scheme();
    }

    /**
     * Resolves host and port with proper precedence: X-Forwarded headers take precedence over Host header
     */
    private static void resolveHostAndPort(UriBuilder builder, HttpServerRequest request, String scheme) {
        String forwardedHost = request.getHeader(HttpHeaders.X_FORWARDED_HOST);
        String forwardedPort = request.getHeader(HttpHeaders.X_FORWARDED_PORT);
        
        // Check legacy mode once for both X-Forwarded-Host and Host header scenarios
        boolean isLegacyMode = StaticEnvironmentProvider.includeDefaultHttpHostHeaderPorts();
        
        if (forwardedHost != null && !forwardedHost.isEmpty()) {
            // X-Forwarded-Host takes precedence - apply legacy mode if enabled
            setHostAndPort(builder, forwardedHost, forwardedPort, scheme, isLegacyMode);
        } else {
            // Fall back to request.host() - apply legacy mode if enabled
            String requestHost = request.host();
            setHostAndPort(builder, requestHost, forwardedPort, scheme, isLegacyMode);
        }
    }

    /**
     * Sets host and port on the builder, handling port precedence and default port omission
     */
    private static void setHostAndPort(UriBuilder builder, String host, String forwardedPort, String scheme, boolean isLegacyMode) {
        if (host == null || host.isEmpty()) {
            return;
        }

        if (host.contains(":")) {
            // Host contains both hostname and port
            String[] parts = host.split(":");
            builder.host(parts[0]);
            
            if (forwardedPort != null) {
                // X-Forwarded-Port takes precedence - always use new behavior (omit default ports)
                setPortIfNotDefault(builder, forwardedPort, scheme, false);
            } else {
                // Use port from host - apply legacy mode
                setPortIfNotDefault(builder, parts[1], scheme, isLegacyMode);
            }
        } else {
            // Host without port
            builder.host(host);
            if (forwardedPort != null) {
                // X-Forwarded-Port - always use new behavior (omit default ports)
                setPortIfNotDefault(builder, forwardedPort, scheme, false);
            }
        }
    }

    /**
     * Sets port on builder only if it's not a default port for the given scheme (unless in legacy mode)
     */
    private static void setPortIfNotDefault(UriBuilder builder, String port, String scheme, boolean isLegacyMode) {
        if (port == null || port.isEmpty()) {
            return;
        }

        if (!isLegacyMode && isDefaultPort(port, scheme)) {
            // Don't set port for default ports in new behavior
            return;
        }

        try {
            builder.port(Integer.parseInt(port));
        } catch (NumberFormatException ex) {
            LOGGER.warn("Invalid port value: {}", port);
        }
    }

    /**
     * Checks if a port is the default port for the given scheme
     */
    private static boolean isDefaultPort(String port, String scheme) {
        return ("http".equals(scheme) && "80".equals(port)) || 
               ("https".equals(scheme) && "443".equals(port));
    }
}

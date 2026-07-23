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
package io.gravitee.am.gateway.handler.root.handler;

import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.impl.Utils;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.CustomLog;
import org.springframework.core.env.Environment;

import java.net.URLDecoder;
import java.util.Base64;
import java.util.Optional;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class GraviteeLoggerHandler implements Handler<RoutingContext> {

    final boolean includeUriParameter;
    final boolean includeDuration;
    final boolean useXForwardedFor;
    final boolean includeClientId;


    public GraviteeLoggerHandler(Environment environment) {
        includeUriParameter = environment.getProperty("handlers.request.logger.accesslog.includeUriParams", Boolean.class, true);
        includeDuration = environment.getProperty("handlers.request.logger.accesslog.includeDuration", Boolean.class, false);
        useXForwardedFor = environment.getProperty("handlers.request.logger.accesslog.useXForward", Boolean.class, false);
        includeClientId = environment.getProperty("handlers.request.logger.accesslog.includeClientId", Boolean.class, false);
    }

    private void log(RoutingContext context, long timestamp, String remoteClient, HttpVersion version, HttpMethod method, String uri) {
        HttpServerRequest request = context.request();
        final var headers = request.headers();
        final var status = request.response().getStatusCode();
        // as per RFC1945 the header is referer but it is not mandatory some implementations use referrer
        final var referrer = headers.contains("referrer") ? headers.get("referrer") : headers.get("referer");
        final var userAgent = headers.get("user-agent");

        final var entry = new AccessLogEntry(
                remoteClient,
                Utils.formatRFC1123DateTime(timestamp),
                method,
                uri,
                formatVersion(version),
                status,
                request.response().bytesWritten(),
                referrer == null ? "-" : referrer,
                userAgent == null ? "-" : userAgent,
                includeDuration ? System.currentTimeMillis() - timestamp : null,
                includeClientId ? extractClientId(context).orElse("-") : null);

        doLog(status, entry.toLogLine());
    }

    private static String formatVersion(HttpVersion version) {
        return switch (version) {
            case HTTP_1_0 -> "HTTP/1.0";
            case HTTP_1_1 -> "HTTP/1.1";
            case HTTP_2 -> "HTTP/2.0";
            default -> version.alpnName();
        };
    }

    record AccessLogEntry(
            String remoteClient,
            String timestamp,
            HttpMethod method,
            String uri,
            String httpVersion,
            int status,
            long contentLength,
            String referrer,
            String userAgent,
            Long duration,
            String clientId) {

        String toLogLine() {
            final var sb = new StringBuilder()
                    .append(remoteClient).append(" - - [")
                    .append(timestamp).append("] \"")
                    .append(method).append(' ').append(uri).append(' ').append(httpVersion).append("\" ")
                    .append(status).append(' ').append(contentLength)
                    .append(" \"").append(referrer).append("\" \"").append(userAgent).append('"');
            if (duration != null) {
                sb.append(' ').append(duration);
            }
            if (clientId != null) {
                sb.append(" \"").append(clientId).append('"');
            }
            return sb.toString();
        }
    }

    protected void doLog(int status, String message) {
        if (status >= 500) {
            log.error(message);
        } else if (status >= 400) {
            log.warn(message);
        } else {
            log.info(message);
        }
    }

    @Override
    public void handle(RoutingContext context) {
        long timestamp = System.currentTimeMillis();
        String remoteClient = useXForwardedFor ? RequestUtils.remoteAddress(context.request()) : Optional.ofNullable(context.request().remoteAddress()).map(SocketAddress::host).orElse(null);
        HttpMethod method = context.request().method();
        String uri = includeUriParameter ? context.request().uri() :  context.request().path();
        HttpVersion version = context.request().version();

        context.addEndHandler(v -> log(context, timestamp, remoteClient, version, method, uri));

        context.next();
    }

    private static Optional<String> extractClientId(RoutingContext context) {
        final Client client = context.get(CLIENT_CONTEXT_KEY);
        if (client != null) {
            return Optional.ofNullable(client.getClientId());
        }

        final HttpServerRequest request = context.request();
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            return extractClientIdFromAuthHeader(request).or(() -> extractClientIdFromQueryParam(request));
        }

        return extractClientIdFromQueryParam(request);
    }

    private static Optional<String> extractClientIdFromAuthHeader(HttpServerRequest request) {
        final var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        final var authHeaderParts = authHeader.split(" ");

        if (authHeaderParts[0].trim().equalsIgnoreCase("Basic") && authHeaderParts.length == 2) {
            try {
                final var decoded = new String(Base64.getDecoder().decode(authHeaderParts[1].trim()));
                final var colonIdx = decoded.indexOf(":");
                if (colonIdx == -1) {
                    throw new IllegalArgumentException();
                }
                return Optional.ofNullable(urlDecode(decoded.substring(0, colonIdx)));
            } catch (IllegalArgumentException e) {
                // Malformed Base64 or invalid characters, ignore.
                log.debug("Unable to decode Basic auth credentials for logging", e);
            }
        } else {
            log.debug("Authorization header is not of type Basic, ignoring");
        }

        return Optional.empty();
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static Optional<String> extractClientIdFromQueryParam(HttpServerRequest request) {
        return Optional.ofNullable(request.getParam("client_id"));
    }
}

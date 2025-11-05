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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.common.oauth2.Parameters;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class LoggerJsonMessageTokenHandler implements Handler<RoutingContext> {
    public static final String PROPERTY_HANDLERS_REQUEST_LOGGER_JSON_MESSAGE_HEADERS = "handlers.request.logger.jsonMessage.allowedHeaders";
    public static final String PROPERTY_REQUEST_JSON_LOGGER_ENABLED = "handlers.request.logger.jsonMessage.enabled";

    private final List<String> allowedHeaders;

    public LoggerJsonMessageTokenHandler(Environment environment) {
        this.allowedHeaders = ofNullable(environment.getProperty(PROPERTY_HANDLERS_REQUEST_LOGGER_JSON_MESSAGE_HEADERS, String.class))
                .map(String::trim)
                .map(headers -> Arrays.asList(headers.split(",")).stream()
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .toList())
                .orElse(List.of());
    }

    @Override
    public final void handle(RoutingContext context) {
        try {
            // keep timestamp here so we can compute the request duration.
            final var timestamp = System.currentTimeMillis();
            final var loggerContext = new LoggerContext();
            loggerContext.setTimestamp(timestamp);
            context.addBodyEndHandler(v -> processContext(context, loggerContext));
        } finally {
            // want to make sure that any error in this handler will not prevent the processing
            // of the request/response as this is only a handler to log information
            context.next();
        }
    }

    private void processContext(RoutingContext context, LoggerContext loggerContext) {
        HttpServerRequest request = context.request();
        if (request != null && request.path().endsWith("/token")) {
            extractRequestInfo(context, loggerContext);
            extractResponseInfo(context, loggerContext);
            log(loggerContext);
        }
    }

    void log(LoggerContext loggerContext) {
        log.info("{}", Json.encode(loggerContext));
    }

    private void extractRequestInfo(RoutingContext context, LoggerContext loggerContext) {
        HttpServerRequest request = context.request();
        if (request != null) {
            final var incomingHeaders = extractHeaders(request.headers());

            loggerContext.setMethod(request.method().name());
            loggerContext.setPath(request.uri());
            loggerContext.setVersion(request.version());
            loggerContext.setIncomingHeaders(incomingHeaders);
            loggerContext.setGrantType(ofNullable(request.getFormAttribute(Parameters.GRANT_TYPE)).orElse(request.getParam(Parameters.GRANT_TYPE)));
            processAuthorizationHeader(request, loggerContext);
            processRequestBodySize(context, loggerContext);
        }
    }

    private void extractResponseInfo(RoutingContext context, LoggerContext loggerContext) {
        if (context.response() != null) {
            final var outgoingHeaders = extractHeaders(context.response().headers());
            loggerContext.setOutgoingHeaders(outgoingHeaders);
            loggerContext.setStatusCode(context.response() != null ? context.response().getStatusCode() : -1);
            loggerContext.setResponseTime(System.currentTimeMillis() - loggerContext.getTimestamp());
            final var responseLengthHeader = context.response().headers().get(HttpHeaders.CONTENT_LENGTH);
            try {
                loggerContext.setResponseBodySize(responseLengthHeader != null ? Long.parseLong(responseLengthHeader) : -1);
            } catch (NumberFormatException e) {
                log.debug("Unable to parse response body size for logging", e);
                loggerContext.setResponseBodySize(-1);
            }
        }
    }

    /**
     * Extract the body size in Bytes (-1 if body is null)
     * @param context
     * @param loggerContext
     */
    private static void processRequestBodySize(RoutingContext context, LoggerContext loggerContext) {
        if (context.body() != null) {
            loggerContext.setRequestBodySize(context.body().length());
        } else {
            loggerContext.setRequestBodySize(-1);
        }
    }

    /**
     * Evaluate the Authorization header to get the scheme and the clientId if the scheme is Basic
     *
     * @param request
     * @param loggerContext
     */
    private static void processAuthorizationHeader(HttpServerRequest request, LoggerContext loggerContext) {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            final var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            final var authHeaderParts = authHeader.split(" ");
            loggerContext.setAuthTokenType(authHeaderParts[0]);

            if (authHeaderParts[0].trim().equalsIgnoreCase("Basic") && authHeaderParts.length == 2) {
                try {
                    final var decoded = new String(Base64.getDecoder().decode(authHeaderParts[1].trim()));
                    final var colonIdx = decoded.indexOf(":");
                    if (colonIdx == -1) {
                        throw new IllegalArgumentException();
                    }
                    final var clientId = urlDecode(decoded.substring(0, colonIdx));
                    loggerContext.setClientId(clientId);
                } catch (IllegalArgumentException e) {
                    // Malformed Base64 or invalid characters, ignore.
                    log.debug("Unable to decode Basic auth credentials for logging", e);
                }
            }
        }
    }

    /**
     * provide headers filtered based on the allowed headers
     * @param headers
     * @return
     */
    private Map<String, String> extractHeaders(MultiMap headers) {
        if (headers == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(headers.entries().stream().filter(entry -> allowedHeaders.contains(entry.getKey().toLowerCase())).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }


    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    @Getter
    @Setter
    static class LoggerContext {
        long timestamp;
        String method;
        String path;
        HttpVersion version;
        @JsonProperty("incoming_headers")
        Map<String, String> incomingHeaders;
        @JsonProperty("client_id")
        String clientId;
        @JsonProperty("auth_token_type")
        String authTokenType;
        @JsonProperty("request_body_size")
        long requestBodySize;
        @JsonProperty("grant_type")
        String grantType;
        int statusCode;
        @JsonProperty("response_body_size")
        long responseBodySize;
        @JsonProperty("response_time")
        long responseTime;
        @JsonProperty("outgoing_headers")
        Map<String, String> outgoingHeaders;
    }
}

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

import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.impl.Utils;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.net.SocketAddress;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import java.util.Optional;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class GraviteeLoggerHandler implements Handler<RoutingContext> {

    final boolean includeUriParameter;
    final boolean includeDuration;
    final boolean useXForwardedFor;

    public GraviteeLoggerHandler(Environment environment) {
        includeUriParameter = environment.getProperty("handlers.request.logger.accesslog.includeUriParams", Boolean.class, true);
        includeDuration = environment.getProperty("handlers.request.logger.accesslog.includeDuration", Boolean.class, false);
        useXForwardedFor = environment.getProperty("handlers.request.logger.accesslog.useXForward", Boolean.class, false);
    }

    private void log(RoutingContext context, long timestamp, String remoteClient, HttpVersion version, HttpMethod method, String uri) {
        HttpServerRequest request = context.request();
        final var contentLength = request.response().bytesWritten();
        String versionFormatted = "-";
        switch (version){
            case HTTP_1_0:
                versionFormatted = "HTTP/1.0";
                break;
            case HTTP_1_1:
                versionFormatted = "HTTP/1.1";
                break;
            case HTTP_2:
                versionFormatted = "HTTP/2.0";
                break;
            default:
                versionFormatted = version.alpnName();
        }

        final var headers = request.headers();
        final var status = request.response().getStatusCode();
        // as per RFC1945 the header is referer but it is not mandatory some implementations use referrer
        var referrer = headers.contains("referrer") ? headers.get("referrer") : headers.get("referer");
        var userAgent = request.headers().get("user-agent");
        referrer = referrer == null ? "-" : referrer;
        userAgent = userAgent == null ? "-" : userAgent;

        final var message = String.format(includeDuration ? "%s - - [%s] \"%s %s %s\" %d %d \"%s\" \"%s\" %d" : "%s - - [%s] \"%s %s %s\" %d %d \"%s\" \"%s\"",
                remoteClient,
                Utils.formatRFC1123DateTime(timestamp),
                method,
                uri,
                versionFormatted,
                status,
                contentLength,
                referrer,
                userAgent,
                System.currentTimeMillis() - timestamp);

        doLog(status, message);
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
}

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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl;

import io.gravitee.node.logging.NodeLoggerFactory;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.impl.ServerCookie;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Traces the lifecycle of a restricted set of cookies, identified by name.
 *
 * @author GraviteeSource Team
 */
public class CookieTracer {

    private static final Logger LOGGER = NodeLoggerFactory.getLogger("io.gravitee.am.cookie.trace");

    private final Set<String> tracedNames;

    public CookieTracer(String names) {
        this.tracedNames = names == null || names.isBlank()
                ? Set.of()
                : Arrays.stream(names.split(","))
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isActive() {
        return !tracedNames.isEmpty() && LOGGER.isDebugEnabled();
    }

    public void traceRequest(RoutingContext context) {
        if (!isActive()) {
            return;
        }
        final var request = context.request();
        final var path = request.path();
        for (String name : tracedNames) {
            final Cookie cookie = request.getCookie(name);
            if (cookie != null) {
                LOGGER.debug("[{}] request carries cookie '{}' {}", path, name, describe(cookie));
            } else {
                LOGGER.debug("[{}] request does not carry cookie '{}'", path, name);
            }
        }
    }

    public void traceResponse(RoutingContext context, ServerCookie cookie) {
        if (!isActive() || !tracedNames.contains(cookie.getName())) {
            return;
        }
        final var path = context.request().path();
        if (cookie.getMaxAge() == 0) {
            LOGGER.debug("[{}] sending DELETION of cookie '{}', Max-Age=0 {}", path, cookie.getName(), describe(cookie));
        } else {
            LOGGER.debug("[{}] sending cookie '{}', Max-Age={} {}", path, cookie.getName(), cookie.getMaxAge(), describe(cookie));
        }
    }

    public void traceRemoval(RoutingContext context, String name, boolean invalidated, String reason) {
        if (!isActive() || !tracedNames.contains(name)) {
            return;
        }
        final var path = context.request().path();
        if (invalidated) {
            LOGGER.debug("[{}] removeCookie('{}') invalidated the cookie, a Max-Age=0 will be sent, reason: {}", path, name, reason);
        } else {
            LOGGER.debug("[{}] removeCookie('{}') was a no-op, the cookie was absent from the jar, no Set-Cookie will be sent, reason: {}", path, name, reason);
        }
    }

    private static String describe(Cookie cookie) {
        return "[value=" + fingerprint(cookie.getValue())
                + ", path=" + cookie.getPath()
                + ", domain=" + cookie.getDomain()
                + ", secure=" + cookie.isSecure()
                + ", httpOnly=" + cookie.isHttpOnly()
                + ", sameSite=" + cookie.getSameSite()
                + ", fromUserAgent=" + (cookie instanceof ServerCookie serverCookie && serverCookie.isFromUserAgent())
                + "]";
    }

    private static String fingerprint(String value) {
        if (value == null) {
            return "null";
        }
        if (value.isEmpty()) {
            return "empty";
        }
        return "length:" + value.length() + "/hash:" + Integer.toHexString(value.hashCode());
    }
}

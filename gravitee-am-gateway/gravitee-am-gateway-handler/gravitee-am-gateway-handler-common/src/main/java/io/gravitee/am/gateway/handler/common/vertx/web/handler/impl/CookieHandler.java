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

import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.impl.ServerCookie;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Map;
import java.util.Set;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Override default Vert.x Cookie Handler to set proxy cookie path
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CookieHandler implements Handler<RoutingContext> {

    private static final String X_FORWARDED_PREFIX = "X-Forwarded-Prefix";
    private final boolean cookieSecure;

    public CookieHandler(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    @Override
    public void handle(RoutingContext context) {

        context.addHeadersEndHandler(v -> {
            // save the cookies
            Map<String, Cookie> cookies = context.getDelegate().cookieMap();
            for (Cookie cookie: cookies.values()) {
                if (cookie instanceof ServerCookie && ((ServerCookie) cookie).isChanged()) {
                    finalizeCookie(context, (ServerCookie) cookie);
                }
            }
        });

        context.next();
    }

    /**
     * Juste finalize the cookie to make sure that attributes are well defined (path, secure flag, ...).
     *
     * @param context the current routing context.
     * @param cookie the cookie to rewrite.
     */
    private void finalizeCookie(RoutingContext context, ServerCookie cookie) {
        final String cookiePath = context.get(CONTEXT_PATH);
        String forwardedPath = context.request().getHeader(X_FORWARDED_PREFIX);
        if (forwardedPath != null && !forwardedPath.isEmpty()) {
            // Remove trailing slash.
            forwardedPath = forwardedPath.substring(0, forwardedPath.length() - (forwardedPath.endsWith("/") ? 1 : 0));
            forwardedPath += cookiePath;
        } else {
            forwardedPath = cookiePath;
        }

        // Rewrite the cookie path (depends on domain path and possible X-Forwarded-Prefix request header).
        cookie.setPath(forwardedPath);

        // Make sure the cookie secure attribute is set as expected.
        cookie.setSecure(cookieSecure);

        // There is no reason to allow javascript to access gateway's cookie.
        cookie.setHttpOnly(true);
    }
}
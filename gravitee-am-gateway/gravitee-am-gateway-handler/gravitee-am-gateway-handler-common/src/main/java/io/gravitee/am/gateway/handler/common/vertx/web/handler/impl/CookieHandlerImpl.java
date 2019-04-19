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

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.impl.CookieImpl;

import java.util.Set;

import static io.vertx.core.http.HttpHeaders.COOKIE;
import static io.vertx.core.http.HttpHeaders.SET_COOKIE;

/**
 * Override default Vert.x Cookie Handler to set proxy cookie path
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CookieHandlerImpl implements CookieHandler {

    private static final String X_FORWARDED_PREFIX = "X-Forwarded-Prefix";

    @Override
    public void handle(RoutingContext context) {
        String cookieHeader = context.request().headers().get(COOKIE);

        if (cookieHeader != null) {
            Set<Cookie> nettyCookies = ServerCookieDecoder.STRICT.decode(cookieHeader);
            for (io.netty.handler.codec.http.cookie.Cookie cookie : nettyCookies) {
                io.vertx.ext.web.Cookie ourCookie = new CookieImpl(cookie);
                context.addCookie(ourCookie);
            }
        }

        context.addHeadersEndHandler(v -> {
            // save the cookies
            Set<io.vertx.ext.web.Cookie> cookies = context.cookies();
            for (io.vertx.ext.web.Cookie cookie: cookies) {
                if (cookie.isChanged()) {
                    proxy(context, cookie);
                    context.response().headers().add(SET_COOKIE, cookie.encode());
                }
            }
        });

        context.next();
    }

    private void proxy(RoutingContext context, io.vertx.ext.web.Cookie cookie) {
        final String cookiePath = cookie.getPath();
        String forwardedPath = context.request().getHeader(X_FORWARDED_PREFIX);
        if (forwardedPath != null && !forwardedPath.isEmpty()) {
            // remove trailing slash
            forwardedPath = forwardedPath.substring(0, forwardedPath.length() - (forwardedPath.endsWith("/") ? 1 : 0));
            forwardedPath += cookiePath;
        } else {
            forwardedPath = cookiePath;
        }
        cookie.setPath(forwardedPath);
    }
}

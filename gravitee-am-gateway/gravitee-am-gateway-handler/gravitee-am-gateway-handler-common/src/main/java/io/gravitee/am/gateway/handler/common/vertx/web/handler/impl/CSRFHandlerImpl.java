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

import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CSRFHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static io.vertx.core.http.HttpMethod.*;

/**
 * Override default Vert.x CSRFHandler to enhance routing context with CSRF values to fill in the right value for the form fields.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CSRFHandlerImpl implements CSRFHandler {

    private static final Logger log = LoggerFactory.getLogger(io.vertx.ext.web.handler.impl.CSRFHandlerImpl.class);

    private static final Base64.Encoder BASE64 = Base64.getMimeEncoder();

    private final Random RAND = new SecureRandom();
    private final Mac mac;

    private boolean nagHttps;
    private String cookieName = DEFAULT_COOKIE_NAME;
    private String cookiePath = DEFAULT_COOKIE_PATH;
    private String headerName = DEFAULT_HEADER_NAME;
    private long timeout = SessionHandler.DEFAULT_SESSION_TIMEOUT;
    private String origin;
    private boolean httpOnly;

    public CSRFHandlerImpl(final String secret) {
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CSRFHandler setOrigin(String origin) {
        this.origin = origin;
        return this;
    }

    @Override
    public CSRFHandler setCookieName(String cookieName) {
        this.cookieName = cookieName;
        return this;
    }

    @Override
    public CSRFHandler setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
        return this;
    }

    @Override
    public CSRFHandler setCookieHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    @Override
    public CSRFHandler setHeaderName(String headerName) {
        this.headerName = headerName;
        return this;
    }

    @Override
    public CSRFHandler setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public CSRFHandler setNagHttps(boolean nag) {
        this.nagHttps = nag;
        return this;
    }

    private String generateToken() {
        byte[] salt = new byte[32];
        RAND.nextBytes(salt);

        String saltPlusToken = BASE64.encodeToString(salt) + "." + Long.toString(System.currentTimeMillis());
        String signature = BASE64.encodeToString(mac.doFinal(saltPlusToken.getBytes()));

        return saltPlusToken + "." + signature;
    }

    private boolean validateToken(String header, Cookie cookie) {
        // both the header and the cookie must be present, not null and equal
        if (header == null || cookie == null || !header.equals(cookie.getValue())) {
            return false;
        }

        String[] tokens = header.split("\\.");
        if (tokens.length != 3) {
            return false;
        }

        String saltPlusToken = tokens[0] + "." + tokens[1];
        String signature = BASE64.encodeToString(mac.doFinal(saltPlusToken.getBytes()));

        if (!signature.equals(tokens[2])) {
            return false;
        }

        try {
            // validate validity
            return !(System.currentTimeMillis() > Long.parseLong(tokens[1]) + timeout);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    protected void redirect(RoutingContext ctx) {
        final int statusCode = 302;

        final HttpServerRequest httpServerRequest = new HttpServerRequest(ctx.request());
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(httpServerRequest);
        queryParams.set("error", "session_expired");
        queryParams.set("error_description", "Your session expired, please try again.");

        final String uri = UriBuilderRequest.resolveProxyRequest(httpServerRequest, ctx.request().path(), queryParams, true);

        ctx.response()
                .putHeader(HttpHeaders.LOCATION, uri)
                .setStatusCode(statusCode)
                .end();
    }

    @Override
    public void handle(RoutingContext ctx) {

        if (nagHttps) {
            String uri = ctx.request().absoluteURI();
            if (uri != null && !uri.startsWith("https:")) {
                log.warn("Using session cookies without https could make you susceptible to session hijacking: " + uri);
            }
        }

        HttpMethod method = ctx.request().method();

        switch (method.name()) {
            case "GET":
                final String token = generateToken();
                // put the token in the context for users who prefer to render the token directly on the HTML
                ctx.put(headerName, token);
                ctx.addCookie(Cookie.cookie(cookieName, token));
                enhanceContext(ctx);
                ctx.next();
                break;
            case "POST":
            case "PUT":
            case "DELETE":
            case "PATCH":
                final String header = ctx.request().getHeader(headerName);
                final Cookie cookie = ctx.getCookie(cookieName);
                if (validateToken(header == null ? ctx.request().getFormAttribute(headerName) : header, cookie)) {
                    ctx.next();
                } else {
                    redirect(ctx);
                }
                break;
            default:
                // ignore these methods
                ctx.next();
                break;
        }
    }

    private void enhanceContext(RoutingContext ctx) {
        Map<String, String> _csrf = new HashMap<>();
        _csrf.put("parameterName", io.vertx.ext.web.handler.CSRFHandler.DEFAULT_HEADER_NAME);
        _csrf.put("token", ctx.get(io.vertx.ext.web.handler.CSRFHandler.DEFAULT_HEADER_NAME));
        ctx.put("_csrf", _csrf);
    }
}

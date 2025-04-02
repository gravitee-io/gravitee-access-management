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

import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorParamsUpdater;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.VertxContextPRNG;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.CSRFHandler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;

/**
 * Override default Vert.x CSRFHandler to enhance routing context with CSRF values to fill in the right value for the form fields.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class CSRFHandlerImpl implements CSRFHandler {

    private static final Base64.Encoder BASE64 = Base64.getMimeEncoder();

    private final VertxContextPRNG RAND;
    private final Mac mac;

    private boolean nagHttps;
    private String cookieName = DEFAULT_COOKIE_NAME;
    private String cookiePath = DEFAULT_COOKIE_PATH;
    private String headerName = DEFAULT_HEADER_NAME;
    private long timeout;
    private String origin;
    private boolean httpOnly;
    private boolean cookieSecure;

    public CSRFHandlerImpl(Vertx vertx, final String secret, final long timeout) {
        this.RAND = VertxContextPRNG.current(vertx);
        this.timeout = timeout;
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
    public CSRFHandler setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
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

        final String plainJwt = BASE64.encodeToString(salt) + "." + System.currentTimeMillis();
        byte[] saltPlusToken = plainJwt.getBytes(StandardCharsets.US_ASCII);
        synchronized (mac) {
            saltPlusToken = mac.doFinal(saltPlusToken);
        }
        String signature = BASE64.encodeToString(saltPlusToken);

        return plainJwt + "." + signature;
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

        byte[] saltPlusToken = (tokens[0] + "." + tokens[1]).getBytes(StandardCharsets.US_ASCII);
        synchronized (mac) {
            saltPlusToken = mac.doFinal(saltPlusToken);
        }
        String signature = BASE64.encodeToString(saltPlusToken);

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

        String hash = ErrorParamsUpdater.addErrorParams(queryParams, "session_expired", "Your session expired, please try again.");
        if (ctx.session() != null) {
            ctx.session().put(ERROR_HASH, hash);
        }

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
                final String token;
                Session session = ctx.session();
                // if there's no session to store values, tokens are issued on every request
                if (session == null) {
                    token = generateToken();
                } else {
                    // get the token from the session
                    String sessionToken = session.get(headerName);
                    // when there's no token in the session, then we behave just like when there is no session
                    // create a new token, but we also store it in the session for the next runs
                    if (sessionToken == null) {
                        token = generateAndStoreToken(ctx);
                    } else {
                        // attempt to parse the value
                        int idx = sessionToken.indexOf('/');
                        if (idx != -1) {
                            String sid = sessionToken.substring(0, idx);
                            if (sid.equals(session.id())) {
                                // we're still on the same session, no need to regenerate the token
                                final String currentToken = sessionToken.substring(idx + 1);
                                if (!validateToken(currentToken, ctx.request().getCookie(cookieName))) {
                                    // XSRF token has expired, regenerate a new one to not block the user
                                    token = generateAndStoreToken(ctx);
                                } else {
                                    // in this case specifically we don't issue the token as it is unchanged
                                    // the user agent still has it from the previous interaction.
                                    token = currentToken;
                                }
                            } else {
                                // session has been upgraded, don't trust the token and regenerate
                                token = generateAndStoreToken(ctx);
                            }
                        } else {
                            // cannot parse the value from the session
                            token = generateAndStoreToken(ctx);
                       }
                    }
                }

                ctx.response().addCookie(Cookie.cookie(cookieName, token).setPath(cookiePath));

                // put the token in the context for users who prefer to render the token directly on the HTML
                ctx.put(headerName, token);
                enhanceContext(ctx);
                ctx.next();
                break;
            case "POST":
            case "PUT":
            case "DELETE":
            case "PATCH":
                final String header = ctx.request().getHeader(headerName);
                final Cookie cookie = ctx.request().getCookie(cookieName);
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

    private String generateAndStoreToken(RoutingContext context) {
        var token = generateToken();
        // storing will include the session id too. The reason is that if a session is upgraded
        // we don't want to allow the token to be valid anymore
        var session = context.session();
        session.put(headerName, session.id() + "/" + token);
        return token;
    }

    private void enhanceContext(RoutingContext ctx) {
        Map<String, String> _csrf = new HashMap<>();
        _csrf.put("parameterName", io.vertx.ext.web.handler.CSRFHandler.DEFAULT_HEADER_NAME);
        _csrf.put("token", ctx.get(io.vertx.ext.web.handler.CSRFHandler.DEFAULT_HEADER_NAME));
        ctx.put("_csrf", _csrf);
    }
}

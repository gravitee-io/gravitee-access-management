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

import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.service.UserService;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.core.http.Cookie;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.USER_ID_KEY;
import static io.vertx.ext.web.handler.SessionHandler.DEFAULT_SESSION_TIMEOUT;

/**
 * Session handler based on minimalistic jwt Cookie.
 * This session handler is also responsible to automatically fetch the current user if a USER_ID_KEY is present in the session.
 * Once loaded, the user is put into the current routing context.
 *
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CookieSessionHandler implements Handler<RoutingContext> {

    private static final String DEFAULT_SESSION_COOKIE_NAME = "GRAVITEE_IO_AM_SESSION";
    private static final Logger logger = LoggerFactory.getLogger(CookieSessionHandler.class);

    private final JWTService jwtService;
    private final CertificateManager certificateManager;
    private final UserService userService;

    @Value("${http.cookie.session.name:" + DEFAULT_SESSION_COOKIE_NAME + "}")
    private String cookieName;

    @Value("${http.cookie.session.timeout:" + DEFAULT_SESSION_TIMEOUT + "}")
    private long timeout;

    public CookieSessionHandler(JWTService jwtService, CertificateManager certificateManager, UserService userService) {
        this.jwtService = jwtService;
        this.certificateManager = certificateManager;
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {

        if (logger.isDebugEnabled()) {
            String uri = context.request().absoluteURI();
            if (!uri.startsWith("https:")) {
                logger.debug("Using session cookies without https could make you susceptible to session hijacking: " + uri);
            }
        }

        Cookie sessionCookie = context.getCookie(cookieName);
        CookieSession session = new CookieSession(jwtService, certificateManager.defaultCertificateProvider(), timeout);

        registerSession(context, session);

        Single<CookieSession> sessionObs = Single.just(session);

        if (sessionCookie != null) {
            sessionObs = session.setValue(sessionCookie.getValue())
                    .flatMap(currentSession -> {
                        String userId = currentSession.get(USER_ID_KEY);
                        if (!StringUtils.isEmpty(userId)) {
                            // Load the user and put it back in the context.
                            return userService.findById(userId)
                                    .doOnSuccess(user -> context.getDelegate().setUser(new User(user)))
                                    .flatMap(user -> userService.enhance(user).toMaybe())
                                    .map(user -> currentSession)
                                    .switchIfEmpty(cleanupSession(currentSession))
                                    .onErrorResumeNext(cleanupSession(currentSession));
                        } else {
                            return Single.just(currentSession);
                        }
                    });
        }

        // Need to wait the session to be ready before invoking next.
        sessionObs
                .doOnError(t -> logger.warn("Unable to restore the session", t))
                .doFinally(context::next)
                .subscribe();
    }

    private Single<CookieSession> cleanupSession(CookieSession currentSession) {
        return Single.defer(() -> {
            // Empty the session to avoid using data of another user (mainly used if user has not been found or in case of error).
            currentSession.setValue(null);
            return Single.just(currentSession);
        });
    }

    private void registerSession(RoutingContext context, CookieSession session) {

        // Register the session to the current context.
        context.getDelegate().setSession(session);

        // Add handler to flush session to cookie when done.
        context.addHeadersEndHandler(v -> flush(context, session));
    }

    private void flush(RoutingContext context, CookieSession session) {

        if (session.isDestroyed()) {
            context.addCookie(Cookie.cookie(cookieName, "").setMaxAge(0));
        } else {
            final int currentStatusCode = context.response().getStatusCode();
            // Regenerate session cookie only if there was no error.
            if (currentStatusCode >= 200 && currentStatusCode < 400) {
                writeSessionCookie(context, session);
            }
        }
    }

    private void writeSessionCookie(final RoutingContext context, final CookieSession session) {

        io.vertx.ext.auth.User user = context.getDelegate().user();
        if (user instanceof User) {
            session.put(USER_ID_KEY, ((User) user).getUser().getId());
        }

        Cookie cookie = Cookie.cookie(cookieName, session.value());

        // set max age if user requested it - else it's a session cookie
        if (timeout >= 0) {
            cookie.setMaxAge(TimeUnit.MILLISECONDS.toSeconds(timeout));
        }

        // All other cookie's properties are managed by a dedicated CookieHandler.
        context.addCookie(cookie);
    }
}



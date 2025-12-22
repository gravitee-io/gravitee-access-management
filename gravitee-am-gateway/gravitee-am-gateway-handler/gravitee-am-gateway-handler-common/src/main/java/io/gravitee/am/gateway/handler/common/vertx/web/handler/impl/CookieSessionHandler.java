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

import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.jwt.PrematureJWTException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.model.CookieSettings;
import io.gravitee.am.model.SessionSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.service.impl.user.UserEnhancer;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.http.Cookie;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.vertx.ext.web.handler.SessionHandler.DEFAULT_SESSION_TIMEOUT;
import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;

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

    final static String USER_ID_KEY = "userId";

    private final JWTService jwtService;
    private final CertificateManager certificateManager;
    private final SubjectManager subjectManager;
    private final UserEnhancer userEnhancer;

    @Value("${http.cookie.session.name:" + DEFAULT_SESSION_COOKIE_NAME + "}")
    private String cookieName;

    @Value("${http.cookie.session.timeout:" + DEFAULT_SESSION_TIMEOUT + "}")
    private long timeout;

    @Value("${http.cookie.session.persistent:true}")
    private boolean persistent;

    public CookieSessionHandler(JWTService jwtService,
                                CertificateManager certificateManager,
                                UserEnhancer userGroupEnhancer,
                                SubjectManager subjectManager) {
        this.jwtService = jwtService;
        this.certificateManager = certificateManager;
        this.userEnhancer = userGroupEnhancer;
        this.subjectManager = subjectManager;
    }

    public CookieSessionHandler(JWTService jwtService,
                                CertificateManager certificateManager,
                                SubjectManager subjectManager,
                                UserEnhancer userGroupEnhancer,
                                String cookieName,
                                long timeout) {
        this(jwtService, certificateManager, userGroupEnhancer, subjectManager);
        this.cookieName = cookieName;
        this.timeout = timeout;
    }

    @Override
    public void handle(RoutingContext context) {
        if (logger.isDebugEnabled()) {
            String uri = context.request().absoluteURI();
            if (!uri.startsWith("https:")) {
                logger.debug("Using session cookies without https could make you susceptible to session hijacking: {}", uri);
            }
        }

        Cookie sessionCookie = context.getCookie(cookieName);
        CookieSession session = new CookieSession(jwtService, certificateManager, timeout);

        registerSession(context, session);
        updateSessionWithTransactionId(context, session);

        Single<CookieSession> sessionObs = Single.just(session);

        if (sessionCookie != null) {
            sessionObs = session.setValue(sessionCookie.getValue())
                    .flatMap(currentSession -> {
                        final String userSub = currentSession.get(Claims.GIO_INTERNAL_SUB);
                        if (StringUtils.hasText(userSub)) {
                            // Load the user and put it back in the context.
                            final var jwt = new JWT();
                            jwt.setSub(userSub);
                            jwt.setInternalSub(userSub);
                            return subjectManager.findUserBySub(jwt)
                                    .doOnSuccess(user -> context.getDelegate().setUser(new User(user)))
                                    .flatMap(user -> userEnhancer.enhance(user).toMaybe())
                                    .map(user -> currentSession)
                                    .switchIfEmpty(cleanupSession(currentSession))
                                    .onErrorResumeNext(exception -> cleanupSession(currentSession));
                        } else {
                            return Single.just(currentSession);
                        }
                    });
        }

        // Need to wait the session to be ready before invoking next.

        sessionObs
                .doFinally(context::next)
                .subscribe(
                        success -> logger.trace("Session restored successfully"),
                        error -> {
                            final Throwable cause = error.getCause();
                            if (cause instanceof PrematureJWTException | error instanceof ExpiredJWTException) {
                                logger.info("Unable to restore the session: {}", cause.getMessage());
                            } else {
                                logger.warn("Unable to restore the session: {}", cause.getMessage());
                            }
                        }
                );
    }

    private void updateSessionWithTransactionId(RoutingContext routingContext, CookieSession session) {
        String transactionId = routingContext.get(ConstantKeys.TRANSACTION_ID_KEY);

        if(transactionId != null && session.get(ConstantKeys.TRANSACTION_ID_KEY) == null) {
            session.put(ConstantKeys.TRANSACTION_ID_KEY, transactionId);
        }
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
            final var modelUser = ((User) user).getUser();
            session.putUserId(modelUser.getId());
            session.putUserInternalSub(subjectManager.generateInternalSubFrom(modelUser));
        }
        Cookie cookie = Cookie.cookie(cookieName, session.value());

        // set max age if user requested it - else it's a session cookie
        if (timeout >= 0 && persistentSession(context)) {
            cookie.setMaxAge(TimeUnit.MILLISECONDS.toSeconds(timeout));
        }

        // All other cookie's properties are managed by a dedicated CookieHandler.
        context.addCookie(cookie);
    }

    private Boolean persistentSession(RoutingContext context) {
        var client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        if (nonNull(client)) {
            CookieSettings cookieSettings =
                    (client instanceof Client) ? ((Client) client).getCookieSettings() :
                            (client instanceof ClientProperties) ? ((ClientProperties) client).getCookieSettings() : null;

            return Optional.ofNullable(cookieSettings)
                    .filter(not(CookieSettings::isInherited))
                    .map(CookieSettings::getSession)
                    .map(SessionSettings::isPersistent)
                    .orElse(persistent);
        }
        return persistent;
    }
}

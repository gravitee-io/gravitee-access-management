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
package io.gravitee.am.gateway.handler.root.resources.endpoint.logout;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.LogoutAuditBuilder;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.net.SocketAddress;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LogoutEndpoint implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogoutEndpoint.class);
    private static final String LOGOUT_URL_PARAMETER = "target_url";
    private static final String INVALIDATE_TOKENS_PARAMETER = "invalidate_tokens";
    private static final String DEFAULT_TARGET_URL = "/";
    private Domain domain;
    private TokenService tokenService;
    private AuditService auditService;

    public LogoutEndpoint(Domain domain, TokenService tokenService, AuditService auditService) {
        this.domain = domain;
        this.tokenService = tokenService;
        this.auditService = auditService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // invalidate session
        invalidateSession(routingContext, invalidateSessionHandler -> {
            // invalidate tokens if option is enabled
            if (invalidateTokensEnabled(routingContext)) {
                invalidateTokens(invalidateSessionHandler.result(), invalidateTokensHandler -> {
                    if (invalidateTokensHandler.failed()) {
                        LOGGER.error("An error occurs while invalidating user tokens", invalidateSessionHandler.cause());
                    }
                    doRedirect(routingContext);
                });
            } else {
                doRedirect(routingContext);
            }
        });
    }

    private void invalidateSession(RoutingContext routingContext, Handler<AsyncResult<User>> handler) {
        io.gravitee.am.model.User endUser = null;
        // clear context and session
        if (routingContext.user() != null) {
            endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            // audit event
            report(endUser, routingContext.request());
            // clear user
            routingContext.clearUser();
        }

        if (routingContext.session() != null) {
            routingContext.session().destroy();
        }

        handler.handle(Future.succeededFuture(endUser));
    }

    private void invalidateTokens(User user, Handler<AsyncResult<Void>> handler) {
        // if you have no user, continue
        if (user == null) {
            handler.handle(Future.succeededFuture());
            return;
        }

        tokenService.deleteByUserId(user.getId())
                .subscribe(
                        () -> handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error)));

    }

    private void doRedirect(RoutingContext routingContext) {
        // redirect to target url
        String logoutRedirectUrl = routingContext.request().getParam(LOGOUT_URL_PARAMETER);
        routingContext
                .response()
                .putHeader(HttpHeaders.LOCATION, (logoutRedirectUrl != null && !logoutRedirectUrl.isEmpty()) ? logoutRedirectUrl : DEFAULT_TARGET_URL)
                .setStatusCode(302)
                .end();
    }

    private void report(User endUser, HttpServerRequest request) {
        auditService.report(AuditBuilder.builder(LogoutAuditBuilder.class).domain(domain.getId()).user(endUser).ipAddress(remoteAddress(request)).userAgent(userAgent(request)));
    }

    private boolean invalidateTokensEnabled(RoutingContext routingContext) {
        String invalidateTokensParam = routingContext.request().getParam(INVALIDATE_TOKENS_PARAMETER);
        return invalidateTokensParam != null && Boolean.valueOf(invalidateTokensParam);
    }

    private String remoteAddress(HttpServerRequest httpServerRequest) {
        String xForwardedFor = httpServerRequest.getHeader(HttpHeaders.X_FORWARDED_FOR);
        String remoteAddress;

        if(xForwardedFor != null && xForwardedFor.length() > 0) {
            int idx = xForwardedFor.indexOf(',');

            remoteAddress = (idx != -1) ? xForwardedFor.substring(0, idx) : xForwardedFor;

            idx = remoteAddress.indexOf(':');

            remoteAddress = (idx != -1) ? remoteAddress.substring(0, idx).trim() : remoteAddress.trim();
        } else {
            SocketAddress address = httpServerRequest.remoteAddress();
            remoteAddress = (address != null) ? address.host() : null;
        }

        return remoteAddress;
    }

    private String userAgent(HttpServerRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}

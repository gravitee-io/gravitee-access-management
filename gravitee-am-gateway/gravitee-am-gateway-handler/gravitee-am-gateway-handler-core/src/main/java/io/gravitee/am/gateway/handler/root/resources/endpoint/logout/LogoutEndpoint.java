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
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.LogoutAuditBuilder;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.net.SocketAddress;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LogoutEndpoint implements Handler<RoutingContext> {

    private static final String LOGOUT_URL_PARAMETER = "target_url";
    private static final String DEFAULT_TARGET_URL = "/";
    private AuditService auditService;
    private Domain domain;

    public LogoutEndpoint(Domain domain, AuditService auditService) {
        this.domain = domain;
        this.auditService = auditService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // clear context and session
        if (routingContext.user() != null) {
            io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            // audit event
            report(endUser, routingContext.request());
            // clear user
            routingContext.clearUser();
        }

        if (routingContext.session() != null) {
            routingContext.session().destroy();
        }

        // redirect to target url
        String logoutRedirectUrl = routingContext.request().getParam(LOGOUT_URL_PARAMETER);
        routingContext
                .response()
                .putHeader(HttpHeaders.LOCATION, (logoutRedirectUrl != null && !logoutRedirectUrl.isEmpty()) ? logoutRedirectUrl : DEFAULT_TARGET_URL)
                .setStatusCode(302)
                .end();
    }

    public static LogoutEndpoint create(Domain domain, AuditService auditService) {
        return new LogoutEndpoint(domain, auditService);
    }

    private void report(User endUser, HttpServerRequest request) {
        auditService.report(AuditBuilder.builder(LogoutAuditBuilder.class).domain(domain.getId()).user(endUser).ipAddress(remoteAddress(request)).userAgent(userAgent(request)));
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

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
package io.gravitee.am.authenticator.api;

import io.gravitee.am.common.exception.authentication.AccountStatusException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.service.AuditService;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AuthenticatorHandler implements Handler<RoutingContext> {

    private final Authenticator authenticator;
    private final AuditService auditService;

    @Override
    public void handle(RoutingContext routingContext) {
        authenticator.authenticate(routingContext)
                .doOnSuccess(user -> auditService.report(authenticator.successAuditLog(routingContext, user)))
                .doOnError(err -> {
                    var auditEvent = authenticator.failedAuditLog(routingContext, err);
                    auditEvent.principal(setAuditUser(err));
                    auditService.report(auditEvent);
                })
                .subscribe(
                        user -> setupUserSession(routingContext, user).next(),
                        err -> {
                            log.debug("An error occurred while authenticating user", err);
                            routingContext.fail(err);
                        }
                );
    }

    private RoutingContext setupUserSession(RoutingContext ctx, User user) {
        ctx.getDelegate().setUser(user);
        ctx.put(ConstantKeys.USER_CONTEXT_KEY, user.getUser());
        return ctx;
    }

    private static DefaultUser setAuditUser(Throwable err) {
        if (!(err instanceof AccountStatusException ase)) {
            return null;
        }

        var details = ase.getDetails();
        if (details == null) {
            return null;
        }

        var user = new io.gravitee.am.model.User();
        user.setId(details.get("id"));
        user.setUsername(details.get("username"));
        user.setDisplayName(details.get("displayName"));
        return new DefaultUser(user);
    }

}

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
package io.gravitee.am.gateway.handler.root.resources.handler.user.password;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.PasswordHistoryException;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

/**
 * Checks a password against a user's history of passwords and returns either a
 * 200 ok or 400 failure if the password is already in the history.
 */
public class PasswordHistoryHandler implements Handler<RoutingContext> {

    private final PasswordHistoryService passwordHistoryService;
    private final UserService userService;
    private final Domain domain;

    public PasswordHistoryHandler(PasswordHistoryService passwordHistoryService, UserService userService, Domain domain) {
        this.passwordHistoryService = passwordHistoryService;
        this.userService = userService;
        this.domain = domain;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void handle(RoutingContext context) {
        var accessToken = context.request().getFormAttribute(ConstantKeys.TOKEN_CONTEXT_KEY);
        var password = context.request().getFormAttribute(ConstantKeys.PASSWORD_PARAM_KEY);
        userService.verifyToken(accessToken)
                   .flatMapSingle(userToken -> {
                       var user = userToken.getUser();
                       return passwordHistoryService
                               .passwordAlreadyUsed(ReferenceType.DOMAIN, domain.getId(), user.getId(), password, getPasswordSettings(context));

                   })
                   .doOnError(throwable -> context.fail(HttpStatusCode.INTERNAL_SERVER_ERROR_500, throwable))
                   .subscribe(passwordUsed -> {
                       if (Boolean.TRUE.equals(passwordUsed)) {
                           context.response().setStatusCode(HttpStatusCode.BAD_REQUEST_400).end();
                       } else {
                           context.response().setStatusCode(HttpStatusCode.OK_200).end();
                       }
                   });

    }

    private PasswordSettings getPasswordSettings(RoutingContext context) {
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        return PasswordSettings.getInstance(client, domain).orElse(null);
    }
}

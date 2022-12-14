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
package io.gravitee.am.gateway.handler.root.resources.handler.user.register;

import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserRequestHandler;
import io.gravitee.am.gateway.handler.root.service.response.RegistrationResponse;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveUserAgent;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterValidationProcessHandler extends UserRequestHandler {

    private final UserService userService;
    private final Domain domain;

    public RegisterValidationProcessHandler(UserService userService, Domain domain) {
        this.userService = userService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        // retrieve the client in context
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        User user = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) context.user().getDelegate()).getUser();

        // register the user
        confirmRegistration(user, context.request().getParam("code"), client, h -> {
            // if failure, return to the register page with an error
            if (h.failed()) {
                context.fail(h.cause());
                return;
            }

            RegistrationResponse registrationResponse = h.result();
            // if auto login option is enabled add the user to the session
            if (!registrationResponse.isAutoLogin()) {
                // we keep user into the session in order to retrieve the userinfo on validation endpoint
                // if autoLogin is disabled, we have to remove it
                context.clearUser();
                context.session().destroy();
            }
            // put response into the context and continue
            context.put(ConstantKeys.REGISTRATION_RESPONSE_KEY, registrationResponse);
            context.put(ConstantKeys.USER_CONTEXT_KEY, registrationResponse.getUser());
            context.next();
        });
    }

    private void confirmRegistration(User user, String code, Client client, Handler<AsyncResult<RegistrationResponse>> handler) {
        if (code != null && code.equals(user.getAdditionalInformation().get("registration_code"))) {
            AccountSettings accountSettings = AccountSettings.getInstance(domain, client);

            user.setEnabled(true);
            user.getAdditionalInformation().remove("registration_code");
            userService.update(user)
                    .subscribe(
                            response -> handler.handle(Future.succeededFuture(new RegistrationResponse(user, accountSettings != null ? accountSettings.getRedirectUriAfterRegistration() : null, accountSettings != null && accountSettings.isAutoLoginAfterRegistration()))),
                            error -> handler.handle(Future.failedFuture(error)));
        } else {
            handler.handle(Future.failedFuture(new InvalidCodeException("Invalid code")));
        }
    }

    @Override
    protected io.gravitee.am.identityprovider.api.User getAuthenticatedUser(RoutingContext routingContext) {
        // override principal user
        DefaultUser principal = new DefaultUser(routingContext.request().getParam("username"));
        Map<String, Object> additionalInformation = new HashMap<>();
        if(canSaveIp(routingContext)){
            additionalInformation.put(Claims.ip_address, RequestUtils.remoteAddress(routingContext.request()));
        }
        if(canSaveUserAgent(routingContext)){
            additionalInformation.put(Claims.user_agent, RequestUtils.userAgent(routingContext.request()));
        }
        additionalInformation.put(Claims.domain, domain.getId());
        principal.setAdditionalInformation(additionalInformation);
        return principal;
    }
}

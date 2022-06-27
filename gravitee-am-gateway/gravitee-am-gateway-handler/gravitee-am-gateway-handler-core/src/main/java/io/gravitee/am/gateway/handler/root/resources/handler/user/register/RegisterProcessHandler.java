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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserRequestHandler;
import io.gravitee.am.gateway.handler.root.service.response.RegistrationResponse;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.impl.user.activity.utils.ConsentUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveUserAgent;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterProcessHandler extends UserRequestHandler {

    private final UserService userService;
    private final Domain domain;

    public RegisterProcessHandler(UserService userService, Domain domain) {
        this.userService = userService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        // retrieve the client in context
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        // create the user
        MultiMap params = context.request().formAttributes();
        User user = convert(params);

        // register the user
        register(client, user, getAuthenticatedUser(context), h -> {
            // if failure, return to the register page with an error
            if (h.failed()) {
                context.fail(h.cause());
                return;
            }

            RegistrationResponse registrationResponse = h.result();
            // if auto login option is enabled add the user to the session
            if (registrationResponse.isAutoLogin()) {
                context.setUser(io.vertx.reactivex.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(registrationResponse.getUser())));
            }
            // put response into the context and continue
            context.put(ConstantKeys.REGISTRATION_RESPONSE_KEY, registrationResponse);
            context.put(ConstantKeys.USER_CONTEXT_KEY, registrationResponse.getUser());
            context.next();
        });
    }

    private void register(Client client, User user, io.gravitee.am.identityprovider.api.User principal, Handler<AsyncResult<RegistrationResponse>> handler) {
        userService.register(client, user, principal)
                .subscribe(
                        response -> handler.handle(Future.succeededFuture(response)),
                        error -> handler.handle(Future.failedFuture(error)));
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

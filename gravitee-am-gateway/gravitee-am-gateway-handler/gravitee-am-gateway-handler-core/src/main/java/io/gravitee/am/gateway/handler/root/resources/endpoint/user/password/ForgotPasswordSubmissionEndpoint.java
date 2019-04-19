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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.password;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserRequestHandler;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ForgotPasswordSubmissionEndpoint extends UserRequestHandler {

    private static final String emailParam = "email";
    private UserService userService;
    private Domain domain;

    public ForgotPasswordSubmissionEndpoint(UserService userService, Domain domain) {
        this.userService = userService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        final String email = context.request().getParam(emailParam);
        final Client client = context.get("client");
        Map<String, String> requestParams = new HashMap<>();
        requestParams.put(Parameters.CLIENT_ID, client.getClientId());

        userService.forgotPassword(email, client, getAuthenticatedUser(context))
                .subscribe(
                        () -> {
                            requestParams.put("success", "forgot_password_completed");
                            redirectToPage(context, requestParams);
                        },
                        error -> {
                            if (error instanceof UserNotFoundException) {
                                requestParams.put("warning", "user_not_found");
                                redirectToPage(context, requestParams);
                            } else {
                                requestParams.put("error", error.getMessage());
                                redirectToPage(context, requestParams, error);
                            }
                        });
    }

    @Override
    protected User getAuthenticatedUser(RoutingContext routingContext) {
        // override principal user
        User principal = new DefaultUser(routingContext.request().getParam(emailParam));
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(Claims.ip_address, remoteAddress(routingContext.request()));
        additionalInformation.put(Claims.user_agent, userAgent(routingContext.request()));
        additionalInformation.put(Claims.domain, domain.getId());
        ((DefaultUser) principal).setAdditionalInformation(additionalInformation);
        return principal;
    }
}

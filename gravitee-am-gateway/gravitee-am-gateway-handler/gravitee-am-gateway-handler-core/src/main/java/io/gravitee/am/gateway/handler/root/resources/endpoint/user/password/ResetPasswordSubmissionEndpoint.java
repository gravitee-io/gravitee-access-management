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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserRequestHandler;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResetPasswordSubmissionEndpoint extends UserRequestHandler {

    private static final String passwordParam = "password";
    private UserService userService;

    public ResetPasswordSubmissionEndpoint(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {
        final String password = context.request().getParam(passwordParam);
        User user = context.get("user");
        user.setPassword(password);

        // add client_id parameter for future use
        Client client = context.get("client");
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(Parameters.CLIENT_ID, client.getClientId());

        userService.resetPassword(user, client, getAuthenticatedUser(context))
                .subscribe(
                        () -> {
                            queryParams.put("success", "reset_password_completed");
                            redirectToPage(context, queryParams);
                        },
                        error -> {
                            queryParams.put("error", "reset_password_failed");
                            redirectToPage(context, queryParams, error);
                        });
    }
}

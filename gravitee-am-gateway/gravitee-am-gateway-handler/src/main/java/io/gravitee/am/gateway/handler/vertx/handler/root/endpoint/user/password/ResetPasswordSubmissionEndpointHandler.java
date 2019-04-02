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
package io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.password;

import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.UserRequestHandler;
import io.gravitee.am.model.User;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResetPasswordSubmissionEndpointHandler extends UserRequestHandler {

    private static final String passwordParam = "password";
    private UserService userService;

    public ResetPasswordSubmissionEndpointHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {
        final String password = context.request().getParam(passwordParam);
        User user = context.get("user");
        user.setPassword(password);

        userService.resetPassword(user, getAuthenticatedUser(context))
                .subscribe(
                        () -> redirectToPage(context, Collections.singletonMap("success", "reset_password_completed")),
                        error -> redirectToPage(context, Collections.singletonMap("error", "reset_password_failed"), error));
    }
}

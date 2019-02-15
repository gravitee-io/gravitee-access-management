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
package io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.register;

import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.UserRequestHandler;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterSubmissionEndpointHandler extends UserRequestHandler {

    private static final String ERROR_PARAM = "error";
    private static final String SUCCESS_PARAM = "success";
    private static final String WARNING_PARAM = "warning";
    private static final String CLIENT_ID_PARAM = "client_id";
    private UserService userService;

    public RegisterSubmissionEndpointHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {
        MultiMap params = context.request().formAttributes();

        Map<String, String> queryParams = new HashMap<>();
        if (context.request().getParam(CLIENT_ID_PARAM) != null) {
            queryParams.put(CLIENT_ID_PARAM, context.request().getParam(CLIENT_ID_PARAM));
        }

        userService.register(convert(params))
                .subscribe(
                        user -> {
                            queryParams.put(SUCCESS_PARAM, "registration_succeed");
                            redirectToPage(context, queryParams);
                        },
                        error -> {
                            if (error instanceof UserAlreadyExistsException) {
                                queryParams.put(WARNING_PARAM, "user_already_exists");
                                redirectToPage(context, queryParams);
                            } else {
                                queryParams.put(ERROR_PARAM, "registration_failed");
                                redirectToPage(context, queryParams, error);
                            }
                        });

    }

    private User convert(MultiMap params) {
        User user = new User();
        user.setUsername(params.get("username"));
        user.setFirstName(params.get("firstName"));
        user.setLastName(params.get("lastName"));
        user.setEmail(params.get("email"));
        user.setPassword(params.get("password"));

        return user;


    }
}

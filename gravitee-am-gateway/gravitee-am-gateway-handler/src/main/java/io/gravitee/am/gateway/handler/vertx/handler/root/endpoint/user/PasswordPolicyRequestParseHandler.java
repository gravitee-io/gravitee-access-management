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
package io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user;

import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordPolicyRequestParseHandler extends UserRequestHandler {

    public static final String PASSWORD_PARAM = "password";
    public static final String TOKEN_PARAM = "token";
    public static final String WARNING_PARAM = "warning";
    public static final String CLIENT_ID_PARAM = "client_id";
    private PasswordValidator passwordValidator;

    public PasswordPolicyRequestParseHandler(PasswordValidator passwordValidator) {
        this.passwordValidator = passwordValidator;
    }

    @Override
    public void handle(RoutingContext context) {
        if (!passwordValidator.validate(context.request().getParam(PASSWORD_PARAM))) {
            Map<String, String> parameters = new HashMap<>();
            if (context.request().getParam(CLIENT_ID_PARAM) != null) {
                parameters.put(CLIENT_ID_PARAM, context.request().getParam(CLIENT_ID_PARAM));
            }
            if (context.request().getParam(TOKEN_PARAM) != null) {
                parameters.put(TOKEN_PARAM, context.request().getParam(TOKEN_PARAM));
            }
            parameters.put(WARNING_PARAM, "invalid_password_value");
            redirectToPage(context, parameters);
        } else {
            context.next();
        }
    }
}

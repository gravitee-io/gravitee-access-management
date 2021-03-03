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
package io.gravitee.am.gateway.handler.root.resources.handler.user;

import io.gravitee.am.common.exception.uma.InvalidPasswordException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.gravitee.am.service.utils.PasswordUtils;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordPolicyRequestParseHandler extends UserRequestHandler {

    private final PasswordValidator passwordValidator;
    private final Domain domain;

    public PasswordPolicyRequestParseHandler(PasswordValidator passwordValidator, Domain domain) {
        this.passwordValidator = passwordValidator;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest request = context.request();
        String password = request.getParam(ConstantKeys.PASSWORD_PARAM_KEY);
        MultiMap queryParams = RequestUtils.getCleanedQueryParams(request);

        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        Optional<PasswordSettings> passwordSettings = PasswordSettings.getInstance(client, this.domain);

        if (passwordSettings.isPresent()) {
            try {
                PasswordUtils.validate(password, passwordSettings.get());
                context.next();
            } catch (InvalidPasswordException e) {
                Optional.ofNullable(context.request().getParam(Parameters.CLIENT_ID)).ifPresent(t -> queryParams.set(Parameters.CLIENT_ID, t));
                warningRedirection(context, queryParams, e.getErrorKey());
            }
            return;
        }

        if (this.passwordValidator.isValid(password)) {
            context.next();
        } else {
            warningRedirection(context, queryParams, "invalid_password_value");
        }
    }

    private void warningRedirection(RoutingContext context, MultiMap queryParams, String warningMsgKey) {
        Optional.ofNullable(context.request().getParam(ConstantKeys.TOKEN_PARAM_KEY)).ifPresent(t -> queryParams.set(ConstantKeys.TOKEN_PARAM_KEY, t));
        queryParams.set(ConstantKeys.WARNING_PARAM_KEY, warningMsgKey);
        redirectToPage(context, queryParams);
    }
}

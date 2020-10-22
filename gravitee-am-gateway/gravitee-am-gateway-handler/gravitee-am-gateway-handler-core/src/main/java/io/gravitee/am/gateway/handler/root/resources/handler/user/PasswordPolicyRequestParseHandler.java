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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordPolicyRequestParseHandler extends UserRequestHandler {

    private final PasswordValidator passwordValidator;

    public PasswordPolicyRequestParseHandler(PasswordValidator passwordValidator) {
        this.passwordValidator = passwordValidator;
    }

    @Override
    public void handle(RoutingContext context) {
        if (!passwordValidator.validate(context.request().getParam(ConstantKeys.PASSWORD_PARAM_KEY))) {
            MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());
            if (context.request().getParam(Parameters.CLIENT_ID) != null) {
                queryParams.set(Parameters.CLIENT_ID, context.request().getParam(Parameters.CLIENT_ID));
            }
            if (context.request().getParam(ConstantKeys.TOKEN_PARAM_KEY) != null) {
                queryParams.set(ConstantKeys.TOKEN_PARAM_KEY, context.request().getParam(ConstantKeys.TOKEN_PARAM_KEY));
            }
            queryParams.set(ConstantKeys.WARNING_PARAM_KEY, "invalid_password_value");
            redirectToPage(context, queryParams);
        } else {
            context.next();
        }
    }
}

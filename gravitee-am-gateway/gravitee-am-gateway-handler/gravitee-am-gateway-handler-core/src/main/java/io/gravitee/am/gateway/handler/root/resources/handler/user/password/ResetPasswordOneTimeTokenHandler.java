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

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserRequestHandler;
import io.gravitee.am.model.User;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResetPasswordOneTimeTokenHandler extends UserRequestHandler {

    private final static Logger logger = LoggerFactory.getLogger(ResetPasswordOneTimeTokenHandler.class);

    @Override
    public void handle(RoutingContext context) {
        final String success = context.request().getParam(ConstantKeys.SUCCESS_PARAM_KEY);
        final String warning = context.request().getParam(ConstantKeys.WARNING_PARAM_KEY);
        final String error = context.request().getParam(ConstantKeys.ERROR_PARAM_KEY);
        final User user = context.get(ConstantKeys.USER_CONTEXT_KEY);
        final JWT jwt = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());

        // user has been redirected due to success, warn or error, continue
        if (success != null || warning != null || error != null) {
            context.next();
            return;
        }

        // first time the user resets its password, continue
        if (user.getLastPasswordReset() == null) {
            context.next();
            return;
        }

        // the current token has already been used for the action
        // redirect user to the reset password page with an error
        if (Instant.ofEpochSecond(jwt.getIat()).isBefore(user.getLastPasswordReset().toInstant())) {
            logger.debug("Token has already been used for reset password action, skip it.");
            queryParams.set(ConstantKeys.ERROR_PARAM_KEY, "invalid_token");
            redirectToPage(context, queryParams);
            return;
        }

        // continue
        context.next();
    }
}

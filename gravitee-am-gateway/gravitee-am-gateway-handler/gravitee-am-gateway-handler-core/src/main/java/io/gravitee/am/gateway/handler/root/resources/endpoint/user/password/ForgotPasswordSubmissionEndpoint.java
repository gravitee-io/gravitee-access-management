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

import io.gravitee.am.common.exception.authentication.AccountStatusException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserRequestHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.ForgotPasswordParameters;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.EnforceUserIdentityException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.impl.user.activity.utils.ConsentUtils;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.FORGOT_PASSWORD_CONFIRM;
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveUserAgent;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ForgotPasswordSubmissionEndpoint extends UserRequestHandler {

    private final UserService userService;
    private final Domain domain;

    public ForgotPasswordSubmissionEndpoint(UserService userService, Domain domain) {
        this.userService = userService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        final String email = context.request().getParam(ConstantKeys.EMAIL_PARAM_KEY);
        final String username = context.request().getParam(ConstantKeys.USERNAME_PARAM_KEY);
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());

        AccountSettings settings = AccountSettings.getInstance(domain, client);

        final ForgotPasswordParameters parameters = new ForgotPasswordParameters(email, username, settings != null && settings.isResetPasswordCustomForm(), settings != null && settings.isResetPasswordConfirmIdentity());
        userService.forgotPassword(parameters, client, getAuthenticatedUser(context))
                .subscribe(
                        () -> {
                            queryParams.set(ConstantKeys.SUCCESS_PARAM_KEY, "forgot_password_completed");
                            redirectToPage(context, queryParams);
                        },
                        error -> {
                            // we don't want to expose potential security leaks such as guessing existing users
                            // the actual error continue to be stored in the audit logs
                            if (error instanceof UserNotFoundException || error instanceof AccountStatusException) {
                                queryParams.set(ConstantKeys.SUCCESS_PARAM_KEY, "forgot_password_completed");
                                redirectToPage(context, queryParams);
                            } else if (error instanceof EnforceUserIdentityException) {
                                if (settings.isResetPasswordConfirmIdentity()) {
                                    queryParams.set(ConstantKeys.WARNING_PARAM_KEY, FORGOT_PASSWORD_CONFIRM);
                                } else {
                                    queryParams.set(ConstantKeys.SUCCESS_PARAM_KEY, "forgot_password_completed");
                                }
                                redirectToPage(context, queryParams);
                            } else {
                                queryParams.set(ConstantKeys.ERROR_PARAM_KEY, "forgot_password_failed");
                                redirectToPage(context, queryParams, error);
                            }
                        });
    }

    @Override
    protected User getAuthenticatedUser(RoutingContext routingContext) {
        // override principal user
        DefaultUser principal = new DefaultUser(routingContext.request().getParam(ConstantKeys.EMAIL_PARAM_KEY));
        Map<String, Object> additionalInformation = new HashMap<>();
        if (canSaveIp(routingContext)) {
            additionalInformation.put(Claims.ip_address, RequestUtils.remoteAddress(routingContext.request()));
        }
        if (canSaveUserAgent(routingContext)) {
            additionalInformation.put(Claims.user_agent, RequestUtils.userAgent(routingContext.request()));
        }
        additionalInformation.put(Claims.domain, domain.getId());
        principal.setAdditionalInformation(additionalInformation);
        return principal;
    }
}

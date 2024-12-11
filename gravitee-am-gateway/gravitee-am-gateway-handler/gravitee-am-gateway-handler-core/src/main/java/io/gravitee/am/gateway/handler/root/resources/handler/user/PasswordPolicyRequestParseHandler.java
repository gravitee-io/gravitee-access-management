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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.root.service.user.UserRegistrationIdpResolver;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordPolicyRequestParseHandler extends UserRequestHandler {

    private final Domain domain;
    private final PasswordService passwordService;
    private final PasswordPolicyManager passwordPolicyManager;
    private final IdentityProviderManager identityProviderManager;
    private final AuditService auditService;
    private final String eventType;

    public PasswordPolicyRequestParseHandler(PasswordService passwordService, PasswordPolicyManager passwordPolicyManager, IdentityProviderManager identityProviderManager, Domain domain, AuditService auditService, String eventType) {
        this.identityProviderManager = identityProviderManager;
        this.passwordPolicyManager = passwordPolicyManager;
        this.passwordService = passwordService;
        this.domain = domain;
        this.auditService = auditService;
        this.eventType = eventType;
    }

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest request = context.request();
        String password = request.getParam(ConstantKeys.PASSWORD_PARAM_KEY);
        MultiMap queryParams = RequestUtils.getCleanedQueryParams(request);
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        User user = getUser(context, client);
        String source = Optional.ofNullable(user.getSource())
                .orElseGet(() -> UserRegistrationIdpResolver.getRegistrationIdp(domain, client));
        IdentityProvider provider = identityProviderManager.getIdentityProvider(source);
        try {
            passwordService.validate(password, passwordPolicyManager.getPolicy(client, provider).orElse(null), user);
            context.next();
        } catch (InvalidPasswordException e) {
            Optional.ofNullable(context.request().getParam(Parameters.CLIENT_ID)).ifPresent(t -> queryParams.set(Parameters.CLIENT_ID, t));
            if (user.getReferenceType() == null) {
                user.setReferenceType(ReferenceType.DOMAIN);
            }
            if (user.getReferenceId() == null) {
                user.setReferenceId(domain.getId());
            }
            Throwable exception = new InvalidPasswordException("The provided password does not meet the password policy requirements.");
            auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(eventType).user(user).throwable(exception));
            warningRedirection(context, queryParams, e.getErrorKey());
        }
    }

    private User getUser(RoutingContext context, Client client) {
        //User is connected
        User user = context.get(ConstantKeys.USER_CONTEXT_KEY);
        if (user != null) {
            return user;
        }
        //We use contact information from the form
        MultiMap params = context.request().formAttributes();
        return convert(params, client);
    }

    private void warningRedirection(RoutingContext context, MultiMap queryParams, String warningMsgKey) {
        Optional.ofNullable(context.request().getParam(ConstantKeys.TOKEN_PARAM_KEY)).ifPresent(t -> queryParams.set(ConstantKeys.TOKEN_PARAM_KEY, t));
        queryParams.set(ConstantKeys.WARNING_PARAM_KEY, warningMsgKey);
        redirectToPage(context, queryParams);
    }
}

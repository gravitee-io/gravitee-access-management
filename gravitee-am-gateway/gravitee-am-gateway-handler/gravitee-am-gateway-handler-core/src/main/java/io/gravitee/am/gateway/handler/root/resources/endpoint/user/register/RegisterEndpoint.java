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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.register;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.gateway.handler.root.service.user.UserRegistrationIdpResolver;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.model.Template.IDENTIFIER_FIRST_LOGIN;
import static io.gravitee.am.model.Template.LOGIN;
import static io.gravitee.am.model.Template.REGISTRATION;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterEndpoint.class);
    private static final String TEMPLATE_ERROR_MESSAGE = "Unable to render registration page";

    private final Domain domain;
    private final BotDetectionManager botDetectionManager;
    private final PasswordPolicyManager passwordPolicyManager;
    private final IdentityProviderManager identityProviderManager;
    private final DeviceIdentifierManager deviceIdentifierManager;

    public RegisterEndpoint(TemplateEngine engine, Domain domain, BotDetectionManager botDetectionManager, PasswordPolicyManager passwordPolicyManager, IdentityProviderManager identityProviderManager, DeviceIdentifierManager deviceIdentifierManager) {
        super(engine);
        this.domain = domain;
        this.botDetectionManager = botDetectionManager;
        this.passwordPolicyManager = passwordPolicyManager;
        this.identityProviderManager = identityProviderManager;
        this.deviceIdentifierManager = deviceIdentifierManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        copyValue(request, routingContext, ConstantKeys.SUCCESS_PARAM_KEY);
        copyValue(request, routingContext, ConstantKeys.WARNING_PARAM_KEY);
        Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        AccountSettings.getInstance(client, domain)
                .ifPresent(accountSettings -> routingContext.put(ConstantKeys.TEMPLATE_VERIFY_REGISTRATION_ACCOUNT_KEY, accountSettings.isSendVerifyRegistrationAccountEmail()));

        String registrationIdp = UserRegistrationIdpResolver.getRegistrationIdp(domain, client);
        IdentityProvider identityProvider = identityProviderManager.getIdentityProvider(registrationIdp);

        passwordPolicyManager.getPolicy(client, identityProvider).ifPresent(v -> routingContext.put(ConstantKeys.PASSWORD_SETTINGS_PARAM_KEY, v));
        String error = request.getParam(ConstantKeys.ERROR_PARAM_KEY);
        String errorDescription = request.getParam(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY);

        String clientId = request.getParam(Parameters.CLIENT_ID);
        // add query params to context
        routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);
        routingContext.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);

        // put parameters in context (backward compatibility)
        var params = new HashMap<String, String>();
        params.computeIfAbsent(Parameters.CLIENT_ID, val -> clientId);
        params.computeIfAbsent(ConstantKeys.ERROR_PARAM_KEY, val -> error);
        params.computeIfAbsent(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, val -> errorDescription);
        routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, params);

        var optionalSettings = ofNullable(LoginSettings.getInstance(domain, client));
        var isIdentifierFirstEnabled = optionalSettings.map(LoginSettings::isIdentifierFirstEnabled).orElse(false);

        MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        final String loginActionKey = routingContext.get(CONTEXT_PATH) + (isIdentifierFirstEnabled ? IDENTIFIER_FIRST_LOGIN.redirectUri() : LOGIN.redirectUri());
        routingContext.put(ConstantKeys.ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true));
        routingContext.put(ConstantKeys.LOGIN_ACTION_KEY, resolveProxyRequest(routingContext.request(), loginActionKey, queryParams, true));

        final Map<String, Object> data = generateData(routingContext, domain, client);
        data.putAll(botDetectionManager.getTemplateVariables(domain, client));
        data.putAll(deviceIdentifierManager.getTemplateVariables(client));
        // render the registration confirmation page
        this.renderPage(routingContext, data, client, logger, TEMPLATE_ERROR_MESSAGE);
    }

    @Override
    public String getTemplateSuffix() {
        return REGISTRATION.template();
    }
}

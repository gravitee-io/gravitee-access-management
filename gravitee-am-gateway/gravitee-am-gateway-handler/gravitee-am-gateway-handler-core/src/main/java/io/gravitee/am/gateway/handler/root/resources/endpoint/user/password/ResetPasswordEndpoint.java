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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.PASSWORD_HISTORY;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORD_VALIDATION;
import static io.gravitee.am.common.web.UriBuilder.encodeURIComponent;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResetPasswordEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(ResetPasswordEndpoint.class);

    private final Domain domain;

    private final IdentityProviderManager identityProviderManager;
    private final PasswordPolicyManager passwordPolicyManager;
    private final DeviceIdentifierManager deviceIdentifierManager;

    public ResetPasswordEndpoint(TemplateEngine engine, Domain domain, PasswordPolicyManager passwordPolicyManager, IdentityProviderManager providerManager, DeviceIdentifierManager deviceIdentifierManager) {
        super(engine);
        this.domain = domain;
        this.passwordPolicyManager = passwordPolicyManager;
        this.identityProviderManager = providerManager;
        this.deviceIdentifierManager = deviceIdentifierManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        // retrieve client (if exists)
        Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        // retrieve user profile to be able to display password policy rules
        // the user profile can be null when the resetPassword has been done
        // and the user is redirected to the resetPassword page to render a confirmation
        User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final var provider = user != null ? identityProviderManager.getIdentityProvider(user.getSource()) : null;
        passwordPolicyManager.getPolicy(client, provider).ifPresent(v -> routingContext.put(ConstantKeys.PASSWORD_SETTINGS_PARAM_KEY, v));

        String error = request.getParam(ConstantKeys.ERROR_PARAM_KEY);
        routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);

        String errorDescription = request.getParam(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY);
        routingContext.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);

        copyValue(request, routingContext, ConstantKeys.SUCCESS_PARAM_KEY);
        copyValue(request, routingContext, ConstantKeys.WARNING_PARAM_KEY);
        // add query params to context
        copyValue(request, routingContext, ConstantKeys.TOKEN_PARAM_KEY);

        // put parameters in context (backward compatibility)
        final Map<String, String> params = new HashMap<>();
        params.computeIfAbsent(ConstantKeys.ERROR_PARAM_KEY, val -> error);
        params.computeIfAbsent(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, val -> errorDescription);
        routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, params);

        final Map<String, String> actionParams = (client != null) ? Map.of(Parameters.CLIENT_ID, encodeURIComponent(client.getClientId())) : Map.of();
        routingContext.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), actionParams));
        routingContext.put(PASSWORD_HISTORY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/passwordHistory", actionParams, true));
        routingContext.put(PASSWORD_VALIDATION, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/passwordValidation", actionParams, true));

        // render the reset password page
        final var data = generateData(routingContext, domain, client);
        data.putAll(deviceIdentifierManager.getTemplateVariables(client));
        this.renderPage(routingContext, data, client, logger, "Unable to render reset password page");
    }


    @Override
    public String getTemplateSuffix() {
        return Template.RESET_PASSWORD.template();
    }
}

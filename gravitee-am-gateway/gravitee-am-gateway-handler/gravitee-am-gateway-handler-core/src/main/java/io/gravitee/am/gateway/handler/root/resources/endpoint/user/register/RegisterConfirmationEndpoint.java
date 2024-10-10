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
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserRequestHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserRegistrationIdpResolver;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORD_VALIDATION;
import static io.gravitee.am.common.web.UriBuilder.encodeURIComponent;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterConfirmationEndpoint extends UserRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(RegisterConfirmationEndpoint.class);

    private final ThymeleafTemplateEngine engine;
    private final Domain domain;
    private final DeviceIdentifierManager deviceIdentifierManager;
    private final PasswordPolicyManager passwordPolicyManager;
    private final IdentityProviderManager identityProviderManager;

    public RegisterConfirmationEndpoint(ThymeleafTemplateEngine thymeleafTemplateEngine,
                                        Domain domain,
                                        DeviceIdentifierManager deviceIdentifierManager,
                                        PasswordPolicyManager passwordPolicyManager,
                                        IdentityProviderManager identityProviderManager) {
        this.engine = thymeleafTemplateEngine;
        this.domain = domain;
        this.deviceIdentifierManager = deviceIdentifierManager;
        this.passwordPolicyManager = passwordPolicyManager;
        this.identityProviderManager = identityProviderManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        final String error = request.getParam(ConstantKeys.ERROR_PARAM_KEY);
        final String errorDescription = request.getParam(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY);
        final String success = request.getParam(ConstantKeys.SUCCESS_PARAM_KEY);
        final String warning = request.getParam(ConstantKeys.WARNING_PARAM_KEY);
        final String token = request.getParam(ConstantKeys.TOKEN_PARAM_KEY);
        // add query params to context
        routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);
        routingContext.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
        routingContext.put(ConstantKeys.SUCCESS_PARAM_KEY, success);
        routingContext.put(ConstantKeys.WARNING_PARAM_KEY, warning);
        routingContext.put(ConstantKeys.TOKEN_PARAM_KEY, token);

        // put parameters in context (backward compatibility)
        Map<String, String> params = new HashMap<>();
        params.computeIfAbsent(ConstantKeys.ERROR_PARAM_KEY, val -> error);
        params.computeIfAbsent(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, val -> errorDescription);
        routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, params);

        // retrieve user who want to register
        User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);

        // retrieve client (if exists)
        Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        String registrationIdp = UserRegistrationIdpResolver.getRegistrationIdpForUser(domain, client, user);
        IdentityProvider identityProvider = identityProviderManager.getIdentityProvider(registrationIdp);

        passwordPolicyManager.getPolicy(client, identityProvider)
                .ifPresent(v -> routingContext.put(ConstantKeys.PASSWORD_SETTINGS_PARAM_KEY, v));

        // check if user has already completed its registration
        if (user != null && user.isPreRegistration() && user.isRegistrationCompleted()) {
            MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            queryParams.set(ConstantKeys.ERROR_PARAM_KEY, "invalid_registration_context");
            if(routingContext.session() != null){
                routingContext.session().put(ERROR_HASH, HashUtil.generateSHA256("invalid_registration_context"));
            }
            redirectToPage(routingContext, queryParams);
            return;
        }

        final Map<String, String> actionParams = (client != null) ? Map.of(Parameters.CLIENT_ID, encodeURIComponent(client.getClientId())) : Map.of();
        routingContext.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), actionParams));
        routingContext.put(PASSWORD_VALIDATION, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/passwordValidation", actionParams, true));

        // render the registration confirmation page
        final var data = generateData(routingContext, domain, client);
        data.putAll(deviceIdentifierManager.getTemplateVariables(client));
        engine.render(data, getTemplateFileName(client))
                .subscribe(
                        buffer -> {
                            routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                            routingContext.response().end(buffer);
                        },
                        throwable -> {
                            logger.error("Unable to render registration confirmation page", throwable);
                            routingContext.fail(throwable.getCause());
                        }
                );
    }

    @Override
    public String getTemplateSuffix() {
        return Template.REGISTRATION_CONFIRMATION.template();
    }
}

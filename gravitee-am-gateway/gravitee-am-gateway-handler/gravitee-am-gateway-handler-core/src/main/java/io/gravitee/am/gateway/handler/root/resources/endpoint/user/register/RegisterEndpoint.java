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
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.model.Template.LOGIN;
import static io.gravitee.am.model.Template.IDENTIFIER_FIRST_LOGIN;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterEndpoint.class);

    private final Domain domain;
    private final BotDetectionManager botDetectionManager;

    public RegisterEndpoint(TemplateEngine engine, Domain domain, BotDetectionManager botDetectionManager) {
        super(engine);
        this.domain = domain;
        this.botDetectionManager = botDetectionManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        copyValue(request, routingContext, ConstantKeys.SUCCESS_PARAM_KEY);
        copyValue(request, routingContext, ConstantKeys.WARNING_PARAM_KEY);
        Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        PasswordSettings.getInstance(client, domain).ifPresent(v -> routingContext.put(ConstantKeys.PASSWORD_SETTINGS_PARAM_KEY, v));

        String error = request.getParam(ConstantKeys.ERROR_PARAM_KEY);
        String errorDescription = request.getParam(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY);

        String clientId = request.getParam(Parameters.CLIENT_ID);
        // add query params to context
        routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);
        routingContext.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);

        // put parameters in context (backward compatibility)
        Map<String, String> params = new HashMap<>();
        params.computeIfAbsent(Parameters.CLIENT_ID, val -> clientId);
        params.computeIfAbsent(ConstantKeys.ERROR_PARAM_KEY, val -> error);
        params.computeIfAbsent(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, val -> errorDescription);
        routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, params);

        var optionalSettings = Optional.ofNullable(LoginSettings.getInstance(domain, client)).filter(Objects::nonNull);
        var isIdentifierFirstEnabled = optionalSettings.map(LoginSettings::isIdentifierFirstEnabled).orElse(false);

        MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        final String loginActionKey = routingContext.get(CONTEXT_PATH) + (isIdentifierFirstEnabled ? IDENTIFIER_FIRST_LOGIN.redirectUri() : LOGIN.redirectUri());
        routingContext.put(ConstantKeys.ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true));
        routingContext.put(ConstantKeys.LOGIN_ACTION_KEY, resolveProxyRequest(routingContext.request(), loginActionKey, queryParams, true));

        final Map<String, Object> data = generateData(routingContext, domain, client);
        data.putAll(botDetectionManager.getTemplateVariables(domain, client));

        // render the registration confirmation page
        this.renderPage(routingContext, data, client, logger, "Unable to render registration page");
    }

    @Override
    public String getTemplateSuffix() {
        return "registration";
    }
}

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
package io.gravitee.am.gateway.handler.root.resources.endpoint.identifierfirst;

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.gateway.handler.context.provider.ClientProperties;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Domain;
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

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.ACTION_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.model.Template.IDENTIFIER_FIRST_LOGIN;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentifierFirstLoginEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(IdentifierFirstLoginEndpoint.class);
    private static final String REQUEST_CONTEXT_KEY = "request";
    private static final String ALLOW_REGISTER_CONTEXT_KEY = "allowRegister";
    private static final String ALLOW_PASSWORDLESS_CONTEXT_KEY = "allowPasswordless";
    private static final String REGISTER_ACTION_KEY = "registerAction";
    private static final String WEBAUTHN_ACTION_KEY = "passwordlessAction";

    private final Domain domain;
    private final BotDetectionManager botDetectionManager;

    public IdentifierFirstLoginEndpoint(TemplateEngine templateEngine, Domain domain, BotDetectionManager botDetectionManager) {
        super(templateEngine);
        this.domain = domain;
        this.botDetectionManager = botDetectionManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        prepareContext(routingContext, client);
        renderLoginPage(routingContext, client);
    }

    private void prepareContext(RoutingContext routingContext, Client client) {
        final HttpServerRequest request = routingContext.request();
        // remove sensible client data
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, new ClientProperties(client));
        // put domain in context data
        routingContext.put(ConstantKeys.DOMAIN_CONTEXT_KEY, domain);
        // put request in context
        EvaluableRequest evaluableRequest = new EvaluableRequest(new VertxHttpServerRequest(request.getDelegate(), true));
        routingContext.put(REQUEST_CONTEXT_KEY, evaluableRequest);

        LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        var optionalSettings = ofNullable(loginSettings).filter(Objects::nonNull);
        routingContext.put(ALLOW_REGISTER_CONTEXT_KEY, optionalSettings.map(LoginSettings::isRegisterEnabled).orElse(false));
        routingContext.put(ALLOW_PASSWORDLESS_CONTEXT_KEY, optionalSettings.map(LoginSettings::isPasswordlessEnabled).orElse(false));

        // put error in context
        final String error = request.getParam(ConstantKeys.ERROR_PARAM_KEY);
        final String errorDescription = request.getParam(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY);
        routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);
        routingContext.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);

        // put parameters in context (backward compatibility)
        Map<String, String> params = new HashMap<>(evaluableRequest.getParams().toSingleValueMap());
        params.put(ConstantKeys.ERROR_PARAM_KEY, error);
        params.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
        routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, params);

        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(request);
        routingContext.put(ACTION_KEY, resolveProxyRequest(request, routingContext.get(CONTEXT_PATH) + "/login", queryParams, true));
        routingContext.put(REGISTER_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/register", queryParams, true));
        routingContext.put(WEBAUTHN_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/webauthn/login", queryParams, true));
    }

    private void renderLoginPage(RoutingContext routingContext, Client client) {
        final Map<String, Object> data = new HashMap<>(routingContext.data());
        data.putAll(botDetectionManager.getTemplateVariables(domain, client));
        this.renderPage(routingContext, data, client, logger, "Unable to render Identifier-first login page");
    }

    @Override
    public String getTemplateSuffix() {
        return IDENTIFIER_FIRST_LOGIN.template();
    }
}

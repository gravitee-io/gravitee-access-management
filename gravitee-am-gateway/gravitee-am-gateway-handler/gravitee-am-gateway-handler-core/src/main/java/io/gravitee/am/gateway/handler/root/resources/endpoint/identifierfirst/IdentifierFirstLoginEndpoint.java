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

import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.utils.StaticEnvironmentProvider;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
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
import java.util.Objects;

import static io.gravitee.am.common.oidc.Parameters.REMEMBER_ME_HINT;
import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.model.Template.IDENTIFIER_FIRST_LOGIN;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentifierFirstLoginEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(IdentifierFirstLoginEndpoint.class);

    private final Domain domain;
    private final BotDetectionManager botDetectionManager;

    public IdentifierFirstLoginEndpoint(TemplateEngine templateEngine, Domain domain, BotDetectionManager botDetectionManager) {
        super(templateEngine);
        this.domain = domain;
        this.botDetectionManager = botDetectionManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                renderLoginPage(routingContext);
                break;
            case "POST":
                redirect(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void renderLoginPage(RoutingContext routingContext) {
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        // remove sensible client data
        routingContext.put(CLIENT_CONTEXT_KEY, new ClientProperties(client));
        // put domain in context data
        routingContext.put(DOMAIN_CONTEXT_KEY, domain);
        // put request in context
        final HttpServerRequest request = routingContext.request();
        EvaluableRequest evaluableRequest = new EvaluableRequest(new VertxHttpServerRequest(request.getDelegate(), true));
        routingContext.put(REQUEST_CONTEXT_KEY, evaluableRequest);

        // put login settings in context
        LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
        var optionalSettings = ofNullable(loginSettings).filter(Objects::nonNull);
        var accountSettingsOptionalSettings = ofNullable(accountSettings).filter(Objects::nonNull);
        routingContext.put(ALLOW_FORGOT_PASSWORD_CONTEXT_KEY, optionalSettings.map(LoginSettings::isForgotPasswordEnabled).orElse(false));
        routingContext.put(ALLOW_REGISTER_CONTEXT_KEY, optionalSettings.map(LoginSettings::isRegisterEnabled).orElse(false));
        routingContext.put(ALLOW_PASSWORDLESS_CONTEXT_KEY, optionalSettings.map(LoginSettings::isPasswordlessEnabled).orElse(false));
        routingContext.put(TEMPLATE_KEY_ALLOW_CBA_CONTEXT_KEY, optionalSettings.map(LoginSettings::isCertificateBasedAuthEnabled).orElse(false));
        routingContext.put(TEMPLATE_KEY_ALLOW_MAGIC_LINK_CONTEXT_KEY, optionalSettings.map(LoginSettings::isMagicLinkAuthEnabled).orElse(false));
        routingContext.put(TEMPLATE_KEY_REMEMBER_ME_KEY, accountSettingsOptionalSettings.map(AccountSettings::isRememberMe).orElse(false));

        // put error in context
        final String error = request.getParam(ERROR_PARAM_KEY);
        final String errorDescription = request.getParam(ERROR_DESCRIPTION_PARAM_KEY);
        routingContext.put(ERROR_PARAM_KEY, error);
        routingContext.put(ERROR_DESCRIPTION_PARAM_KEY, errorDescription);

        // put parameters in context (backward compatibility)
        Map<String, String> params = new HashMap<>(evaluableRequest.getParams().toSingleValueMap());
        params.put(ERROR_PARAM_KEY, error);
        params.put(ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
        final String loginHint = routingContext.request().getParam(Parameters.LOGIN_HINT);
        if (loginHint != null) {
            params.put(ConstantKeys.USERNAME_PARAM_KEY, loginHint);
        }
        routingContext.put(PARAM_CONTEXT_KEY, params);

        // put actions in context
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(request);
        routingContext.put(ACTION_KEY, resolveProxyRequest(request, routingContext.get(CONTEXT_PATH) + "/login/identifier", queryParams, true));
        routingContext.put(REGISTER_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/register", queryParams, true));
        routingContext.put(WEBAUTHN_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/webauthn/login", queryParams, true));
        routingContext.put(FORGOT_ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/forgotPassword", queryParams, true));

        final Map<String, Object> data = generateData(routingContext, domain, client);
        data.putAll(botDetectionManager.getTemplateVariables(domain, client));
        this.renderPage(routingContext, data, client, logger, "Unable to render Identifier-first login page");
    }

    private void redirect(RoutingContext routingContext) {
        final String redirectUrl = routingContext.get(CONTEXT_PATH) + "/login";
        final HttpServerRequest request = routingContext.request();
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(request);
        // login_hint parameter can be duplicated from a previous step, remove it
        queryParams.remove(Parameters.LOGIN_HINT);
        queryParams.remove(REMEMBER_ME_HINT);
        // If parameters is sanitized when generating the redirect URL, then the login-hint requires an extra encoding
        queryParams.add(Parameters.LOGIN_HINT, StaticEnvironmentProvider.sanitizeParametersEncoding() ? UriBuilder.encodeURIComponent(request.getParam(USERNAME_PARAM_KEY)) : request.getParam(USERNAME_PARAM_KEY));
        if (request.getParam(REMEMBER_ME_PARAM_KEY) != null) {
            queryParams.add(REMEMBER_ME_HINT, StaticEnvironmentProvider.sanitizeParametersEncoding() ? UriBuilder.encodeURIComponent(request.getParam(REMEMBER_ME_HINT)) : request.getParam(REMEMBER_ME_HINT));
        }
        final String url = resolveProxyRequest(request, redirectUrl, queryParams, true);
        doRedirect0(routingContext, url);
    }

    private void doRedirect0(RoutingContext routingContext, String url) {
        routingContext.response()
                .putHeader(io.vertx.core.http.HttpHeaders.LOCATION, url)
                .setStatusCode(302)
                .end();
    }

    @Override
    public String getTemplateSuffix() {
        return IDENTIFIER_FIRST_LOGIN.template();
    }
}

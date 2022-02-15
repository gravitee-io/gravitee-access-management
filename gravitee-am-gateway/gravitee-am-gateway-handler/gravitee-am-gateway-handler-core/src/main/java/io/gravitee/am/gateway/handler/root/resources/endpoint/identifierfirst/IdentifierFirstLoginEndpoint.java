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

import com.google.common.base.Strings;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_PROVIDER_CONTEXT_KEY;
import static io.gravitee.am.model.Template.IDENTIFIER_FIRST_LOGIN;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
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
        var optionalSettings = ofNullable(loginSettings).filter(Objects::nonNull);
        routingContext.put(ALLOW_REGISTER_CONTEXT_KEY, optionalSettings.map(LoginSettings::isRegisterEnabled).orElse(false));
        routingContext.put(ALLOW_PASSWORDLESS_CONTEXT_KEY, optionalSettings.map(LoginSettings::isPasswordlessEnabled).orElse(false));

        // put error in context
        final String error = request.getParam(ERROR_PARAM_KEY);
        final String errorDescription = request.getParam(ERROR_DESCRIPTION_PARAM_KEY);
        routingContext.put(ERROR_PARAM_KEY, error);
        routingContext.put(ERROR_DESCRIPTION_PARAM_KEY, errorDescription);

        // put parameters in context (backward compatibility)
        Map<String, String> params = new HashMap<>(evaluableRequest.getParams().toSingleValueMap());
        params.put(ERROR_PARAM_KEY, error);
        params.put(ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
        routingContext.put(PARAM_CONTEXT_KEY, params);

        // put actions in context
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(request);
        routingContext.put(ACTION_KEY, resolveProxyRequest(request, routingContext.get(CONTEXT_PATH) + "/login/identifier", queryParams, true));
        routingContext.put(REGISTER_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/register", queryParams, true));
        routingContext.put(WEBAUTHN_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/webauthn/login", queryParams, true));

        final Map<String, Object> data = generateData(routingContext, domain, client);
        data.putAll(botDetectionManager.getTemplateVariables(domain, client));
        this.renderPage(routingContext, data, client, logger, "Unable to render Identifier-first login page");
    }

    private void redirect(RoutingContext routingContext) {
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        final List<IdentityProvider> socialProviders = routingContext.get(SOCIAL_PROVIDER_CONTEXT_KEY);
        // no social providers configured, continue
        if (socialProviders == null || socialProviders.isEmpty()) {
            doInternalRedirect(routingContext);
            return;
        }

        if (client.getIdentityProviders() == null || client.getIdentityProviders().isEmpty()) {
            doInternalRedirect(routingContext);
            return;
        }

        var appIdpMap = client.getIdentityProviders().stream().collect(Collectors.toMap(
                ApplicationIdentityProvider::getIdentity, Function.identity()
        ));

        var context = new SimpleAuthenticationContext(new VertxHttpServerRequest(routingContext.request().getDelegate()), routingContext.data());
        var templateEngine = context.getTemplateEngine();
        var identityProvider = socialProviders.stream()
                .filter(idp -> appIdpMap.containsKey(idp.getId()))
                .filter(idp -> evaluateRule(appIdpMap.get(idp.getId()), templateEngine, idp))
                .findFirst();

        // no IdP has matched, continue
        if (identityProvider.isEmpty()) {
            doInternalRedirect(routingContext);
            return;
        }

        // else, redirect to the external provider
        doExternalRedirect(routingContext, identityProvider.get());
    }

    private boolean evaluateRule(ApplicationIdentityProvider appIdp, io.gravitee.el.TemplateEngine templateEngine, IdentityProvider idp) {
        var rule = appIdp.getSelectionRule();
        // We keep the same behaviour as before, if there is no rule, no automatic redirect
        if (Strings.isNullOrEmpty(rule) || rule.isBlank()) {
            return false;
        }
        try {
            return templateEngine != null && templateEngine.getValue(rule.trim(), Boolean.class);
        } catch (Exception e) {
            logger.warn("Cannot evaluate the expression [{}] as boolean", rule);
            return false;
        }
    }

    private void doInternalRedirect(RoutingContext routingContext) {
        final String redirectUrl = routingContext.get(CONTEXT_PATH) + "/login";
        final HttpServerRequest request = routingContext.request();
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(request);
        // login_hint parameter can be duplicated from a previous step, remove it
        queryParams.remove(Parameters.LOGIN_HINT);
        queryParams.add(Parameters.LOGIN_HINT, UriBuilder.encodeURIComponent(request.getParam(USERNAME_PARAM_KEY)));
        final String url = UriBuilderRequest.resolveProxyRequest(request, redirectUrl, queryParams, true);
        doRedirect0(routingContext, url);
    }

    private void doExternalRedirect(RoutingContext routingContext, IdentityProvider identityProvider) {
        Map<String, String> urls = routingContext.get(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY);
        UriBuilder uriBuilder = UriBuilder.fromHttpUrl(urls.get(identityProvider.getId()));
        // encode login_hint parameter for external provider (Azure AD replace the '+' sign by a space ' ')
        uriBuilder.addParameter(Parameters.LOGIN_HINT, UriBuilder.encodeURIComponent(routingContext.request().getParam(USERNAME_PARAM_KEY)));
        doRedirect0(routingContext, uriBuilder.buildString());
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

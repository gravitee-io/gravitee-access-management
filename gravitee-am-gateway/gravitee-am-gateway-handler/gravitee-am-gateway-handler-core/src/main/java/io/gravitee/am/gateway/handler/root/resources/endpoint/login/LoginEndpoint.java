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
package io.gravitee.am.gateway.handler.root.resources.endpoint.login;

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.ACTION_KEY;
import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.USERNAME_PARAM_KEY;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils.getCleanedQueryParams;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_PROVIDER_CONTEXT_KEY;
import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginEndpoint.class);
    private static final String ALLOW_FORGOT_PASSWORD_CONTEXT_KEY = "allowForgotPassword";
    private static final String ALLOW_REGISTER_CONTEXT_KEY = "allowRegister";
    private static final String ALLOW_PASSWORDLESS_CONTEXT_KEY = "allowPasswordless";
    private static final String HIDE_FORM_CONTEXT_KEY = "hideLoginForm";
    private static final String IDENTIFIER_FIRST_LOGIN_CONTEXT_KEY = "identifierFirstLoginEnabled";
    private static final String REQUEST_CONTEXT_KEY = "request";
    private static final String FORGOT_ACTION_KEY = "forgotPasswordAction";
    private static final String REGISTER_ACTION_KEY = "registerAction";
    private static final String WEBAUTHN_ACTION_KEY = "passwordlessAction";
    private static final String BACK_TO_LOGIN_IDENTIFIER_ACTION_KEY = "backToLoginIdentifierAction";
    private static final String REDIRECT_TO_LOGIN_IDENTIFIER = "redirectToLoginIdentifier";

    private final Domain domain;
    private final BotDetectionManager botDetectionManager;
    private final DeviceIdentifierManager deviceIdentifierManager;

    public LoginEndpoint(TemplateEngine templateEngine, Domain domain, BotDetectionManager botDetectionManager, DeviceIdentifierManager deviceIdentifierManager) {
        super(templateEngine);
        this.domain = domain;
        this.botDetectionManager = botDetectionManager;
        this.deviceIdentifierManager = deviceIdentifierManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        prepareContext(routingContext, client);
        renderLoginPage(routingContext, client);
    }

    private void prepareContext(RoutingContext routingContext, Client client) {
        // create query params
        final MultiMap queryParams = getCleanedQueryParams(routingContext.request());
        // remove sensible client data
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, new ClientProperties(client));
        // put domain in context data
        routingContext.put(ConstantKeys.DOMAIN_CONTEXT_KEY, domain);
        // put login settings in context data
        LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        var optionalSettings = ofNullable(loginSettings).filter(Objects::nonNull);
        boolean isIdentifierFirstLoginEnabled = optionalSettings.map(LoginSettings::isIdentifierFirstEnabled).orElse(false);

        routingContext.put(ALLOW_FORGOT_PASSWORD_CONTEXT_KEY, optionalSettings.map(LoginSettings::isForgotPasswordEnabled).orElse(false));
        routingContext.put(ALLOW_REGISTER_CONTEXT_KEY, optionalSettings.map(LoginSettings::isRegisterEnabled).orElse(false));
        routingContext.put(ALLOW_PASSWORDLESS_CONTEXT_KEY, optionalSettings.map(LoginSettings::isPasswordlessEnabled).orElse(false));
        routingContext.put(HIDE_FORM_CONTEXT_KEY, optionalSettings.map(LoginSettings::isHideForm).orElse(false));
        routingContext.put(IDENTIFIER_FIRST_LOGIN_CONTEXT_KEY, isIdentifierFirstLoginEnabled);

        if (isIdentifierFirstLoginEnabled) {
            routingContext.put(USERNAME_PARAM_KEY, routingContext.request().getParam(USERNAME_PARAM_KEY));
        }

        // put request in context
        EvaluableRequest evaluableRequest = new EvaluableRequest(new VertxHttpServerRequest(routingContext.request().getDelegate(), true));
        routingContext.put(REQUEST_CONTEXT_KEY, evaluableRequest);

        // put error in context
        final String error = routingContext.request().getParam(ConstantKeys.ERROR_PARAM_KEY);
        final String errorDescription = routingContext.request().getParam(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY);
        routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);
        routingContext.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);

        // put parameters in context (backward compatibility)
        Map<String, String> params = new HashMap<>(evaluableRequest.getParams().toSingleValueMap());
        params.put(ConstantKeys.ERROR_PARAM_KEY, error);
        params.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
        if (!isIdentifierFirstLoginEnabled) {
            params.remove(USERNAME_PARAM_KEY);
        }
        routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, params);

        // create post action url.
        if (isIdentifierFirstLoginEnabled && isNullOrEmpty(routingContext.get(USERNAME_PARAM_KEY))) {
            routingContext.put(REDIRECT_TO_LOGIN_IDENTIFIER, resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/login/identifier", queryParams, true));
        } else {
            routingContext.put(ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true));
            routingContext.put(FORGOT_ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/forgotPassword", queryParams, true));
            routingContext.put(REGISTER_ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/register", queryParams, true));
            routingContext.put(WEBAUTHN_ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/webauthn/login", queryParams, true));

            var queryParamsNoUsername = MultiMap.caseInsensitiveMultiMap();
            queryParamsNoUsername.addAll(queryParams);
            queryParamsNoUsername.remove(USERNAME_PARAM_KEY);
            routingContext.put(BACK_TO_LOGIN_IDENTIFIER_ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/login/identifier", queryParamsNoUsername, true));
        }
    }

    private void renderLoginPage(RoutingContext routingContext, Client client) {
        // we redirect if we don't have the username while being on first login identifier enabled
        final String urlIdentifierFirstRedirect = routingContext.get(REDIRECT_TO_LOGIN_IDENTIFIER);
        if (!isNullOrEmpty(urlIdentifierFirstRedirect)) {
            routingContext.response()
                    .putHeader(io.vertx.core.http.HttpHeaders.LOCATION, urlIdentifierFirstRedirect)
                    .setStatusCode(302)
                    .end();
        } else {
            final Map<String, Object> data = generateData(routingContext, domain, client);
            data.putAll(botDetectionManager.getTemplateVariables(domain, client));
            data.putAll(deviceIdentifierManager.getTemplateVariables(client));

            //we render the page if no redirect is set
            final boolean identifierFirstLoginEnabled = TRUE.equals(routingContext.get(IDENTIFIER_FIRST_LOGIN_CONTEXT_KEY));
            boolean render = identifierFirstLoginEnabled ?
                    redirectAutomaticallyFirstIdentifierLogin(routingContext, data) :
                    redirectAutomaticallySingle(routingContext, data);
            if (render) {
                this.renderPage(routingContext, data, client, logger, "Unable to render login page");
            }
        }
    }

    private boolean redirectAutomaticallyFirstIdentifierLogin(RoutingContext routingContext, Map<String, Object> data) {
        var optionalProviders = ofNullable((List<IdentityProvider>) data.get(SOCIAL_PROVIDER_CONTEXT_KEY));
        final String userName = String.valueOf(data.get(USERNAME_PARAM_KEY));
        String[] usernameDomain = userName.split("@");
        return usernameDomain.length == 1 || optionalProviders.orElse(List.of()).stream().noneMatch(provider -> {
            boolean domainMatches = provider.getDomainWhitelist().stream().anyMatch(usernameDomain[1]::equals);
            if (domainMatches) {
                redirectToExternalProvider(routingContext, data, provider.getId(), "google".equals(provider.getType()));
            }
            return domainMatches;
        });
    }

    private boolean redirectAutomaticallySingle(RoutingContext routingContext, Map<String, Object> data) {
        final List<IdentityProvider> providers = (List<IdentityProvider>) data.get(SOCIAL_PROVIDER_CONTEXT_KEY);
        if (providers != null && TRUE.equals(data.get(HIDE_FORM_CONTEXT_KEY))) {
            if (providers.size() == 1) {
                redirectToExternalProvider(routingContext, data, providers.get(0).getId(), false);
                return false;
            }
        }
        return true;
    }

    private void redirectToExternalProvider(RoutingContext routingContext, Map<String, Object> data, String providerId, boolean forceChoose) {
        Map<String, String> urls = (Map<String, String>) data.get(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY);
        String redirectUrl = urls.get(providerId);
        routingContext.response()
                .putHeader(io.vertx.core.http.HttpHeaders.LOCATION, redirectUrl + (forceChoose ? "&prompt=select_account+consent" : ""))
                .setStatusCode(302)
                .end();
    }

    @Override
    public String getTemplateSuffix() {
        return Template.LOGIN.template();
    }
}

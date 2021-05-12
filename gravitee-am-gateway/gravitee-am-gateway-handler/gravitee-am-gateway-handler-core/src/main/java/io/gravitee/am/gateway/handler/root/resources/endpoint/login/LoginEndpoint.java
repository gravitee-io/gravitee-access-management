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

import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.gateway.handler.context.provider.ClientProperties;
import io.gravitee.am.gateway.handler.manager.form.FormManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginEndpoint.class);
    private static final String ALLOW_FORGOT_PASSWORD_CONTEXT_KEY = "allowForgotPassword";
    private static final String ALLOW_REGISTER_CONTEXT_KEY = "allowRegister";
    private static final String ALLOW_PASSWORDLESS_CONTEXT_KEY = "allowPasswordless";
    private static final String REQUEST_CONTEXT_KEY = "request";
    private static final String FORGOT_ACTION_KEY = "forgotPasswordAction";
    private static final String REGISTER_ACTION_KEY = "registerAction";
    private static final String WEBAUTHN_ACTION_KEY = "passwordlessAction";

    private final ThymeleafTemplateEngine engine;
    private final Domain domain;
    private final BotDetectionManager botDetectionManager;

    public LoginEndpoint(ThymeleafTemplateEngine thymeleafTemplateEngine, Domain domain, BotDetectionManager botDetectionManager) {
        this.engine = thymeleafTemplateEngine;
        this.domain = domain;
        this.botDetectionManager = botDetectionManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        // prepare context
        prepareContext(routingContext, client);

        // render login page
        renderLoginPage(routingContext, client);
    }

    private void prepareContext(RoutingContext routingContext, Client client) {
        // remove sensible client data
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, new ClientProperties(client));
        // put domain in context data
        routingContext.put(ConstantKeys.DOMAIN_CONTEXT_KEY, domain);
        // put login settings in context data
        LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        routingContext.put(ALLOW_FORGOT_PASSWORD_CONTEXT_KEY, loginSettings != null && loginSettings.isForgotPasswordEnabled());
        routingContext.put(ALLOW_REGISTER_CONTEXT_KEY, loginSettings != null && loginSettings.isRegisterEnabled());
        routingContext.put(ALLOW_PASSWORDLESS_CONTEXT_KEY, loginSettings != null && loginSettings.isPasswordlessEnabled());

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
        routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, params);

        // create post action url.
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        routingContext.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true));
        routingContext.put(FORGOT_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/forgotPassword", queryParams, true));
        routingContext.put(REGISTER_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/register", queryParams, true));
        routingContext.put(WEBAUTHN_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/webauthn/login", queryParams, true));
    }

    private void renderLoginPage(RoutingContext routingContext, Client client) {
        // render the login page
        final Map<String, Object> data = new HashMap<>();
        data.putAll(routingContext.data());
        data.putAll(botDetectionManager.getTemplateVariables(domain, client));

        engine.render(data, getTemplateFileName(client), res -> {
            if (res.succeeded()) {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                routingContext.response().end(res.result());
            } else {
                logger.error("Unable to render login page", res.cause());
                routingContext.fail(res.cause());
            }
        });
    }

    private String getTemplateFileName(Client client) {
        return "login" + (client != null ? FormManager.TEMPLATE_NAME_SEPARATOR + client.getId() : "");
    }
}

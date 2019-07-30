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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.context.provider.ClientProperties;
import io.gravitee.am.gateway.handler.form.FormManager;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginEndpoint.class);
    private static final String DOMAIN_CONTEXT_KEY = "domain";
    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String PARAM_CONTEXT_KEY = "param";
    private static final String ERROR_PARAM_KEY = "error";
    private static final String ALLOW_FORGOT_PASSWORD_CONTEXT_KEY = "allowForgotPassword";
    private static final String ALLOW_REGISTER_CONTEXT_KEY = "allowRegister";
    private ThymeleafTemplateEngine engine;
    private Domain domain;

    public LoginEndpoint(ThymeleafTemplateEngine thymeleafTemplateEngine, Domain domain) {
        this.engine = thymeleafTemplateEngine;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);

        // prepare context
        prepareContext(routingContext, client);

        // render login page
        renderLoginPage(routingContext, client);
    }

    private void prepareContext(RoutingContext routingContext, Client client) {
        // remove sensible client data
        routingContext.put(CLIENT_CONTEXT_KEY, new ClientProperties(client));
        // put domain in context data
        routingContext.put(DOMAIN_CONTEXT_KEY, domain);
        // put domain login settings in context data
        routingContext.put(ALLOW_FORGOT_PASSWORD_CONTEXT_KEY, domain.getLoginSettings() == null ? false : domain.getLoginSettings().isForgotPasswordEnabled());
        routingContext.put(ALLOW_REGISTER_CONTEXT_KEY, domain.getLoginSettings() == null ? false : domain.getLoginSettings().isRegisterEnabled());

        // put additional parameter (backward compatibility)
        final String error = routingContext.request().getParam(ERROR_PARAM_KEY);
        Map<String, String> params = new HashMap<>();
        if (error != null) {
            params.put(ERROR_PARAM_KEY, error);
        }
        params.put(Parameters.CLIENT_ID, routingContext.request().getParam(Parameters.CLIENT_ID));
        routingContext.put(PARAM_CONTEXT_KEY, params);
    }

    private void renderLoginPage(RoutingContext routingContext, Client client) {
        // render the login page
        engine.render(routingContext.data(), getTemplateFileName(client), res -> {
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

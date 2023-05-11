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

import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.model.Template.*;
import static io.vertx.core.http.HttpMethod.GET;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterVerifyEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterVerifyEndpoint.class);

    private static final String TEMPLATE_ERROR_MESSAGE = "Unable to render registration verify page";
    private final Domain domain;

    public RegisterVerifyEndpoint(Domain domain, TemplateEngine templateEngine) {
        super(templateEngine);
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        if (GET.equals(request.method())) {
            displayRegisterVerifyPage(routingContext);
        } else {
            routingContext.fail(405);
        }
    }

    private void displayRegisterVerifyPage(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();

        //We remove the token because we don't need it anymore
        routingContext.remove(TOKEN_PARAM_KEY);
        request.params().remove(TOKEN_PARAM_KEY);

        final String requestError = request.params().get(ERROR_PARAM_KEY);
        if (nonNull(requestError)) {
            routingContext.put(ERROR_PARAM_KEY, requestError);
            routingContext.put(ERROR_DESCRIPTION_PARAM_KEY, request.params().get(ERROR_DESCRIPTION_PARAM_KEY));
        }

        if (isNull(routingContext.get(ERROR_PARAM_KEY))) {
            routingContext.put(SUCCESS_PARAM_KEY, REGISTRATION_VERIFY_SUCCESS);
        }

        Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        var queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        var loginAction = getLoginPath(routingContext, client);
        routingContext.put(LOGIN_ACTION_KEY, resolveProxyRequest(routingContext.request(), loginAction, queryParams, true));

        this.renderPage(routingContext, generateData(routingContext, domain, client), client, LOGGER, TEMPLATE_ERROR_MESSAGE);
    }

    private String getLoginPath(RoutingContext routingContext, Client client) {
        var isIdentifierFirstEnabled = ofNullable(LoginSettings.getInstance(domain, client))
                .map(LoginSettings::isIdentifierFirstEnabled)
                .orElse(false);
        final String loginPath = isIdentifierFirstEnabled ? IDENTIFIER_FIRST_LOGIN.redirectUri() : LOGIN.redirectUri();
        return routingContext.get(CONTEXT_PATH) + loginPath;
    }

    @Override
    public String getTemplateSuffix() {
        return REGISTRATION_VERIFY.template();
    }
}

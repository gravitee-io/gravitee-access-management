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

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.form.FormManager;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserRequestHandler;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
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
public class RegisterConfirmationEndpoint extends UserRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(RegisterConfirmationEndpoint.class);

    private final ThymeleafTemplateEngine engine;

    public RegisterConfirmationEndpoint(ThymeleafTemplateEngine thymeleafTemplateEngine) {
        this.engine = thymeleafTemplateEngine;
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
        routingContext.put(ConstantKeys.USER_CONTEXT_KEY, user);

        // retrieve client (if exists)
        Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        // check if user has already completed its registration
        if (user != null && user.isPreRegistration() && user.isRegistrationCompleted()) {
            MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            queryParams.set(ConstantKeys.ERROR_PARAM_KEY, "invalid_registration_context");
            redirectToPage(routingContext, queryParams);
            return;
        }

        routingContext.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path()));

        // render the registration confirmation page
        engine.render(routingContext.data(), getTemplateFileName(client), res -> {
            if (res.succeeded()) {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                routingContext.response().end(res.result());
            } else {
                logger.error("Unable to render registration confirmation page", res.cause());
                routingContext.fail(res.cause());
            }
        });
    }

    private String getTemplateFileName(Client client) {
        return "registration_confirmation" + (client != null ? FormManager.TEMPLATE_NAME_SEPARATOR + client.getId() : "");
    }
}

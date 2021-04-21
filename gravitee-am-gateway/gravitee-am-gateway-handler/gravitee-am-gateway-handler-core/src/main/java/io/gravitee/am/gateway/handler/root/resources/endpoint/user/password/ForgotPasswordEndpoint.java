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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.password;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.form.FormManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.account.FormField;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.FORGOT_PASSWORD_CONFIRM;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ForgotPasswordEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordEndpoint.class);

    private final ThymeleafTemplateEngine engine;

    private final Domain domain;

    public ForgotPasswordEndpoint(ThymeleafTemplateEngine engine, Domain domain) {
        this.engine = engine;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        final String error = request.getParam(ConstantKeys.ERROR_PARAM_KEY);
        final String errorDescription = request.getParam(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY);
        final String success = request.getParam(ConstantKeys.SUCCESS_PARAM_KEY);
        final String warning = request.getParam(ConstantKeys.WARNING_PARAM_KEY);
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final String clientId = request.getParam(Parameters.CLIENT_ID);

        // add query params to context
        routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);
        routingContext.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
        routingContext.put(ConstantKeys.SUCCESS_PARAM_KEY, success);
        routingContext.put(ConstantKeys.WARNING_PARAM_KEY, warning);

        // put parameters in context (backward compatibility)
        Map<String, String> params = new HashMap<>();
        params.computeIfAbsent(Parameters.CLIENT_ID, val -> clientId);
        params.computeIfAbsent(ConstantKeys.ERROR_PARAM_KEY, val -> error);
        params.computeIfAbsent(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, val -> errorDescription);
        routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, params);

        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        routingContext.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams));
        routingContext.put(ConstantKeys.LOGIN_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/login", queryParams));

        AccountSettings settings = AccountSettings.getInstance(domain, client);
        if (settings.isResetPasswordCustomForm()) {
            // custom form is enabled
            // display Email form if ConfirmIdentity is enable & warning parameter is missing
            // otherwise display custom form (ConfirmIdentity is disabled or an identity confirmation is required)
            if (settings.isResetPasswordConfirmIdentity() && !FORGOT_PASSWORD_CONFIRM.equals(warning)) {
                routingContext.put(ConstantKeys.FORGOT_PASSWORD_FIELDS_KEY, Arrays.asList(FormField.getEmailField()));
            }  else {
                routingContext.put(ConstantKeys.FORGOT_PASSWORD_FIELDS_KEY, settings.getResetPasswordCustomFormFields());
            }
        } else {
            routingContext.put(ConstantKeys.FORGOT_PASSWORD_FIELDS_KEY, Arrays.asList(FormField.getEmailField()));
        }

        // render the forgot password page
        engine.render(routingContext.data(), getTemplateFileName(client), res -> {
            if (res.succeeded()) {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                routingContext.response().end(res.result());
            } else {
                logger.error("Unable to render forgot password page", res.cause());
                routingContext.fail(res.cause());
            }
        });
    }

    private String getTemplateFileName(Client client) {
        return "forgot_password" + (client != null ? FormManager.TEMPLATE_NAME_SEPARATOR + client.getId() : "");
    }
}

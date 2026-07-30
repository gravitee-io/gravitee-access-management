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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.account.FormField;
import io.gravitee.am.model.account.ForgotPasswordLookupField;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.gravitee.am.common.utils.ConstantKeys.FORGOT_PASSWORD_CONFIRM;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import lombok.CustomLog;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class ForgotPasswordEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private final Domain domain;
    private final BotDetectionManager botDetectionManager;

    public ForgotPasswordEndpoint(TemplateEngine engine, Domain domain, BotDetectionManager botDetectionManager) {
        super(engine);
        this.domain = domain;
        this.botDetectionManager = botDetectionManager;
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
        routingContext.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true));
        routingContext.put(ConstantKeys.LOGIN_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/login", queryParams, true));

        AccountSettings settings = AccountSettings.getInstance(domain, client);
        if (settings != null && settings.isResetPasswordCustomForm()) {
            // custom form is enabled
            // display Email form if ConfirmIdentity is enable & warning parameter is missing
            // otherwise display custom form (ConfirmIdentity is disabled or an identity confirmation is required)
            if (settings.isResetPasswordConfirmIdentity() && !FORGOT_PASSWORD_CONFIRM.equals(warning)) {
                routingContext.put(ConstantKeys.FORGOT_PASSWORD_FIELDS_KEY, List.of(FormField.getEmailField()));
            } else {
                routingContext.put(ConstantKeys.FORGOT_PASSWORD_FIELDS_KEY,
                        withDefaultInputTypes(settings.getResetPasswordCustomFormFields()));
            }
        } else {
            routingContext.put(ConstantKeys.FORGOT_PASSWORD_FIELDS_KEY, List.of(FormField.getEmailField()));
        }

        final Map<String, Object> data = generateData(routingContext, domain, client);
        data.putAll(botDetectionManager.getTemplateVariables(domain, client));

        this.renderPage(routingContext, data, client, log, "Unable to render forgot password page");
    }

    @Override
    public String getTemplateSuffix() {
        return Template.FORGOT_PASSWORD.template();
    }

    private static List<FormField> withDefaultInputTypes(List<FormField> fields) {
        if (fields == null) {
            return List.of();
        }
        return fields.stream().map(ForgotPasswordEndpoint::withDefaultInputType).collect(Collectors.toList());
    }

    private static FormField withDefaultInputType(FormField field) {
        if (field == null) {
            return null;
        }
        if (field.getType() != null && !field.getType().isBlank()) {
            return field;
        }
        FormField copy = new FormField();
        copy.setKey(field.getKey());
        copy.setLabel(field.getLabel());
        copy.setType(ForgotPasswordLookupField.defaultInputType(field.getKey()));
        return copy;
    }
}

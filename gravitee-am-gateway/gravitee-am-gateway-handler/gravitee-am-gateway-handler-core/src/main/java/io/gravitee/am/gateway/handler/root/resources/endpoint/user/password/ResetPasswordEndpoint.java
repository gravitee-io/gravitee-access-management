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

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
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
public class ResetPasswordEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(ResetPasswordEndpoint.class);

    private final ThymeleafTemplateEngine engine;
    private final Domain domain;

    public ResetPasswordEndpoint(ThymeleafTemplateEngine engine, Domain domain) {
        this.engine = engine;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        // retrieve client (if exists)
        Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        PasswordSettings.getInstance(client, domain).ifPresent(v -> routingContext.put(ConstantKeys.PASSWORD_CONFIG_PARAM_KEY, v));

        String error = request.getParam(ConstantKeys.ERROR_PARAM_KEY);
        routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);

        String errorDescription = request.getParam(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY);
        routingContext.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);

        copyValue(request,routingContext,ConstantKeys.SUCCESS_PARAM_KEY);
        copyValue(request,routingContext,ConstantKeys.WARNING_PARAM_KEY);
        // add query params to context
        copyValue(request,routingContext,ConstantKeys.TOKEN_PARAM_KEY);

        // put parameters in context (backward compatibility)
        Map<String, String> params = new HashMap<>();
        params.computeIfAbsent(ConstantKeys.ERROR_PARAM_KEY, val -> error);
        params.computeIfAbsent(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, val -> errorDescription);
        routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, params);


        routingContext.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path()));

        // render the reset password page
        engine.render(routingContext.data(), getTemplateFileName(client), res -> {
            if (res.succeeded()) {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                routingContext.response().end(res.result());
            } else {
                logger.error("Unable to render reset password page", res.cause());
                routingContext.fail(res.cause());
            }
        });
    }


    @Override
    public String getTemplateSuffix() {
        return "reset_password";
    }
}

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
package io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.register;

import io.gravitee.am.gateway.handler.form.FormManager;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.UserRequestHandler;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterConfirmationEndpointHandler extends UserRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(RegisterConfirmationEndpointHandler.class);
    private static final String ERROR_PARAM = "error";
    private static final String SUCCESS_PARAM = "success";
    private static final String TOKEN_PARAM = "token";
    private ThymeleafTemplateEngine engine;

    public RegisterConfirmationEndpointHandler(ThymeleafTemplateEngine thymeleafTemplateEngine) {
        this.engine = thymeleafTemplateEngine;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        final String error = request.getParam(ERROR_PARAM);
        final String success = request.getParam(SUCCESS_PARAM);
        final String token = request.getParam(TOKEN_PARAM);
        // add query params to context
        routingContext.put(ERROR_PARAM, error);
        routingContext.put(SUCCESS_PARAM, success);
        routingContext.put(TOKEN_PARAM, token);

        // retrieve user who want to register
        User user = routingContext.get("user");
        routingContext.put("user", user);

        // retrieve client (if exists)
        Client client = routingContext.get("client");

        // check if user has already completed its registration
        if (user != null && user.isPreRegistration() && user.isRegistrationCompleted()) {
            redirectToPage(routingContext, Collections.singletonMap("error", "invalid_registration_context"));
            return;
        }

        // render the registration confirmation page
        engine.render(routingContext, getTemplateFileName(client), res -> {
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

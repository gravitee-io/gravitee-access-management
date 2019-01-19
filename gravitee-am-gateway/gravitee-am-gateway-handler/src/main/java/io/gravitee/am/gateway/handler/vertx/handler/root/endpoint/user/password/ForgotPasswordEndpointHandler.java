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
package io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.password;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ForgotPasswordEndpointHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordEndpointHandler.class);
    private static final String ERROR_PARAM = "error";
    private static final String SUCCESS_PARAM = "success";
    private static final String WARNING_PARAM = "warning";
    private ThymeleafTemplateEngine engine;

    public ForgotPasswordEndpointHandler(ThymeleafTemplateEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        final String error = request.getParam(ERROR_PARAM);
        final String success = request.getParam(SUCCESS_PARAM);
        final String warning = request.getParam(WARNING_PARAM);
        // add query params to context
        routingContext.put(ERROR_PARAM, error);
        routingContext.put(SUCCESS_PARAM, success);
        routingContext.put(WARNING_PARAM, warning);

        // render the forgot password page
        engine.render(routingContext, "forgot_password", res -> {
            if (res.succeeded()) {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                routingContext.response().end(res.result());
            } else {
                logger.error("Unable to render forgot password page", res.cause());
                routingContext.fail(res.cause());
            }
        });
    }
}

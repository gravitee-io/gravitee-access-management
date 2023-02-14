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

import io.gravitee.am.common.exception.oauth2.BadClientCredentialsException;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import static io.gravitee.am.common.utils.ConstantKeys.ACTION_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginSSOPOSTEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginSSOPOSTEndpoint.class);
    private static final String FORM_PARAMETERS = "parameters";

    private final ThymeleafTemplateEngine engine;

    public LoginSSOPOSTEndpoint(ThymeleafTemplateEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext routingContext) {

        // Prepare context to render post form.
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());

        routingContext.put(ACTION_KEY, queryParams.get(ACTION_KEY));
        routingContext.put(FORM_PARAMETERS, queryParams.remove(ACTION_KEY));

        if (StringUtils.isEmpty(routingContext.get(ACTION_KEY)) || ((MultiMap) routingContext.get(FORM_PARAMETERS)).isEmpty()) {
            routingContext.fail(new BadClientCredentialsException());
            return;
        }

        // Render login SSO POST form.
        engine.rxRender(routingContext.data(), "login_sso_post")
                .doOnSuccess(buffer -> {
                    routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                    routingContext.response().end(buffer);
                })
                .doOnError(throwable -> {
                    logger.error("Unable to render Login SSO POST page", throwable);
                    routingContext.fail(throwable.getCause());
                })
                .subscribe();
    }
}

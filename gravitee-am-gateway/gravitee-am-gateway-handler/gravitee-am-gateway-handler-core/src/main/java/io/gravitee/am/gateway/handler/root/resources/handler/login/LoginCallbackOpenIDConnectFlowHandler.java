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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2AuthenticationProvider;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackOpenIDConnectFlowHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginCallbackOpenIDConnectFlowHandler.class);
    private static final String PROVIDER_PARAMETER = "provider";
    private ThymeleafTemplateEngine engine;

    public LoginCallbackOpenIDConnectFlowHandler(ThymeleafTemplateEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext context) {
        final String providerId = context.request().getParam(PROVIDER_PARAMETER);
        final OAuth2AuthenticationProvider authenticationProvider = context.get(PROVIDER_PARAMETER);

        // response_type == code (nominal use case), continue
        if (ResponseType.CODE.equals(authenticationProvider.configuration().getResponseType())) {
            context.next();
            return;
        }

        // implicit flow, we need to retrieve hash url from the browser to get access_token, id_token, ...
        engine.render(Collections.singletonMap("providerId", providerId), "login_callback", res -> {
            if (res.succeeded()) {
                context.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                context.response().end(res.result());
            } else {
                logger.error("Unable to render login callback page", res.cause());
                context.fail(res.cause());
            }
        });
    }
}

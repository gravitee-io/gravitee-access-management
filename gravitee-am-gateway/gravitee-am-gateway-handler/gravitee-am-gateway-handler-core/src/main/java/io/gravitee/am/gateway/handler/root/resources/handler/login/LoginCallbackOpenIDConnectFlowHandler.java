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

import io.gravitee.am.common.exception.authentication.InternalAuthenticationServiceException;
import io.gravitee.am.common.oidc.AuthenticationFlow;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectAuthenticationProvider;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackOpenIDConnectFlowHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginCallbackOpenIDConnectFlowHandler.class);
    private static final String PROVIDER_PARAMETER = "provider";
    private static final String HASH_VALUE_PARAMETER = "urlHash";
    private ThymeleafTemplateEngine engine;

    public LoginCallbackOpenIDConnectFlowHandler(ThymeleafTemplateEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext context) {
        final String providerId = context.request().getParam(PROVIDER_PARAMETER);
        final AuthenticationProvider authenticationProvider = context.get(PROVIDER_PARAMETER);

        // identity provider type is not OpenID Connect or the implicit flow is not used, continue
        if (!canHandle(authenticationProvider)) {
            context.next();
            return;
        }

        // if method is post, the OpenID Connect implicit flow response hash url must be present, add it to the execution context
        if (context.request().method().equals(HttpMethod.POST)) {
            final String hashValue = context.request().getParam(HASH_VALUE_PARAMETER);
            if (hashValue == null) {
                context.fail(new InternalAuthenticationServiceException("No URL hash value found"));
                return;
            }
            // decode hash value and put data in the execution context
            Map<String, String> hashValues = getParams(hashValue.substring(1)); // remove # symbol
            hashValues.forEach((k, v) -> context.put(k, v));
            context.next();
            return;
        }

        // implicit flow, we need to retrieve hash url from the browser to get access_token, id_token, ...
        engine.render(
            Collections.singletonMap("providerId", providerId),
            "login_callback",
            res -> {
                if (res.succeeded()) {
                    context.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                    context.response().end(res.result());
                } else {
                    logger.error("Unable to render login callback page", res.cause());
                    context.fail(res.cause());
                }
            }
        );
    }

    private boolean canHandle(AuthenticationProvider authenticationProvider) {
        return (
            (authenticationProvider instanceof OpenIDConnectAuthenticationProvider) &&
            (((OpenIDConnectAuthenticationProvider) authenticationProvider).authenticationFlow().equals(AuthenticationFlow.IMPLICIT_FLOW))
        );
    }

    private Map<String, String> getParams(String query) {
        Map<String, String> query_pairs = new LinkedHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return query_pairs;
    }
}

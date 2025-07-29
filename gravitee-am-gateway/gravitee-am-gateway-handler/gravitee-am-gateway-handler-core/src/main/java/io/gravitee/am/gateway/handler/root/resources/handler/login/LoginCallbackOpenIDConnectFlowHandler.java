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
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.URLParametersUtils;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Handle OpenID Connect response with response_type = id_token or id_token token.
 * For this kind of response, the OIDC provider redirects user to the OAuth 2.0 client with parameters as fragment instead of query.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackOpenIDConnectFlowHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginCallbackOpenIDConnectFlowHandler.class);
    private static final String RELAY_STATE_PARAM_KEY = "RelayState";
    private final ThymeleafTemplateEngine engine;

    public LoginCallbackOpenIDConnectFlowHandler(ThymeleafTemplateEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext context) {
        final HttpServerRequest request = context.request();

        // if request contains query parameters, authorization_code flow is used, continue
        if (request.method().equals(HttpMethod.GET) &&
                (request.params() != null && !request.params().isEmpty())) {
            context.next();
            return;
        }

        // if method is post
        // either SAML 2.0 protocol is used and RelayState must be present
        // or the OpenID Connect implicit flow response hash url must be present
        if (request.method().equals(HttpMethod.POST)) {
            final String relayState = request.getParam(RELAY_STATE_PARAM_KEY);
            // if SAML 2.0 is used, continue
            if (relayState != null) {
                context.next();
                return;
            }
            // if OAuth 2.0 authorization_code flow is used, continue
            final String code = request.getParam(Parameters.CODE);
            if (code != null) {
                context.next();
                return;
            }

            // if CAS callback is used, continue
            if (request.getParam(Parameters.TICKET) != null) {
                context.next();
                return;
            }

            // else check OpenID Connect flow validity
            final String hashValue = request.getParam(ConstantKeys.URL_HASH_PARAMETER);
            if (hashValue == null || hashValue.isEmpty()) {
                context.fail(new InternalAuthenticationServiceException("No URL hash value found"));
                return;
            }
            // decode hash value and put data in the execution context
            Map<String, String> hashValues = getParams(hashValue.substring(1)); // remove # symbol
            hashValues.forEach(context::put);
            context.next();
            return;
        }

        // implicit flow, we need to retrieve hash url from the browser to get access_token, id_token, ...
        engine.render(new HashMap<>(Map.of(ConstantKeys.CSP_SCRIPT_INLINE_NONCE, context.get(ConstantKeys.CSP_SCRIPT_INLINE_NONCE))), "login_callback")
                .subscribe(
                        buffer -> {
                            context.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                            context.response().end(buffer);
                        },
                        throwable -> {
                            logger.error("Unable to render login callback page", throwable);
                            context.fail(throwable.getCause());
                        }
                );
    }

    private Map<String, String> getParams(String query) {
        return URLParametersUtils.parse(query);
    }
}

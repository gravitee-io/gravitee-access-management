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
package io.gravitee.am.gateway.handler.vertx.auth.handler.impl;

import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.oauth2.utils.OIDCParameters;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamic Client Registration is a protocol that allows OAuth client applications to register with an OAuth server.
 * Specifications are defined by OpenID Foundation and by the IETF as RFC 7591 too.
 * They define how a client may submit a request to register itself and how should be the response.
 *
 * See <a href="https://openid.net/specs/openid-connect-registration-1_0.html">Openid Connect Dynamic Client Registration</a>
 * See <a href="https://tools.ietf.org/html/rfc7591"> OAuth 2.0 Dynamic Client Registration Protocol</a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class ClientAssertionAuthHandlerImpl extends AuthHandlerImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAssertionAuthHandlerImpl.class);
    public static final String BASE_PATH_PARAM = "BASE_PATH";

    public ClientAssertionAuthHandlerImpl(AuthProvider authProvider) {
        super(authProvider);
    }


    protected final void parseAuthorization(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
        String clientAssertionType = context.request().getParam(OIDCParameters.CLIENT_ASSERTION_TYPE);
        String clientAssertion = context.request().getParam(OIDCParameters.CLIENT_ASSERTION);
        String clientId = context.request().getParam(OAuth2Constants.CLIENT_ID);

        if (clientAssertionType != null && clientAssertion != null) {
            JsonObject clientCredentials = new JsonObject()
                    .put(OIDCParameters.CLIENT_ASSERTION_TYPE, clientAssertionType)
                    .put(OIDCParameters.CLIENT_ASSERTION, clientAssertion)
                    .put(OAuth2Constants.CLIENT_ID, clientId)
                    .put(BASE_PATH_PARAM, UriBuilderRequest.extractBasePath(context));
            handler.handle(Future.succeededFuture(clientCredentials));
        } else {
            handler.handle(Future.failedFuture(UNAUTHORIZED));
        }
    }
}

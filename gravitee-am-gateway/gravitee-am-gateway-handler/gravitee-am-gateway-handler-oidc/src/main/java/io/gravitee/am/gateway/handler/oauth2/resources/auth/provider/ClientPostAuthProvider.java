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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.provider;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientAuthHandler;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * Client Authentication method : client_secret_post
 *
 * Clients that have received a client_secret value from the Authorization Server,
 * authenticate with the Authorization Server in accordance with Section 2.3.1 of OAuth 2.0 [RFC6749] by including the Client Credentials in the request body.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientPostAuthProvider implements ClientAuthProvider {

    @Override
    public boolean canHandle(Client client, RoutingContext context) {
        if (client != null && ClientAuthenticationMethod.CLIENT_SECRET_POST.equals(client.getTokenEndpointAuthMethod())) {
            return true;
        }
        if ((client != null && (client.getTokenEndpointAuthMethod() == null || client.getTokenEndpointAuthMethod().isEmpty()))
                && getClientId(context.request()) != null && getClientSecret(context.request()) != null) {
            return true;
        }
        return false;
    }

    @Override
    public void handle(Client client, RoutingContext context, Handler<AsyncResult<Client>> handler) {
        final String clientId = getClientId(context.request());
        final String clientSecret = getClientSecret(context.request());

        if (!client.getClientId().equals(clientId) || !client.getClientSecret().equals(clientSecret)) {
            handler.handle(Future.failedFuture(new InvalidClientException(ClientAuthHandler.GENERIC_ERROR_MESSAGE)));
            return;
        }
        handler.handle(Future.succeededFuture(client));
    }

    private String getClientId(HttpServerRequest request) {
        return request.getParam(Parameters.CLIENT_ID);
    }

    private String getClientSecret(HttpServerRequest request) {
        return request.getParam(Parameters.CLIENT_SECRET);
    }
}

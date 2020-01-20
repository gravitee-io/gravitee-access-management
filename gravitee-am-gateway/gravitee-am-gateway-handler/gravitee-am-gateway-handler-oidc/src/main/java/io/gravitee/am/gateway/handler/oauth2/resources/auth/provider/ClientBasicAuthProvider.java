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

import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientAuthHandler;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.reactivex.core.http.HttpServerRequest;

import java.util.Base64;

/**
 * Client Authentication method : client_secret_basic
 *
 * Clients that have received a client_secret value from the Authorization Server authenticate with the Authorization Server
 * in accordance with Section 2.3.1 of OAuth 2.0 [RFC6749] using the HTTP Basic authentication scheme.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientBasicAuthProvider implements ClientAuthProvider {

    private static final String TYPE = "Basic";

    @Override
    public boolean canHandle(Client client, HttpServerRequest request) {
        if (client != null && ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(client.getTokenEndpointAuthMethod())) {
            return true;
        }
        if ((client != null && (client.getTokenEndpointAuthMethod() == null || client.getTokenEndpointAuthMethod().isEmpty()))
                && getBasicAuthorization(request) != null) {
            return true;
        }
        return false;
    }

    @Override
    public void handle(Client client, HttpServerRequest request, Handler<AsyncResult<Client>> handler) {
        final String authorization = getBasicAuthorization(request);
        if (authorization == null) {
            handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: missing or unsupported authentication method", authenticationHeader())));
            return;
        }
        try {
            // decode the payload
            String decoded = new String(Base64.getDecoder().decode(authorization));
            int colonIdx = decoded.indexOf(":");
            if (colonIdx == -1) {
                throw new IllegalArgumentException();
            }
            String clientId = decoded.substring(0, colonIdx);
            String clientSecret = decoded.substring(colonIdx + 1);
            if (!client.getClientId().equals(clientId) || !client.getClientSecret().equals(clientSecret)) {
                handler.handle(Future.failedFuture(new InvalidClientException(ClientAuthHandler.GENERIC_ERROR_MESSAGE, authenticationHeader())));
                return;
            }
            handler.handle(Future.succeededFuture(client));
        } catch (RuntimeException e) {
            handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: missing or unsupported authentication method", e, authenticationHeader())));
            return;
        }
    }

    private String getBasicAuthorization(HttpServerRequest request) {
        final String authorization = request.headers().get(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            return null;
        }
        int idx = authorization.indexOf(' ');
        if (idx <= 0) {
           return null;
        }
        if (!TYPE.equalsIgnoreCase(authorization.substring(0, idx))) {
            return null;
        }
        return authorization.substring(idx + 1);
    }

    private String authenticationHeader() {
        return "Basic realm=\"gravitee-io\"";
    }
}

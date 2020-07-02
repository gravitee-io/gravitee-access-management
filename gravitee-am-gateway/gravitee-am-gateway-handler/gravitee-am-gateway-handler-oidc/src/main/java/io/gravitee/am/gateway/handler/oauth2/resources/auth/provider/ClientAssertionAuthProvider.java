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
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientAuthHandler;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client Authentication method : private_key_jwt
 *
 * Clients that have registered a public key sign a JWT using that key.
 *
 * The Client authenticates in accordance with JSON Web Token (JWT) Profile for OAuth 2.0 Client Authentication and Authorization Grants [OAuth.JWT]
 * and Assertion Framework for OAuth 2.0 Client Authentication and Authorization Grants [OAuth.Assertions].
 *
 * The JWT MUST contain the following REQUIRED Claim Values and MAY contain the following OPTIONAL Claim Values:
 * iss
 * REQUIRED. Issuer. This MUST contain the client_id of the OAuth Client.
 * sub
 * REQUIRED. Subject. This MUST contain the client_id of the OAuth Client.
 * aud
 * REQUIRED. Audience. The aud (audience) Claim. Value that identifies the Authorization Server as an intended audience. The Authorization Server MUST verify that it is an intended audience for the token. The Audience SHOULD be the URL of the Authorization Server's Token Endpoint.
 * jti
 * REQUIRED. JWT ID. A unique identifier for the token, which can be used to prevent reuse of the token. These tokens MUST only be used once, unless conditions for reuse were negotiated between the parties; any such negotiation is beyond the scope of this specification.
 * exp
 * REQUIRED. Expiration time on or after which the ID Token MUST NOT be accepted for processing.
 * iat
 * OPTIONAL. Time at which the JWT was issued.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientAssertionAuthProvider implements ClientAuthProvider {

    private static final Logger logger = LoggerFactory.getLogger(ClientAssertionAuthProvider.class);
    private ClientAssertionService clientAssertionService;

    public ClientAssertionAuthProvider() { }

    public ClientAssertionAuthProvider(ClientAssertionService clientAssertionService) {
        this.clientAssertionService = clientAssertionService;
    }

    @Override
    public boolean canHandle(Client client, RoutingContext context) {
        if (client != null && (
                ClientAuthenticationMethod.PRIVATE_KEY_JWT.equals(client.getTokenEndpointAuthMethod()) ||
                        ClientAuthenticationMethod.CLIENT_SECRET_JWT.equals(client.getTokenEndpointAuthMethod()))) {
            return true;
        }

        if ((client == null || client.getTokenEndpointAuthMethod() == null || client.getTokenEndpointAuthMethod().isEmpty())
                && getClientAssertion(context.request()) != null && getClientAssertionType(context.request()) != null) {
            return true;
        }
        return false;
    }

    @Override
    public void handle(Client client, RoutingContext context, Handler<AsyncResult<Client>> handler) {
        HttpServerRequest request = context.request();
        String clientAssertionType = getClientAssertionType(request);
        String clientAssertion = getClientAssertion(request);
        String clientId = request.getParam(Parameters.CLIENT_ID);
        String basePath = UriBuilderRequest.extractBasePath(context);

        clientAssertionService.assertClient(clientAssertionType, clientAssertion, basePath)
                .flatMap(client1 -> {
                    // clientId is optional, but if provided we must ensure it is the same than the logged client.
                    if(clientId != null && !clientId.equals(client1.getClientId())) {
                        return Maybe.error(new InvalidClientException("client_id parameter does not match with assertion"));
                    }
                    return Maybe.just(client1);
                })
                .subscribe(
                        client1 -> handler.handle(Future.succeededFuture(client1)),
                        throwable -> {
                            if (throwable instanceof InvalidClientException) {
                                logger.debug("Failed to authenticate client with assertion method", throwable);
                                handler.handle(Future.failedFuture(throwable));
                            } else {
                                logger.error("Failed to authenticate client with assertion method", throwable);
                                handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: Failed to authenticate client with assertion method", throwable)));
                            }
                        },
                        () -> handler.handle(Future.failedFuture(new InvalidClientException(ClientAuthHandler.GENERIC_ERROR_MESSAGE)))
                );
    }

    public void setClientAssertionService(ClientAssertionService clientAssertionService) {
        this.clientAssertionService = clientAssertionService;
    }

    private String getClientAssertionType(HttpServerRequest request) {
        return request.getParam(Parameters.CLIENT_ASSERTION_TYPE);
    }

    private String getClientAssertion(HttpServerRequest request) {
        return request.getParam(Parameters.CLIENT_ASSERTION);
    }

}

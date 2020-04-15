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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.handler;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.impl.ClientAuthHandlerImpl;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.*;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client Authentication Handler used by Clients to authenticate to the Authorization Server when using the Token Endpoint
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ClientAuthHandler {

    String GENERIC_ERROR_MESSAGE = "Client authentication failed due to unknown or invalid client";

    static Handler<RoutingContext> create(ClientSyncService clientSyncService, ClientAssertionService clientAssertionService, JWKService jwkService) {
        List<ClientAuthProvider> clientAuthProviders = new ArrayList<>();
        clientAuthProviders.add(new ClientBasicAuthProvider());
        clientAuthProviders.add(new ClientPostAuthProvider());
        clientAuthProviders.add(new ClientAssertionAuthProvider(clientAssertionService));
        clientAuthProviders.add(new ClientCertificateAuthProvider());
        clientAuthProviders.add(new ClientSelfSignedAuthProvider(jwkService));
        clientAuthProviders.add(new ClientNoneAuthProvider());
        return new ClientAuthHandlerImpl(clientSyncService, clientAuthProviders);
    }
}

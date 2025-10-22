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
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceSyncService;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.impl.ClientAuthHandlerImpl;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.ClientAssertionAuthProvider;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.ClientAuthProvider;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.ClientBasicAuthProvider;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.ClientCertificateAuthProvider;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.ClientNoneAuthProvider;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.ClientPostAuthProvider;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.ClientSelfSignedAuthProvider;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.impl.SecretService;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Client Authentication Handler used by Clients to authenticate to the Authorization Server when using the Token Endpoint
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ClientAuthHandler {

    String GENERIC_ERROR_MESSAGE = "Client authentication failed due to unknown clientId, expired or invalid client secret";

    static Handler<RoutingContext> create(ClientSyncService clientSyncService, ClientAssertionService clientAssertionService, JWKService jwkService, Domain domain, SecretService appSecretService, String certHeader, AuditService auditService, ProtectedResourceSyncService protectedResourceSyncService) {
        List<ClientAuthProvider> clientAuthProviders = new ArrayList<>();
        clientAuthProviders.add(new ClientBasicAuthProvider(appSecretService));
        clientAuthProviders.add(new ClientPostAuthProvider(appSecretService));
        clientAuthProviders.add(new ClientAssertionAuthProvider(clientAssertionService));
        clientAuthProviders.add(new ClientCertificateAuthProvider(certHeader));
        clientAuthProviders.add(new ClientSelfSignedAuthProvider(jwkService, certHeader));
        clientAuthProviders.add(new ClientNoneAuthProvider());
        return new ClientAuthHandlerImpl(clientSyncService, clientAuthProviders, domain, certHeader, auditService, protectedResourceSyncService);
    }
}

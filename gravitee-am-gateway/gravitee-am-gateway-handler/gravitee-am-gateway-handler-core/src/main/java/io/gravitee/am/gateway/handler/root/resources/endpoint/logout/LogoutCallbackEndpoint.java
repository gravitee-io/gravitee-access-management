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
package io.gravitee.am.gateway.handler.root.resources.endpoint.logout;

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.TokenService;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LogoutCallbackEndpoint extends AbstractLogoutEndpoint {

    public LogoutCallbackEndpoint(Domain domain,
                                  TokenService tokenService,
                                  AuditService auditService,
                                  AuthenticationFlowContextService authenticationFlowContextService) {
        super(domain, tokenService, auditService, authenticationFlowContextService);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        // put client in context for later use
        if (client != null) {
            Client safeClient = new Client(client);
            safeClient.setClientSecret(null);
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, safeClient);
        }

        // invalidate session
        invalidateSession(routingContext, invalidSessionHandler(routingContext, client));
    }

}

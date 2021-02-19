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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal;

import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SPNEGOStep extends AuthenticationFlowStep {

    private static final String KERBEROS_AM_IDP = "kerberos-am-idp";
    private final IdentityProviderManager identityProviderManager;

    public SPNEGOStep(Handler<RoutingContext> wrapper,
                      IdentityProviderManager identityProviderManager) {
        super(wrapper);
        this.identityProviderManager = identityProviderManager;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final HttpServerRequest request = routingContext.request();

        // user already authenticated, continue
        if (routingContext.user() != null) {
            flow.doNext(routingContext);
            return;
        }

        // check if negotiate step has already been triggered
        if (Boolean.TRUE.equals(request.params().get(ConstantKeys.ASK_FOR_NEGOTIATE_KEY))) {
            flow.doNext(routingContext);
            return;
        }

        // check if application has enabled Kerberos authentication
        if (client == null || client.getIdentities() == null) {
            flow.doNext(routingContext);
            return;
        }

        boolean kerberosEnabled =
                client.getIdentities()
                        .stream()
                        .map(identityProviderManager::getIdentityProvider)
                        .anyMatch(identityProvider -> identityProvider != null && KERBEROS_AM_IDP.equals(identityProvider.getType()));

        if (!kerberosEnabled) {
            flow.doNext(routingContext);
            return;
        }

        // else go to the SPNEGO login page
        flow.exit(this);
    }
}

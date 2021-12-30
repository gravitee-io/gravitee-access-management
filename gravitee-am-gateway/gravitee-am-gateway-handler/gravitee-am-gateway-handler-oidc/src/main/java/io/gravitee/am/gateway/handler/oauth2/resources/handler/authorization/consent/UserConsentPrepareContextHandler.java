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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentPrepareContextHandler implements Handler<RoutingContext> {

    private final ClientSyncService clientSyncService;

    public UserConsentPrepareContextHandler(ClientSyncService clientSyncService) {
        this.clientSyncService = clientSyncService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // user must redirected here after an authorization request
        AuthorizationRequest authorizationRequest = routingContext.get(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY);
        if (authorizationRequest == null) {
            routingContext.response().setStatusCode(400).end("An authorization request is required to handle user approval");
            return;
        }

        // check user
        User authenticatedUser = routingContext.user();
        if (authenticatedUser == null || !(authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User)) {
            routingContext.fail(new AccessDeniedException());
            return;
        }

        // prepare context
        Client safeClient = new Client(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY));
        safeClient.setClientSecret(null);
        io.gravitee.am.model.User user = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) authenticatedUser.getDelegate()).getUser();
        prepareContext(routingContext, safeClient, user);

        routingContext.next();
    }

    private void prepareContext(RoutingContext context, Client client, io.gravitee.am.model.User user) {
        context.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        context.put(ConstantKeys.USER_CONTEXT_KEY, user);

        // add id_token if exists
        String idToken = context.session().get(ConstantKeys.ID_TOKEN_KEY);
        if (idToken != null) {
            context.put(ConstantKeys.ID_TOKEN_CONTEXT_KEY, idToken);
        }

        // add webAuthn credential id if exists
        String webAuthnCredentialId = context.session().get(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY);
        if (webAuthnCredentialId != null) {
            context.put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, webAuthnCredentialId);
        }

        // add mfa factor id if exists
        String mfaFactorId = context.session().get(ConstantKeys.MFA_FACTOR_ID_CONTEXT_KEY);
        if (mfaFactorId != null) {
            context.put(ConstantKeys.MFA_FACTOR_ID_CONTEXT_KEY, mfaFactorId);
        }
    }
}

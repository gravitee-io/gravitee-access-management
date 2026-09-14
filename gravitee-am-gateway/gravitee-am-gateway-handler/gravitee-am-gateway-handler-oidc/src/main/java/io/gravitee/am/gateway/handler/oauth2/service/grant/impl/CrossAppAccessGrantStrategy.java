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
package io.gravitee.am.gateway.handler.oauth2.service.grant.impl;

import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.ResolvedEndUser;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;

public class CrossAppAccessGrantStrategy extends ExtensionGrantStrategy {

    private final ExtensionGrantProvider extensionGrantProvider;

    public CrossAppAccessGrantStrategy(
            ExtensionGrantProvider extensionGrantProvider,
            ExtensionGrant extensionGrant,
            UserAuthenticationManager userAuthenticationManager,
            IdentityProviderManager identityProviderManager,
            UserGatewayService userService,
            SubjectManager subjectManager,
            Domain domain) {
        super(extensionGrantProvider, extensionGrant, userAuthenticationManager, identityProviderManager, userService, subjectManager, domain);
        this.extensionGrantProvider = extensionGrantProvider;
    }

    @Override
    public boolean supports(String grantType, Client client, Domain domain) {
        return false;
    }

    @Override
    public boolean supports(TokenRequest request, Client client, Domain domain) {
        return super.supports(request.getGrantType(), client, domain) && isIdJagAssertion(request);
    }

    @Override
    protected Maybe<ResolvedEndUser> resolveEndUser(TokenRequest tokenRequest, Client client) {
        return extensionGrantProvider.resolveEndUser(convertToPluginRequest(tokenRequest))
                .ignoreElement()
                .andThen(Maybe.error(() -> new InvalidGrantException("Identity assertion authorization grant redemption is not available")));
    }
}

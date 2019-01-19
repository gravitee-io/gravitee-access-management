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
package io.gravitee.am.gateway.handler.oauth2.granter.extensiongrant;

import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.User;
import io.reactivex.Maybe;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the Extension Grants
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.5">4.5. Extension Grants</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantGranter extends AbstractTokenGranter {

    private ExtensionGrantProvider extensionGrantProvider;
    private ExtensionGrant extensionGrant;
    private UserAuthenticationManager userAuthenticationManager;

    public ExtensionGrantGranter(ExtensionGrantProvider extensionGrantProvider,
                                 ExtensionGrant extensionGrant,
                                 UserAuthenticationManager userAuthenticationManager,
                                 TokenService tokenService,
                                 TokenRequestResolver tokenRequestResolver) {
        super(extensionGrant.getGrantType());
        setTokenService(tokenService);
        setTokenRequestResolver(tokenRequestResolver);
        this.extensionGrantProvider = extensionGrantProvider;
        this.extensionGrant = extensionGrant;
        this.userAuthenticationManager = userAuthenticationManager;
    }

    @Override
    protected Maybe<User> resolveResourceOwner(TokenRequest tokenRequest, Client client) {
        return extensionGrantProvider.grant(convert(tokenRequest))
                .flatMap(endUser -> {
                    if (extensionGrant.isCreateUser()) {
                        Map<String, Object> additionalInformation = endUser.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(endUser.getAdditionalInformation());
                        // set source provider
                        additionalInformation.put("source", extensionGrant.getIdentityProvider());
                        ((DefaultUser) endUser).setAdditionalInformation(additionalInformation);
                        return userAuthenticationManager.loadUser(endUser).toMaybe();
                    } else {
                        User user = new User();
                        user.setUsername(endUser.getUsername());
                        user.setAdditionalInformation(endUser.getAdditionalInformation());
                        return Maybe.just(user);
                    }
                })
                .onErrorResumeNext(ex -> {
                    return Maybe.error(new InvalidGrantException());
                });
    }

    private io.gravitee.am.repository.oauth2.model.request.TokenRequest convert(TokenRequest _tokenRequest) {
        io.gravitee.am.repository.oauth2.model.request.TokenRequest tokenRequest = new io.gravitee.am.repository.oauth2.model.request.TokenRequest();
        tokenRequest.setClientId(_tokenRequest.getClientId());
        tokenRequest.setGrantType(_tokenRequest.getGrantType());
        tokenRequest.setScope(_tokenRequest.getScopes());
        tokenRequest.setRequestParameters(_tokenRequest.getRequestParameters().toSingleValueMap());

        return tokenRequest;
    }
}

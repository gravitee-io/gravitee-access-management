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
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.ExtensionGrant;
import io.reactivex.Single;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
    private UserService userService;

    public ExtensionGrantGranter(ExtensionGrantProvider extensionGrantProvider,
                                 ExtensionGrant extensionGrant,
                                 UserService userService,
                                 TokenService tokenService) {
        super(extensionGrant.getGrantType());
        setTokenService(tokenService);
        this.extensionGrantProvider = extensionGrantProvider;
        this.extensionGrant = extensionGrant;
        this.userService = userService;
    }

    @Override
    protected Single<OAuth2Request> createOAuth2Request(TokenRequest tokenRequest, Client client) {
        return extensionGrantProvider.grant(convert(tokenRequest))
                .map(user -> Optional.of(user))
                .defaultIfEmpty(Optional.empty())
                .flatMapSingle(optUser -> {
                    if (optUser.isPresent() && extensionGrant.isCreateUser()) {
                        User endUser = optUser.get();
                        // set source provider
                        Map<String, Object> additionalInformation =
                                endUser.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(endUser.getAdditionalInformation());
                        additionalInformation.put("source", extensionGrant.getIdentityProvider());
                        ((DefaultUser) endUser).setAdditonalInformation(additionalInformation);
                        return userService.findOrCreate(endUser)
                                .flatMap(user -> super.createOAuth2Request(tokenRequest, client)
                                        .map(oAuth2Request -> {
                                            oAuth2Request.setSubject(user.getId());
                                            return oAuth2Request;
                                        }));
                    } else {
                        return super.createOAuth2Request(tokenRequest, client);
                    }
                })
                .onErrorResumeNext(ex -> Single.error(new InvalidGrantException()));
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

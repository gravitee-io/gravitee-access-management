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
package io.gravitee.am.gateway.handler.oauth2.service.token.impl;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.common.oauth2.InternalClientTokenIssuer;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DomainReadService;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class InternalClientTokenIssuerImpl implements InternalClientTokenIssuer {

    private final TokenService tokenService;
    private final Domain domain;
    private final DomainReadService domainReadService;

    public InternalClientTokenIssuerImpl(TokenService tokenService, Domain domain, DomainReadService domainReadService) {
        this.tokenService = tokenService;
        this.domain = domain;
        this.domainReadService = domainReadService;
    }

    @Override
    public Single<IssuedToken> issue(Client client) {
        if (client.getAuthorizedGrantTypes() == null || !client.getAuthorizedGrantTypes().contains(GrantType.CLIENT_CREDENTIALS)) {
            return Single.error(new UnauthorizedClientException("Client is not authorized to use the client_credentials grant type"));
        }
        return Single.fromCallable(this::origin)
                .flatMap(origin -> tokenService.create(tokenRequest(client, origin), client, null))
                .map(token -> new IssuedToken(token.getValue(), token.getExpiresIn()));
    }

    private OAuth2Request tokenRequest(Client client, String origin) {
        OAuth2Request tokenRequest = new OAuth2Request();
        tokenRequest.setClientId(client.getClientId());
        tokenRequest.setGrantType(GrantType.CLIENT_CREDENTIALS);
        tokenRequest.setScopes(defaultScopes(client));
        tokenRequest.setOrigin(origin);
        return tokenRequest;
    }

    private String origin() {
        String url = domainReadService.buildUrl(domain, "");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private Set<String> defaultScopes(Client client) {
        List<ApplicationScopeSettings> scopeSettings = client.getScopeSettings();
        if (scopeSettings == null) {
            return Set.of();
        }
        return scopeSettings.stream()
                .filter(ApplicationScopeSettings::isDefaultScope)
                .map(ApplicationScopeSettings::getScope)
                .collect(Collectors.toSet());
    }
}

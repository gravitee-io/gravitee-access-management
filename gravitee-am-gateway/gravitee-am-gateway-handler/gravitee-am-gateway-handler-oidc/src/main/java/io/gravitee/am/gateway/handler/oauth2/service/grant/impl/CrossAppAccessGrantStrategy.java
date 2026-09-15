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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.ResolvedEndUser;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.IdJagAssertionContext;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class CrossAppAccessGrantStrategy extends ExtensionGrantStrategy {

    private final ExtensionGrantProvider extensionGrantProvider;
    private final UserGatewayService userService;
    private final OpenIDDiscoveryService openIDDiscoveryService;

    public CrossAppAccessGrantStrategy(
            ExtensionGrantProvider extensionGrantProvider,
            ExtensionGrant extensionGrant,
            UserAuthenticationManager userAuthenticationManager,
            IdentityProviderManager identityProviderManager,
            UserGatewayService userService,
            SubjectManager subjectManager,
            Domain domain,
            OpenIDDiscoveryService openIDDiscoveryService) {
        super(extensionGrantProvider, extensionGrant, userAuthenticationManager, identityProviderManager, userService, subjectManager, domain);
        this.extensionGrantProvider = extensionGrantProvider;
        this.userService = userService;
        this.openIDDiscoveryService = openIDDiscoveryService;
    }

    @Override
    public boolean supports(String grantType, Client client, Domain domain) {
        return false;
    }

    @Override
    protected Maybe<ResolvedEndUser> resolveEndUser(TokenRequest tokenRequest, Client client) {
        return extensionGrantProvider.resolveEndUser(convertToPluginRequest(tokenRequest))
                .switchIfEmpty(Maybe.error(() -> new InvalidGrantException("Assertion did not resolve an end user")))
                .doOnSuccess(resolved -> tokenRequest.setIdJagAssertionContext(verifiedAssertionContext(resolved)))
                .flatMap(resolved -> validateRedemption(tokenRequest, client, resolved));
    }

    private Maybe<ResolvedEndUser> validateRedemption(TokenRequest tokenRequest, Client client, ResolvedEndUser resolved) {
        String domainIssuer = openIDDiscoveryService.getIssuer(tokenRequest.getOrigin());
        if (domainIssuer.equals(resolved.verifiedClaims().get(Claims.ISS))) {
            return Maybe.error(new InvalidGrantException("Assertion was issued by this domain"));
        }
        if (!audienceIncludes(resolved.verifiedClaims().get(Claims.AUD), domainIssuer)) {
            return Maybe.error(new InvalidGrantException("Assertion audience does not include this domain"));
        }
        if (!client.getClientId().equals(resolved.verifiedClaims().get(Claims.CLIENT_ID))) {
            return Maybe.error(new InvalidGrantException("Assertion client_id does not match the authenticated client"));
        }
        return Maybe.just(resolved);
    }

    @Override
    protected Maybe<User> resolveExistingResourceOwner(TokenRequest tokenRequest, ResolvedEndUser endUser) {
        return Maybe.fromOptional(Optional.ofNullable(stringClaim(endUser.verifiedClaims(), Claims.SUB)))
                .flatMap(subject -> userService.findByExternalIdAndSource(subject, endUser.identityProvider()))
                .switchIfEmpty(Maybe.error(() -> new InvalidGrantException("No user matches the assertion subject")));
    }

    @Override
    protected boolean supportsRefreshToken(Client client) {
        return false;
    }

    private static IdJagAssertionContext verifiedAssertionContext(ResolvedEndUser resolved) {
        Map<String, Object> verifiedClaims = resolved.verifiedClaims();
        return new IdJagAssertionContext(
                stringClaim(verifiedClaims, Claims.ISS),
                resolved.identityProvider(),
                stringClaim(verifiedClaims, Claims.JTI),
                stringClaim(verifiedClaims, Claims.CLIENT_ID));
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        return claims.get(name) instanceof String value ? value : null;
    }

    private static boolean audienceIncludes(Object audience, String domainIssuer) {
        return switch (audience) {
            case String singleAudience -> singleAudience.equals(domainIssuer);
            case Collection<?> audiences -> audiences.contains(domainIssuer);
            case null, default -> false;
        };
    }
}

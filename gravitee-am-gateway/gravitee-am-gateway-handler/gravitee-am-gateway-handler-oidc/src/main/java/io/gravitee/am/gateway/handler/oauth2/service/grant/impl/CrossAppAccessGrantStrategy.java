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
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.ResolvedEndUser;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidResourceException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.binding.UserBindingException;
import io.gravitee.am.gateway.handler.oauth2.service.binding.UserBindingResolver;
import io.gravitee.am.gateway.handler.oauth2.service.grant.IdJagAssertionContext;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.utils.ParameterizedScopeUtils;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.splitScopes;

public class CrossAppAccessGrantStrategy extends ExtensionGrantStrategy {

    private final ExtensionGrantProvider extensionGrantProvider;
    private final UserGatewayService userService;
    private final OpenIDDiscoveryService openIDDiscoveryService;
    private final ProtectedResourceManager protectedResourceManager;
    private final ScopeManager scopeManager;
    private final UserBindingResolver userBindingResolver;

    public CrossAppAccessGrantStrategy(
            ExtensionGrantProvider extensionGrantProvider,
            ExtensionGrant extensionGrant,
            UserAuthenticationManager userAuthenticationManager,
            IdentityProviderManager identityProviderManager,
            UserGatewayService userService,
            SubjectManager subjectManager,
            Domain domain,
            OpenIDDiscoveryService openIDDiscoveryService,
            ProtectedResourceManager protectedResourceManager,
            ScopeManager scopeManager) {
        super(extensionGrantProvider, extensionGrant, userAuthenticationManager, identityProviderManager, userService, subjectManager, domain);
        this.extensionGrantProvider = extensionGrantProvider;
        this.userService = userService;
        this.openIDDiscoveryService = openIDDiscoveryService;
        this.protectedResourceManager = protectedResourceManager;
        this.scopeManager = scopeManager;
        this.userBindingResolver = new UserBindingResolver(userService);
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
                .flatMap(resolved -> validateRedemption(tokenRequest, client, resolved))
                .flatMap(resolved -> resolveTargetResource(tokenRequest, resolved.verifiedClaims())
                        .doOnSuccess(resource -> attachTargetResource(tokenRequest, resource))
                        .flatMap(resource -> resolveGrantedScopes(tokenRequest, client, resolved.verifiedClaims(), resource))
                        .doOnSuccess(scopes -> attachGrantedScopes(tokenRequest, scopes))
                        .map(scopes -> resolved));
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

    private Maybe<String> resolveTargetResource(TokenRequest tokenRequest, Map<String, Object> verifiedClaims) {
        Object assertionResource = verifiedClaims.get(Parameters.RESOURCE);
        Set<String> requestedResources = Optional.ofNullable(tokenRequest.getResources()).orElse(Set.of());
        if (assertionResource == null && requestedResources.isEmpty()) {
            return Maybe.error(new InvalidResourceException("Neither the assertion nor the request names a resource"));
        }
        if (assertionResource != null && !(assertionResource instanceof String)) {
            return Maybe.error(new InvalidResourceException("Assertion resource must be a single resource identifier"));
        }
        if (requestedResources.size() > 1) {
            return Maybe.error(new InvalidResourceException("Request must name a single resource"));
        }
        String resource = assertionResource != null ? (String) assertionResource : requestedResources.iterator().next();
        if (!requestedResources.isEmpty() && !requestedResources.contains(resource)) {
            return Maybe.error(new InvalidResourceException("Requested resource does not match the assertion resource"));
        }
        if (protectedResourceManager.getByIdentifier(resource).isEmpty()) {
            return Maybe.error(new InvalidResourceException("Resource is not a protected resource of this domain"));
        }
        return Maybe.just(resource);
    }

    private static void attachTargetResource(TokenRequest tokenRequest, String resource) {
        tokenRequest.setResources(Set.of(resource));
        tokenRequest.setIdJagAssertionContext(tokenRequest.getIdJagAssertionContext().withResource(resource));
    }

    private Maybe<Set<String>> resolveGrantedScopes(TokenRequest tokenRequest, Client client, Map<String, Object> verifiedClaims, String resource) {
        Object assertionScope = verifiedClaims.get(Claims.SCOPE);
        if (assertionScope != null && !(assertionScope instanceof String)) {
            return Maybe.error(new InvalidScopeException("Assertion scope claim must be a space-delimited string"));
        }
        Set<String> assertionScopes = Optional.ofNullable(splitScopes((String) assertionScope)).orElse(Set.of());
        if (!allCoveredBy(protectedResourceManager.getScopesForResources(Set.of(resource)), assertionScopes)) {
            return Maybe.error(new InvalidScopeException("Assertion scope is not defined by the resource's MCP tools"));
        }
        if (!allCoveredBy(applicationScopes(client), assertionScopes)) {
            return Maybe.error(new InvalidScopeException("Assertion scope is not permitted for the application"));
        }
        Set<String> requestedScopes = Optional.ofNullable(tokenRequest.getScopes()).orElse(Set.of());
        if (!assertionScopes.containsAll(requestedScopes)) {
            return Maybe.error(new InvalidScopeException("Requested scope exceeds the assertion scopes"));
        }
        return Maybe.just(requestedScopes.isEmpty() ? assertionScopes : requestedScopes);
    }

    private boolean allCoveredBy(Collection<String> scopePool, Set<String> scopes) {
        List<String> parameterizedScopePool = scopePool.stream()
                .filter(scopeManager::isParameterizedScope)
                .toList();
        return scopes.stream().allMatch(scope -> scopePool.contains(scope)
                || ParameterizedScopeUtils.isParameterizedScope(parameterizedScopePool, scope));
    }

    private static List<String> applicationScopes(Client client) {
        return Optional.ofNullable(client.getScopeSettings()).orElse(List.of()).stream()
                .map(ApplicationScopeSettings::getScope)
                .toList();
    }

    private static void attachGrantedScopes(TokenRequest tokenRequest, Set<String> scopes) {
        tokenRequest.setScopes(scopes);
        tokenRequest.setIdJagAssertionContext(tokenRequest.getIdJagAssertionContext().withScopes(scopes));
    }

    @Override
    protected Maybe<User> resolveExistingResourceOwner(TokenRequest tokenRequest, ResolvedEndUser endUser) {
        return endUser.bindingCriteria().isEmpty()
                ? resolveBySubject(endUser)
                : resolveByBindingCriteria(endUser);
    }

    private Maybe<User> resolveBySubject(ResolvedEndUser endUser) {
        return Maybe.fromOptional(Optional.ofNullable(stringClaim(endUser.verifiedClaims(), Claims.SUB)))
                .flatMap(subject -> userService.findByExternalIdAndSource(subject, endUser.identityProvider()))
                .switchIfEmpty(Maybe.error(() -> new InvalidGrantException("No user matches the assertion subject")));
    }

    private Maybe<User> resolveByBindingCriteria(ResolvedEndUser endUser) {
        return userBindingResolver.resolve(endUser.bindingCriteria(), endUser.verifiedClaims())
                .onErrorResumeNext(error -> Single.error(error instanceof UserBindingException bindingError
                        ? bindingRefusal(bindingError)
                        : error))
                .toMaybe();
    }

    private static InvalidGrantException bindingRefusal(UserBindingException error) {
        return new InvalidGrantException(switch (error.getReason()) {
            case NO_MATCH -> "No user matches the binding rules";
            case SEVERAL_MATCHES -> "Several users match the binding rules";
            case UNUSABLE_CRITERIA -> "Binding rules cannot be evaluated against the assertion";
        });
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
                stringClaim(verifiedClaims, Claims.CLIENT_ID),
                null,
                null);
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

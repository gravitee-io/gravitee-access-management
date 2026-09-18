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
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.extensiongrant.api.ExtensionGrantAssertionTypes;
import io.gravitee.am.extensiongrant.api.ExtensionGrantContext;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.ExtensionGrantResult;
import io.gravitee.am.extensiongrant.api.exceptions.ExtensionGrantException;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidResourceException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.service.binding.UserBindingException;
import io.gravitee.am.gateway.handler.oauth2.service.binding.UserBindingResolver;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.IdJagAssertionContext;
import io.gravitee.am.gateway.handler.oauth2.service.grant.IdJagAssertionContext.BindingMode;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.CustomLog;

/**
 * Strategy for OAuth 2.0 Extension Grants.
 * Handles custom grant types implemented via plugins.
 * Supports both V1 (without SubjectManager) and V2 (with SubjectManager) modes.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6749#section-4.5">RFC 6749 Section 4.5</a>
 * @author GraviteeSource Team
 */
@CustomLog
public class ExtensionGrantStrategy implements GrantStrategy {

    private static final String EXTENSION_GRANT_SEPARATOR = "~";

    private final ExtensionGrantProvider extensionGrantProvider;
    private final ExtensionGrant extensionGrant;
    private final UserAuthenticationManager userAuthenticationManager;
    private final IdentityProviderManager identityProviderManager;
    private final UserGatewayService userService;
    private final SubjectManager subjectManager; // nullable for V1 mode
    private final Domain domain;
    private final OpenIDDiscoveryService openIDDiscoveryService;
    private final UserBindingResolver userBindingResolver;
    @Setter
    private volatile Date minDate;

    /**
     * Constructor for V1 mode (without SubjectManager).
     */
    public ExtensionGrantStrategy(
            ExtensionGrantProvider extensionGrantProvider,
            ExtensionGrant extensionGrant,
            UserAuthenticationManager userAuthenticationManager,
            IdentityProviderManager identityProviderManager,
            UserGatewayService userService,
            Domain domain,
            OpenIDDiscoveryService openIDDiscoveryService) {
        this(extensionGrantProvider, extensionGrant, userAuthenticationManager,
                identityProviderManager, userService, null, domain, openIDDiscoveryService);
    }

    /**
     * Constructor for V2 mode (with SubjectManager).
     */
    public ExtensionGrantStrategy(
            ExtensionGrantProvider extensionGrantProvider,
            ExtensionGrant extensionGrant,
            UserAuthenticationManager userAuthenticationManager,
            IdentityProviderManager identityProviderManager,
            UserGatewayService userService,
            SubjectManager subjectManager,
            Domain domain,
            OpenIDDiscoveryService openIDDiscoveryService) {
        this.extensionGrantProvider = extensionGrantProvider;
        this.extensionGrant = extensionGrant;
        this.userAuthenticationManager = userAuthenticationManager;
        this.identityProviderManager = identityProviderManager;
        this.userService = userService;
        this.subjectManager = subjectManager;
        this.domain = domain;
        this.userBindingResolver = new UserBindingResolver(userService);
        this.openIDDiscoveryService = openIDDiscoveryService;
    }

    @Override
    public boolean supports(TokenRequest request, Client client, Domain domain) {
        return grantTypeMatches(request.getGrantType(), client) &&
                clientCanHandle(client) &&
                extensionGrantProvider.supports(convertToPluginRequest(request));
    }

    private boolean grantTypeMatches(String grantType, Client client) {
        return extensionGrant.getGrantType().equals(grantType);
    }

    private boolean clientCanHandle(Client client) {
        List<String> authorizedGrantTypes = client.getAuthorizedGrantTypes();
        if (authorizedGrantTypes == null || authorizedGrantTypes.isEmpty()) {
            return false;
        }

        // Check for exact match with extension grant ID
        String grantTypeWithId = extensionGrant.getGrantType() + EXTENSION_GRANT_SEPARATOR + extensionGrant.getId();
        if (authorizedGrantTypes.contains(grantTypeWithId)) {
            return true;
        }

        // Check for grant type match when this is the oldest extension grant
        return authorizedGrantTypes.contains(extensionGrant.getGrantType()) &&
                extensionGrant.getCreatedAt().equals(minDate);
    }

    @Override
    public Single<TokenCreationRequest> process(TokenRequest request, Client client, Domain domain) {
        log.debug("Processing extension grant request for client: {}, grant type: {}",
                client.getClientId(), extensionGrant.getGrantType());

        return resolveEndUser(request, client)
                .doOnSuccess(grantResult -> attachGrantedAccess(request, grantResult))
                .flatMap(grantResult -> resolveResourceOwner(request, client, grantResult)
                        .map(user -> createTokenCreationRequest(request, client, user, resolveSource(grantResult.identityProvider()))))
                .switchIfEmpty(Single.fromCallable(() -> createTokenCreationRequest(request, client, null, resolveSource(null))))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof InvalidGrantException
                            || ex instanceof UnauthorizedClientException
                            || ex instanceof InvalidResourceException
                            || ex instanceof InvalidScopeException) {
                        return Single.error(ex);
                    }
                    if (ex instanceof io.gravitee.am.extensiongrant.api.exceptions.InvalidResourceException) {
                        return Single.error(new InvalidResourceException(ex.getMessage()));
                    }
                    if (ex instanceof io.gravitee.am.extensiongrant.api.exceptions.InvalidScopeException) {
                        return Single.error(new InvalidScopeException(ex.getMessage()));
                    }
                    String msg = StringUtils.isBlank(ex.getMessage()) ? "Unknown error" : ex.getMessage();
                    return Single.error(new InvalidGrantException(msg));
                });
    }

    private static void attachGrantedAccess(TokenRequest tokenRequest, ExtensionGrantResult grantResult) {
        if (grantResult.resource() != null) {
            tokenRequest.setResources(Set.of(grantResult.resource()));
        }
        if (grantResult.scopes() != null) {
            tokenRequest.setScopes(grantResult.scopes());
        }
    }

    private Maybe<User> resolveResourceOwner(TokenRequest tokenRequest, Client client, ExtensionGrantResult grantResult) {
        return Maybe.defer(() -> {
                    updateRedemptionContext(tokenRequest, context -> context.withBindingMode(bindingMode(grantResult)));
                    return resolveUser(tokenRequest, client, grantResult);
                })
                .doOnSuccess(user -> updateRedemptionContext(tokenRequest, context -> context.withBoundUser(user.getId())));
    }

    private BindingMode bindingMode(ExtensionGrantResult grantResult) {
        if (extensionGrant.isCreateUser()) {
            return BindingMode.CREATE_USER;
        }
        if (!extensionGrant.isUserExists()) {
            return BindingMode.TRANSIENT;
        }
        return grantResult.bindingCriteria().isEmpty() ? BindingMode.SUBJECT : BindingMode.BINDING_RULES;
    }

    private Maybe<User> resolveUser(TokenRequest tokenRequest, Client client, ExtensionGrantResult grantResult) {
        var endUser = grantResult.endUser();
        if (extensionGrant.isCreateUser()) {
            return manageUserConnect(client, endUser, tokenRequest, resolveSource(grantResult.identityProvider()));
        } else if (extensionGrant.isUserExists()) {
            return resolveExistingResourceOwner(tokenRequest, grantResult);
        } else {
            return forgeUserProfile(endUser);
        }
    }

    private Maybe<User> resolveExistingResourceOwner(TokenRequest tokenRequest, ExtensionGrantResult endUser) {
        if (!endUser.bindingCriteria().isEmpty()) {
            return resolveByBindingCriteria(endUser);
        }
        String identityProvider = resolveIdentityProvider(endUser.identityProvider());
        if (identityProvider == null) {
            return Maybe.error(new InvalidGrantException("No identity_provider provided"));
        }
        return endUser.verifiedClaims().isEmpty()
                ? manageUserValidation(tokenRequest, endUser.endUser(), identityProvider)
                : resolveBySubject(endUser.verifiedClaims(), identityProvider);
    }

    private Maybe<User> resolveBySubject(Map<String, Object> verifiedClaims, String identityProvider) {
        return Maybe.fromOptional(Optional.ofNullable(stringClaim(verifiedClaims, Claims.SUB)))
                .flatMap(subject -> userService.findByExternalIdAndSource(subject, identityProvider))
                .switchIfEmpty(Maybe.error(() -> new InvalidGrantException("No user matches the assertion subject")));
    }

    private Maybe<User> resolveByBindingCriteria(ExtensionGrantResult endUser) {
        List<UserBindingCriterion> criteria = endUser.bindingCriteria().stream()
                .map(binding -> new UserBindingCriterion(binding.attribute(), binding.expression()))
                .toList();
        return userBindingResolver.resolve(criteria, endUser.verifiedClaims())
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

    protected Maybe<ExtensionGrantResult> resolveEndUser(TokenRequest tokenRequest, Client client) {
        var pluginRequest = convertToPluginRequest(tokenRequest);
        if (ExtensionGrantAssertionTypes.isIdJag(pluginRequest)) {
            return redeemIdJag(tokenRequest, client, pluginRequest);
        }
        return extensionGrantProvider.grant(pluginRequest, createExtensionGrantContext(tokenRequest, client));
    }

    private Maybe<ExtensionGrantResult> redeemIdJag(TokenRequest tokenRequest, Client client,
                                                   io.gravitee.am.repository.oauth2.model.request.TokenRequest pluginRequest) {
        return Maybe.defer(() -> {
                    tokenRequest.setIdJagAssertionContext(IdJagAssertionContext.empty());
                    return extensionGrantProvider.grant(pluginRequest, createExtensionGrantContext(tokenRequest, client));
                })
                .doOnError(error -> verifiedResultOf(error)
                        .ifPresent(verified -> tokenRequest.setIdJagAssertionContext(verifiedAssertionContext(verified))))
                .switchIfEmpty(Maybe.error(() -> new InvalidGrantException("Assertion did not resolve an end user")))
                .doOnSuccess(resolved -> tokenRequest.setIdJagAssertionContext(verifiedAssertionContext(resolved)));
    }

    private static Optional<ExtensionGrantResult> verifiedResultOf(Throwable error) {
        return error instanceof ExtensionGrantException refusal
                ? refusal.getVerifiedResult()
                : Optional.empty();
    }

    private static IdJagAssertionContext verifiedAssertionContext(ExtensionGrantResult resolved) {
        Map<String, Object> verifiedClaims = resolved.verifiedClaims();
        return new IdJagAssertionContext(
                stringClaim(verifiedClaims, Claims.ISS),
                resolved.identityProvider(),
                stringClaim(verifiedClaims, Claims.JTI),
                stringClaim(verifiedClaims, Claims.CLIENT_ID),
                resolved.resource(),
                resolved.scopes(),
                null,
                null);
    }

    private static void updateRedemptionContext(TokenRequest tokenRequest, UnaryOperator<IdJagAssertionContext> update) {
        IdJagAssertionContext context = tokenRequest.getIdJagAssertionContext();
        if (context != null) {
            tokenRequest.setIdJagAssertionContext(update.apply(context));
        }
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        return claims.get(name) instanceof String value ? value : null;
    }

    private ExtensionGrantContext createExtensionGrantContext(TokenRequest tokenRequest, Client client) {
        return new ExtensionGrantContext(openIDDiscoveryService.getIssuer(tokenRequest.getOrigin()), tokenRequest.getResources(), applicationScopes(client));
    }

    private static Set<String> applicationScopes(Client client) {
        return Optional.ofNullable(client.getScopeSettings()).orElse(List.of()).stream()
                .map(ApplicationScopeSettings::getScope)
                .collect(Collectors.toSet());
    }

    private String resolveIdentityProvider(String resolvedIdentityProvider) {
        return resolvedIdentityProvider != null ? resolvedIdentityProvider : extensionGrant.getIdentityProvider();
    }

    private String resolveSource(String resolvedIdentityProvider) {
        String identityProvider = resolveIdentityProvider(resolvedIdentityProvider);
        return identityProvider != null ? identityProvider : extensionGrant.getId();
    }

    private TokenCreationRequest createTokenCreationRequest(
            TokenRequest request, Client client, User user, String source) {

        boolean supportRefresh = supportsRefreshToken(client);

        Map<String, Object> additionalClaims = (user != null && user.getAdditionalInformation() != null)
                ? new HashMap<>(user.getAdditionalInformation())
                : new HashMap<>();

        return TokenCreationRequest.forExtensionGrant(
                request,
                user,
                extensionGrant.getId(),
                extensionGrant.getGrantType(),
                additionalClaims,
                source,
                request.getIdJagAssertionContext(),
                supportRefresh
        );
    }

    private boolean supportsRefreshToken(Client client) {
        return (extensionGrant.isCreateUser() || extensionGrant.isUserExists())
                && client.hasGrantType(GrantType.REFRESH_TOKEN)
                && extensionGrantProvider.supportsRefreshToken();
    }

    private Maybe<User> forgeUserProfile(io.gravitee.am.identityprovider.api.User endUser) {
        User user = new User();
        user.setId(endUser.getId());
        user.setUsername(endUser.getUsername());
        user.setAdditionalInformation(endUser.getAdditionalInformation());

        // V2 mode: handle internal subject
        if (subjectManager != null && endUser.getAdditionalInformation() != null) {
            String gis = (String) endUser.getAdditionalInformation().get(Claims.GIO_INTERNAL_SUB);
            if (gis != null) {
                user.setExternalId(subjectManager.extractUserId(gis));
                user.setSource(subjectManager.extractSourceId(gis));
            }
        }

        return Maybe.just(user);
    }

    private Maybe<User> manageUserValidation(
            TokenRequest tokenRequest,
            io.gravitee.am.identityprovider.api.User endUser,
            String identityProvider) {

        return identityProviderManager.get(identityProvider)
                .flatMap(provider -> retrieveUserByUsernameFromIdp(provider, tokenRequest, convertToAmUser(endUser))
                        .switchIfEmpty(Maybe.defer(() -> {
                            log.debug("User name '{}' not found, try as the userId", endUser.getUsername());
                            if (endUser.getId() != null) {
                                return findUserByIdFromIdp(endUser, tokenRequest, provider, identityProvider);
                            }
                            return Maybe.empty();
                        }))
                        .map(idpUser -> {
                            User user = createUser(idpUser, endUser);
                            // V2 mode: set source
                            if (subjectManager != null) {
                                user.setSource(identityProvider);
                            }
                            return user;
                        }))
                .switchIfEmpty(Maybe.error(new InvalidGrantException("Unknown user: " + endUser.getId())));
    }

    private Maybe<io.gravitee.am.identityprovider.api.User> findUserByIdFromIdp(
            io.gravitee.am.identityprovider.api.User endUser,
            TokenRequest tokenRequest,
            AuthenticationProvider provider,
            String identityProvider) {

        if (subjectManager != null) {
            // V2 mode: use SubjectManager for lookup
            final var jwt = new JWT();
            jwt.setSub(endUser.getUsername());
            if (endUser.getAdditionalInformation() != null &&
                    endUser.getAdditionalInformation().containsKey(Claims.GIO_INTERNAL_SUB)) {
                jwt.setInternalSub((String) endUser.getAdditionalInformation().get(Claims.GIO_INTERNAL_SUB));
            }
            return subjectManager.findUserBySub(jwt)
                    .onErrorResumeNext(e -> {
                        if (e instanceof IllegalArgumentException) {
                            log.debug("Subject Manager can't retrieve the profile as sub is invalid, fall back to userService.findById", e);
                            return Maybe.empty();
                        } else {
                            return Maybe.error(e);
                        }
                    })
                    .switchIfEmpty(Maybe.defer(() -> userService.findById(endUser.getUsername())
                            .switchIfEmpty(userService.findByExternalIdAndSource(endUser.getUsername(), identityProvider))))
                    .flatMap(user -> retrieveUserByUsernameFromIdp(provider, tokenRequest, user));
        }
        return userService.findById(endUser.getUsername())
                .flatMap(user -> retrieveUserByUsernameFromIdp(provider, tokenRequest, user));
    }

    private Maybe<User> manageUserConnect(
            Client client,
            io.gravitee.am.identityprovider.api.User endUser,
            Request request,
            String source) {

        // V2 mode: extract user ID from internal subject
        if (subjectManager != null && endUser.getAdditionalInformation() != null) {
            String gis = (String) endUser.getAdditionalInformation().get(Claims.GIO_INTERNAL_SUB);
            if (gis != null) {
                ((DefaultUser) endUser).setId(subjectManager.extractUserId(gis));
            }
        }

        Map<String, Object> additionalInformation = endUser.getAdditionalInformation() == null
                ? new HashMap<>()
                : new HashMap<>(endUser.getAdditionalInformation());

        additionalInformation.put("source", source);
        additionalInformation.put("client_id", client.getId());
        ((DefaultUser) endUser).setAdditionalInformation(additionalInformation);

        return userAuthenticationManager.connect(endUser, request, false)
                .map(connectedUser -> {
                    // V2 mode: set source on connected user
                    if (subjectManager != null) {
                        connectedUser.setSource(source);
                    }
                    return connectedUser;
                })
                .toMaybe();
    }

    private Maybe<io.gravitee.am.identityprovider.api.User> retrieveUserByUsernameFromIdp(
            AuthenticationProvider provider,
            TokenRequest tokenRequest,
            User user) {

        SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(tokenRequest);
        authenticationContext.setDomain(domain);
        Authentication authentication = new EndUserAuthentication(user, null, authenticationContext);
        return provider.loadPreAuthenticatedUser(authentication);
    }

    private User convertToAmUser(io.gravitee.am.identityprovider.api.User idpUser) {
        User newUser = new User();
        newUser.setExternalId(idpUser.getId());
        newUser.setUsername(idpUser.getUsername());
        newUser.setEmail(idpUser.getEmail());
        newUser.setFirstName(idpUser.getFirstName());
        newUser.setLastName(idpUser.getLastName());
        newUser.setAdditionalInformation(idpUser.getAdditionalInformation());
        return newUser;
    }

    protected io.gravitee.am.repository.oauth2.model.request.TokenRequest convertToPluginRequest(TokenRequest tokenRequest) {
        var pluginRequest = new io.gravitee.am.repository.oauth2.model.request.TokenRequest();
        pluginRequest.setClientId(tokenRequest.getClientId());
        pluginRequest.setGrantType(tokenRequest.getGrantType());
        pluginRequest.setScope(tokenRequest.getScopes());
        pluginRequest.setRequestParameters(tokenRequest.parameters().toSingleValueMap());
        return pluginRequest;
    }

    private User createUser(
            io.gravitee.am.identityprovider.api.User idpUser,
            io.gravitee.am.identityprovider.api.User endUser) {

        User user = new User();
        user.setId(endUser.getId());
        user.setExternalId(idpUser.getId());
        user.setUsername(endUser.getUsername());

        Map<String, Object> extraInformation = new HashMap<>(idpUser.getAdditionalInformation());
        if (endUser.getAdditionalInformation() != null) {
            extraInformation.putAll(endUser.getAdditionalInformation());
        }
        if (user.getLoggedAt() != null) {
            extraInformation.put(io.gravitee.am.common.oidc.idtoken.Claims.AUTH_TIME, user.getLoggedAt().getTime() / 1000);
        }
        extraInformation.put(StandardClaims.PREFERRED_USERNAME, user.getUsername());

        user.setAdditionalInformation(extraInformation);
        user.setCreatedAt(idpUser.getCreatedAt());
        user.setUpdatedAt(idpUser.getUpdatedAt());
        user.setDynamicRoles(idpUser.getRoles());
        user.setDynamicGroups(idpUser.getGroups());
        return user;
    }
}

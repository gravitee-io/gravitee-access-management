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
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Strategy for OAuth 2.0 Extension Grants.
 * Handles custom grant types implemented via plugins.
 * Supports both V1 (without SubjectManager) and V2 (with SubjectManager) modes.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6749#section-4.5">RFC 6749 Section 4.5</a>
 * @author GraviteeSource Team
 */
public class ExtensionGrantStrategy implements GrantStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionGrantStrategy.class);
    private static final String EXTENSION_GRANT_SEPARATOR = "~";

    private final ExtensionGrantProvider extensionGrantProvider;
    private final ExtensionGrant extensionGrant;
    private final UserAuthenticationManager userAuthenticationManager;
    private final IdentityProviderManager identityProviderManager;
    private final UserGatewayService userService;
    private final SubjectManager subjectManager; // nullable for V1 mode
    private final Domain domain;
    private Date minDate;

    /**
     * Constructor for V1 mode (without SubjectManager).
     */
    public ExtensionGrantStrategy(
            ExtensionGrantProvider extensionGrantProvider,
            ExtensionGrant extensionGrant,
            UserAuthenticationManager userAuthenticationManager,
            IdentityProviderManager identityProviderManager,
            UserGatewayService userService,
            Domain domain) {
        this(extensionGrantProvider, extensionGrant, userAuthenticationManager,
                identityProviderManager, userService, null, domain);
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
            Domain domain) {
        this.extensionGrantProvider = extensionGrantProvider;
        this.extensionGrant = extensionGrant;
        this.userAuthenticationManager = userAuthenticationManager;
        this.identityProviderManager = identityProviderManager;
        this.userService = userService;
        this.subjectManager = subjectManager;
        this.domain = domain;
    }

    public void setMinDate(Date minDate) {
        this.minDate = minDate;
    }

    @Override
    public boolean supports(String grantType, Client client, Domain domain) {
        if (!extensionGrant.getGrantType().equals(grantType)) {
            return false;
        }
        return canHandle(client);
    }

    private boolean canHandle(Client client) {
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
        LOGGER.debug("Processing extension grant request for client: {}, grant type: {}",
                client.getClientId(), extensionGrant.getGrantType());

        return resolveResourceOwner(request, client)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optUser -> Single.just(
                        createTokenCreationRequest(request, client, optUser.orElse(null))))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof InvalidGrantException || ex instanceof UnauthorizedClientException) {
                        return Single.error(ex);
                    }
                    String msg = StringUtils.isBlank(ex.getMessage()) ? "Unknown error" : ex.getMessage();
                    return Single.error(new InvalidGrantException(msg));
                });
    }

    private Maybe<User> resolveResourceOwner(TokenRequest tokenRequest, Client client) {
        return extensionGrantProvider.grant(convertToPluginRequest(tokenRequest))
                .flatMap(endUser -> {
                    if (extensionGrant.isCreateUser()) {
                        return manageUserConnect(client, endUser, tokenRequest);
                    } else if (extensionGrant.isUserExists()) {
                        if (extensionGrant.getIdentityProvider() == null) {
                            return Maybe.error(new InvalidGrantException("No identity_provider provided"));
                        }
                        return manageUserValidation(tokenRequest, endUser);
                    } else {
                        return forgeUserProfile(endUser);
                    }
                });
    }

    private TokenCreationRequest createTokenCreationRequest(
            TokenRequest request, Client client, User user) {

        // Extension grants support refresh token if they create or validate users
        boolean supportRefresh = (extensionGrant.isCreateUser() || extensionGrant.isUserExists()) &&
                client.hasGrantType(GrantType.REFRESH_TOKEN);

        Map<String, Object> additionalClaims = (user != null && user.getAdditionalInformation() != null)
                ? new HashMap<>(user.getAdditionalInformation())
                : new HashMap<>();

        return TokenCreationRequest.forExtensionGrant(
                request,
                user,
                extensionGrant.getId(),
                extensionGrant.getGrantType(),
                additionalClaims,
                retrieveSourceFrom(extensionGrant),
                supportRefresh
        );
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
            io.gravitee.am.identityprovider.api.User endUser) {

        return identityProviderManager.get(extensionGrant.getIdentityProvider())
                .flatMap(provider -> retrieveUserByUsernameFromIdp(provider, tokenRequest, convertToAmUser(endUser))
                        .switchIfEmpty(Maybe.defer(() -> {
                            LOGGER.debug("User name '{}' not found, try as the userId", endUser.getUsername());
                            if (endUser.getId() != null) {
                                return findUserByIdFromIdp(endUser, tokenRequest, provider);
                            }
                            return Maybe.empty();
                        }))
                        .map(idpUser -> {
                            User user = createUser(idpUser, endUser);
                            // V2 mode: set source
                            if (subjectManager != null) {
                                user.setSource(retrieveSourceFrom(extensionGrant));
                            }
                            return user;
                        }))
                .switchIfEmpty(Maybe.error(new InvalidGrantException("Unknown user: " + endUser.getId())));
    }

    private Maybe<io.gravitee.am.identityprovider.api.User> findUserByIdFromIdp(
            io.gravitee.am.identityprovider.api.User endUser,
            TokenRequest tokenRequest,
            AuthenticationProvider provider) {

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
                            LOGGER.debug("Subject Manager can't retrieve the profile as sub is invalid, fall back to userService.findById", e);
                            return Maybe.empty();
                        } else {
                            return Maybe.error(e);
                        }
                    })
                    .switchIfEmpty(Maybe.defer(() -> userService.findById(endUser.getUsername())
                            .switchIfEmpty(userService.findByExternalIdAndSource(endUser.getUsername(), retrieveSourceFrom(extensionGrant)))))
                    .flatMap(user -> retrieveUserByUsernameFromIdp(provider, tokenRequest, user));
        }
        return userService.findById(endUser.getUsername())
                .flatMap(user -> retrieveUserByUsernameFromIdp(provider, tokenRequest, user));
    }

    private Maybe<User> manageUserConnect(
            Client client,
            io.gravitee.am.identityprovider.api.User endUser,
            Request request) {

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

        additionalInformation.put("source", retrieveSourceFrom(extensionGrant));
        additionalInformation.put("client_id", client.getId());
        ((DefaultUser) endUser).setAdditionalInformation(additionalInformation);

        return userAuthenticationManager.connect(endUser, request, false)
                .map(connectedUser -> {
                    // V2 mode: set source on connected user
                    if (subjectManager != null) {
                        connectedUser.setSource(retrieveSourceFrom(extensionGrant));
                    }
                    return connectedUser;
                })
                .toMaybe();
    }

    private String retrieveSourceFrom(ExtensionGrant extGrant) {
        return extGrant.getIdentityProvider() != null
                ? extGrant.getIdentityProvider()
                : extGrant.getId();
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

    private io.gravitee.am.repository.oauth2.model.request.TokenRequest convertToPluginRequest(TokenRequest tokenRequest) {
        io.gravitee.am.repository.oauth2.model.request.TokenRequest pluginRequest =
                new io.gravitee.am.repository.oauth2.model.request.TokenRequest();
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

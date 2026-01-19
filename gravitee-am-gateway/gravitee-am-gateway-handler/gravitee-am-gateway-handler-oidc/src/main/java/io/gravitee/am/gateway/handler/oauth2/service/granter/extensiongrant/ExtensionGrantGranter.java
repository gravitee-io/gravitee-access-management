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
package io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant;

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.service.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the Extension Grants
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.5">4.5. Extension Grants</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter(AccessLevel.PROTECTED)
@Slf4j
public class ExtensionGrantGranter extends AbstractTokenGranter {
    private static final String EXTENSION_GRANT_SEPARATOR = "~";
    private final ExtensionGrantProvider extensionGrantProvider;
    private final ExtensionGrant extensionGrant;
    private final UserAuthenticationManager userAuthenticationManager;
    private final IdentityProviderManager identityProviderManager;
    @Setter
    private Date minDate;
    private final UserGatewayService userService;
    private final Domain domain;

    public ExtensionGrantGranter(ExtensionGrantProvider extensionGrantProvider,
                                 ExtensionGrant extensionGrant,
                                 UserAuthenticationManager userAuthenticationManager,
                                 TokenService tokenService,
                                 TokenRequestResolver tokenRequestResolver,
                                 IdentityProviderManager identityProviderManager,
                                 UserGatewayService userService,
                                 RulesEngine rulesEngine,
                                 Domain domain) {
        super(extensionGrant.getGrantType());
        setTokenService(tokenService);
        setTokenRequestResolver(tokenRequestResolver);
        setSupportRefreshToken(extensionGrant.isCreateUser() || extensionGrant.isUserExists());
        setRulesEngine(rulesEngine);
        this.extensionGrantProvider = extensionGrantProvider;
        this.extensionGrant = extensionGrant;
        this.userAuthenticationManager = userAuthenticationManager;
        this.identityProviderManager = identityProviderManager;
        this.userService = userService;
        this.domain = domain;
    }

    @Override
    public boolean handle(String grantType, Client client) {
        return super.handle(grantType, client) && canHandle(client);
    }

    @Override
    protected Single<TokenRequest> parseRequest(TokenRequest tokenRequest, Client client) {
        // Is client allowed to use such grant type ?
        if (!canHandle(client)) {
            throw new UnauthorizedClientException("Unauthorized grant type: " + extensionGrant.getGrantType());
        }
        return Single.just(tokenRequest);
    }

    private boolean canHandle(Client client) {
        final List<String> authorizedGrantTypes = client.getAuthorizedGrantTypes();
        return authorizedGrantTypes != null && !authorizedGrantTypes.isEmpty()
                && (authorizedGrantTypes.contains(extensionGrant.getGrantType() + EXTENSION_GRANT_SEPARATOR + extensionGrant.getId())
                || authorizedGrantTypes.contains(extensionGrant.getGrantType()) && extensionGrant.getCreatedAt().equals(minDate));
    }

    @Override
    protected Maybe<User> resolveResourceOwner(TokenRequest tokenRequest, Client client) {
        return extensionGrantProvider.grant(convert(tokenRequest))
                .flatMap(endUser -> {
                    enrichTokenExchangeContext(tokenRequest, endUser);
                    if (extensionGrant.isCreateUser()) {
                        return manageUserConnect(client, endUser, tokenRequest);
                    } else {
                        // Check that the user is existing from the identity provider
                        if (extensionGrant.isUserExists()) {
                            if (extensionGrant.getIdentityProvider() == null) {
                                return Maybe.error(new InvalidGrantException("No identity_provider provided"));
                            }
                            return manageUserValidation(tokenRequest, endUser, client);
                        } else {
                            return forgeUserProfile(endUser);
                        }
                    }
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof OAuth2Exception) {
                        return Maybe.error(ex);
                    }
                    String msg = StringUtils.isBlank(ex.getMessage()) ? "Unknown error" : ex.getMessage();
                    return Maybe.error(new InvalidGrantException(msg));
                });
    }

    /**
     * RFC 8693 Token Exchange: Transfer metadata from user to request context.
     * This ensures TokenService can access issued_token_type and other metadata.
     */
    private void enrichTokenExchangeContext(TokenRequest tokenRequest, io.gravitee.am.identityprovider.api.User endUser) {
        if (endUser == null || endUser.getAdditionalInformation() == null) {
            return;
        }

        Map<String, Object> additionalInfo = endUser.getAdditionalInformation();

        // Initialize context if needed
        if (tokenRequest.getContext() == null) {
            tokenRequest.setContext(new HashMap<>());
        }

        // Transfer issued_token_type to context for TokenService
        Object issuedTokenType = additionalInfo.get("issued_token_type");
        if (issuedTokenType != null) {
            tokenRequest.getContext().put(Token.ISSUED_TOKEN_TYPE, issuedTokenType.toString());
            log.debug("RFC 8693: Set issued_token_type={} in request context", issuedTokenType);
        }

        // Transfer token_type hint to context for TokenService
        Object tokenType = additionalInfo.get("token_type");
        if (tokenType != null) {
            tokenRequest.getContext().put("token_type", tokenType.toString());
        }
    }

    protected Maybe<User> forgeUserProfile(io.gravitee.am.identityprovider.api.User endUser) {
        User user = new User();
        // we do not router AM user, user id is the idp user id
        user.setId(endUser.getId());
        user.setUsername(endUser.getUsername());
        user.setAdditionalInformation(endUser.getAdditionalInformation());
        return Maybe.just(user);
    }

    protected Maybe<User> manageUserValidation(TokenRequest tokenRequest, io.gravitee.am.identityprovider.api.User endUser, Client client) {
        return identityProviderManager
                .get(extensionGrant.getIdentityProvider())
                .flatMap(prov -> retrieveUserByUsernameFromIdp(prov, tokenRequest, convert(endUser))
                        .switchIfEmpty(Maybe.defer(() -> {
                            log.debug("User name '{}' not found, try as the userId", endUser.getUsername());
                            if (endUser.getId() != null) {
                                // MongoIDP & JDBC IDP, set the userId as SUB claim, this claim is used as username by extensionGrantProvider.grant()
                                // so the search by ID should be done with the username...
                                return userService.findById(endUser.getUsername())
                                        .flatMap(user -> retrieveUserByUsernameFromIdp(prov, tokenRequest, user));
                            }
                            return Maybe.empty();
                        })))
                .map(idpUser -> createUser(idpUser, endUser))
                .switchIfEmpty(Maybe.error(new InvalidGrantException("Unknown user: " + endUser.getId())));
    }

    protected Maybe<User> manageUserConnect(Client client, io.gravitee.am.identityprovider.api.User endUser, Request request) {
        Map<String, Object> additionalInformation = endUser.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(endUser.getAdditionalInformation());
        // set source provider
        additionalInformation.put("source", retrieveSourceFrom(extensionGrant));
        additionalInformation.put("client_id", client.getId());
        ((DefaultUser) endUser).setAdditionalInformation(additionalInformation);
        return userAuthenticationManager.connect(endUser, request, false).toMaybe();
    }

    protected final String retrieveSourceFrom(ExtensionGrant extGrant) {
        return extGrant.getIdentityProvider() != null ? extGrant.getIdentityProvider() : extGrant.getId();
    }

    protected final Maybe<io.gravitee.am.identityprovider.api.User> retrieveUserByUsernameFromIdp(AuthenticationProvider provider, TokenRequest tokenRequest, User user) {
        SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(tokenRequest);
        authenticationContext.setDomain(domain);
        final Authentication authentication = new EndUserAuthentication(user, null, authenticationContext);
        return provider.loadPreAuthenticatedUser(authentication);
    }

    protected final User convert(io.gravitee.am.identityprovider.api.User idpUser) {
        User newUser = new User();
        newUser.setExternalId(idpUser.getId());
        newUser.setUsername(idpUser.getUsername());
        newUser.setEmail(idpUser.getEmail());
        newUser.setFirstName(idpUser.getFirstName());
        newUser.setLastName(idpUser.getLastName());
        newUser.setAdditionalInformation(idpUser.getAdditionalInformation());
        return newUser;
    }

    private io.gravitee.am.repository.oauth2.model.request.TokenRequest convert(TokenRequest _tokenRequest) {
        io.gravitee.am.repository.oauth2.model.request.TokenRequest tokenRequest = new io.gravitee.am.repository.oauth2.model.request.TokenRequest();
        tokenRequest.setClientId(_tokenRequest.getClientId());
        tokenRequest.setGrantType(_tokenRequest.getGrantType());
        tokenRequest.setScope(_tokenRequest.getScopes());
        tokenRequest.setRequestParameters(_tokenRequest.parameters().toSingleValueMap());
        tokenRequest.setResources(_tokenRequest.getResources());
        tokenRequest.setAudiences(_tokenRequest.getAudiences());

        return tokenRequest;
    }

    protected final User createUser(io.gravitee.am.identityprovider.api.User idpUser, io.gravitee.am.identityprovider.api.User endUser) {
        User user = new User();
        user.setId(endUser.getId());
        user.setExternalId(idpUser.getId());
        user.setUsername(endUser.getUsername());

        Map<String, Object> extraInformation = new HashMap<>(idpUser.getAdditionalInformation());
        if (endUser.getAdditionalInformation() != null) {
            extraInformation.putAll(endUser.getAdditionalInformation());
        }
        if (user.getLoggedAt() != null) {
            extraInformation.put(Claims.AUTH_TIME, user.getLoggedAt().getTime() / 1000);
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

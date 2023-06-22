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

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.service.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class ExtensionGrantGranter extends AbstractTokenGranter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionGrantGranter.class);
    private static final String EXTENSION_GRANT_SEPARATOR = "~";
    private final ExtensionGrantProvider extensionGrantProvider;
    private final ExtensionGrant extensionGrant;
    private final UserAuthenticationManager userAuthenticationManager;
    private final IdentityProviderManager identityProviderManager;
    private Date minDate;
    private UserService userService;

    public ExtensionGrantGranter(ExtensionGrantProvider extensionGrantProvider,
                                 ExtensionGrant extensionGrant,
                                 UserAuthenticationManager userAuthenticationManager,
                                 TokenService tokenService,
                                 TokenRequestResolver tokenRequestResolver,
                                 IdentityProviderManager identityProviderManager,
                                 UserService userService,
                                 RulesEngine rulesEngine) {
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

    @Override
    protected Maybe<User> resolveResourceOwner(TokenRequest tokenRequest, Client client) {
        return extensionGrantProvider.grant(convert(tokenRequest))
                .flatMap(endUser -> {
                    if (extensionGrant.isCreateUser()) {
                        Map<String, Object> additionalInformation = endUser.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(endUser.getAdditionalInformation());
                        // set source provider
                        additionalInformation.put("source", extensionGrant.getIdentityProvider() != null ? extensionGrant.getIdentityProvider() : extensionGrant.getId());
                        additionalInformation.put("client_id", client.getId());
                        ((DefaultUser) endUser).setAdditionalInformation(additionalInformation);
                        return userAuthenticationManager.connect(endUser, false).toMaybe();
                    } else {
                        // Check that the user is existing from the identity provider
                        if (extensionGrant.isUserExists()) {
                            if (extensionGrant.getIdentityProvider() == null) {
                                return Maybe.error(new InvalidGrantException("No identity_provider provided"));
                            }
                            return identityProviderManager
                                    .get(extensionGrant.getIdentityProvider())
                                    .flatMap(prov -> retrieveUserByUsernameFromIdp(prov, tokenRequest, convert(endUser))
                                            .switchIfEmpty(Maybe.defer(() -> {
                                                LOGGER.debug("User name '{}' not found, try as the userId", endUser.getUsername());
                                                if (endUser.getId() != null) {
                                                    // MongoIDP & JDBC IDP, set the userId as SUB claim, this claim is used as username by extensionGrantProvider.grant()
                                                    // so the search by ID should be done with the username...
                                                    return userService.findById(endUser.getUsername())
                                                            .flatMap(user -> retrieveUserByUsernameFromIdp(prov, tokenRequest, user));
                                                }
                                                return Maybe.empty();
                                            })))
                                    .map(idpUser -> {
                                        User user = new User();
                                        user.setId(endUser.getId());
                                        user.setExternalId(idpUser.getId());
                                        user.setUsername(endUser.getUsername());

                                        Map<String, Object> extraInformation = new HashMap<>(idpUser.getAdditionalInformation());
                                        if (endUser.getAdditionalInformation() != null) {
                                            extraInformation.putAll(endUser.getAdditionalInformation());
                                        }
                                        if (user.getLoggedAt() != null) {
                                            extraInformation.put(Claims.auth_time, user.getLoggedAt().getTime() / 1000);
                                        }
                                        extraInformation.put(StandardClaims.PREFERRED_USERNAME, user.getUsername());

                                        user.setAdditionalInformation(extraInformation);
                                        user.setCreatedAt(idpUser.getCreatedAt());
                                        user.setUpdatedAt(idpUser.getUpdatedAt());
                                        user.setDynamicRoles(idpUser.getRoles());
                                        return user;
                                    })
                                    .switchIfEmpty(Maybe.error(new InvalidGrantException("Unknown user: " + endUser.getId())));
                        } else {
                            User user = new User();
                            // we do not router AM user, user id is the idp user id
                            user.setId(endUser.getId());
                            user.setUsername(endUser.getUsername());
                            user.setAdditionalInformation(endUser.getAdditionalInformation());
                            return Maybe.just(user);
                        }
                    }
                })
                .onErrorResumeNext(ex -> {
                    return Maybe.error(new InvalidGrantException(ex.getMessage()));
                });
    }

    private Maybe<io.gravitee.am.identityprovider.api.User> retrieveUserByUsernameFromIdp(AuthenticationProvider provider, TokenRequest tokenRequest, User user) {
        SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(tokenRequest);
        final Authentication authentication = new EndUserAuthentication(user, null, authenticationContext);
        final Maybe<io.gravitee.am.identityprovider.api.User> userMaybe = provider.loadPreAuthenticatedUser(authentication);
        return userMaybe;
    }

    private User convert(io.gravitee.am.identityprovider.api.User idpUser) {
        User newUser = new User();
        newUser.setExternalId(idpUser.getId());
        newUser.setUsername(idpUser.getUsername());
        newUser.setEmail(idpUser.getEmail());
        newUser.setFirstName(idpUser.getFirstName());
        newUser.setLastName(idpUser.getLastName());
        newUser.setAdditionalInformation(idpUser.getAdditionalInformation());
        return newUser;
    }

    public void setMinDate(Date minDate) {
        this.minDate = minDate;
    }

    private io.gravitee.am.repository.oauth2.model.request.TokenRequest convert(TokenRequest _tokenRequest) {
        io.gravitee.am.repository.oauth2.model.request.TokenRequest tokenRequest = new io.gravitee.am.repository.oauth2.model.request.TokenRequest();
        tokenRequest.setClientId(_tokenRequest.getClientId());
        tokenRequest.setGrantType(_tokenRequest.getGrantType());
        tokenRequest.setScope(_tokenRequest.getScopes());
        tokenRequest.setRequestParameters(_tokenRequest.parameters().toSingleValueMap());

        return tokenRequest;
    }

    private boolean canHandle(Client client) {
        final List<String> authorizedGrantTypes = client.getAuthorizedGrantTypes();
        return authorizedGrantTypes != null && !authorizedGrantTypes.isEmpty()
                && ( authorizedGrantTypes.contains(extensionGrant.getGrantType() + EXTENSION_GRANT_SEPARATOR + extensionGrant.getId())
                    || authorizedGrantTypes.contains(extensionGrant.getGrantType()) && extensionGrant.getCreatedAt().equals(minDate));
    }
}

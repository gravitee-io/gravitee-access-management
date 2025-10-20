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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the Extension Grants
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.5">4.5. Extension Grants</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class ExtensionGrantGranterV2 extends ExtensionGrantGranter {

    private final SubjectManager subjectManager;

    public ExtensionGrantGranterV2(ExtensionGrantProvider extensionGrantProvider,
                                   ExtensionGrant extensionGrant,
                                   UserAuthenticationManager userAuthenticationManager,
                                   TokenService tokenService,
                                   TokenRequestResolver tokenRequestResolver,
                                   IdentityProviderManager identityProviderManager,
                                   UserGatewayService userService,
                                   RulesEngine rulesEngine,
                                   SubjectManager subjectManager,
                                   Domain domain) {
        super(extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                tokenService,
                tokenRequestResolver,
                identityProviderManager,
                userService,
                rulesEngine,
                domain);
        this.subjectManager = subjectManager;
    }

    @Override
    protected Maybe<User> manageUserConnect(Client client, io.gravitee.am.identityprovider.api.User endUser, Request request) {
        String gis = (String) endUser.getAdditionalInformation().get(Claims.GIO_INTERNAL_SUB);
        if (gis != null) {
            ((DefaultUser) endUser).setId(subjectManager.extractUserId(gis));
        }
        return super.manageUserConnect(client, endUser, request).map(connectedUser -> {
            connectedUser.setSource(retrieveSourceFrom(getExtensionGrant()));
            return connectedUser;
        });
    }

    @Override
    protected Maybe<User> forgeUserProfile(io.gravitee.am.identityprovider.api.User endUser) {
        User user = new User();
        // we do not router AM user, user id is the idp user id
        user.setId(endUser.getId());
        user.setUsername(endUser.getUsername());
        user.setAdditionalInformation(endUser.getAdditionalInformation());

        String gis = (String) endUser.getAdditionalInformation().get(Claims.GIO_INTERNAL_SUB);
        if (gis != null) {
            user.setExternalId(subjectManager.extractUserId(gis));
            user.setSource(subjectManager.extractSourceId(gis));
        }
        return Maybe.just(user);
    }

    protected Maybe<User> manageUserValidation(TokenRequest tokenRequest, io.gravitee.am.identityprovider.api.User endUser, Client client) {
        return getIdentityProviderManager()
                .get(getExtensionGrant().getIdentityProvider())
                .flatMap(prov -> retrieveUserByUsernameFromIdp(prov, tokenRequest, convert(endUser))
                        .switchIfEmpty(Maybe.defer(() -> {
                            log.debug("User name '{}' not found, try as the userId", endUser.getUsername());
                            if (endUser.getId() != null) {
                                // MongoIDP & JDBC IDP, set the userId as SUB claim, this claim is used as username by extensionGrantProvider.grant()
                                // so the search by ID should be done with the username...
                                final var jwt = new JWT();
                                jwt.setSub(endUser.getUsername());
                                if (endUser.getAdditionalInformation().containsKey(Claims.GIO_INTERNAL_SUB)) {
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
                                        .switchIfEmpty(getUserService().findById(endUser.getUsername())
                                                .switchIfEmpty(getUserService().findByExternalIdAndSource(endUser.getUsername(), retrieveSourceFrom(getExtensionGrant()))))
                                        .flatMap(user -> retrieveUserByUsernameFromIdp(prov, tokenRequest, user));
                            }
                            return Maybe.empty();
                        })))
                .map(idpUser -> createUser(idpUser, endUser)).map(user -> {
                    user.setSource(retrieveSourceFrom(getExtensionGrant()));
                    return user;
                })
                .switchIfEmpty(Maybe.error(new InvalidGrantException("Unknown user: " + endUser.getId())));
    }
}

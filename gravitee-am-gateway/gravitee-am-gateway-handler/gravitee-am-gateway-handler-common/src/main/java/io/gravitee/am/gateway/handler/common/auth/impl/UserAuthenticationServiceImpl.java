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
package io.gravitee.am.gateway.handler.common.auth.impl;

import io.gravitee.am.common.exception.authentication.AccountDisabledException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.gateway.handler.common.auth.UserAuthenticationService;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthenticationServiceImpl implements UserAuthenticationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAuthenticationServiceImpl.class);
    private static final String SOURCE_FIELD = "source";

    @Autowired
    private Domain domain;

    @Autowired
    private UserService userService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Override
    public Single<User> connect(io.gravitee.am.identityprovider.api.User principal, boolean afterAuthentication) {
        // save or update the user
        return saveOrUpdate(principal, afterAuthentication)
                // check account status
                .flatMap(user -> checkAccountStatus(user)
                        // and enhance user information
                        .andThen(Single.defer(() -> userService.enhance(user))));
    }

    @Override
    public Maybe<User> loadPreAuthenticatedUser(String subject) {
        // find user by its technical id
        return userService
                .findById(subject)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(subject)))
                .flatMap(user -> identityProviderManager.get(user.getSource())
                        // if the user has been found, try to load user information from its latest identity provider
                        .flatMap(authenticationProvider -> authenticationProvider.loadUserByUsername(user.getUsername()))
                        .flatMap(idpUser -> {
                            // retrieve information from the idp user and update the user
                            Map<String, Object> additionalInformation = idpUser.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(idpUser.getAdditionalInformation());
                            additionalInformation.put(SOURCE_FIELD, user.getSource());
                            additionalInformation.put(Parameters.CLIENT_ID, user.getClient());
                            ((DefaultUser) idpUser).setAdditionalInformation(additionalInformation);
                            return update(user, idpUser, false)
                                    .flatMap(userService::enhance).toMaybe();
                        })
                        // no user has been found in the identity provider, just enhance user information
                        .switchIfEmpty(Maybe.defer(() -> userService.enhance(user).toMaybe())));
    }

    private Single<User> saveOrUpdate(io.gravitee.am.identityprovider.api.User principal, boolean afterAuthentication) {
        String source = (String) principal.getAdditionalInformation().get(SOURCE_FIELD);
        return userService.findByDomainAndExternalIdAndSource(domain.getId(), principal.getId(), source)
                .switchIfEmpty(Maybe.defer(() -> userService.findByDomainAndUsernameAndSource(domain.getId(), principal.getUsername(), source)))
                .switchIfEmpty(Maybe.error(new UserNotFoundException(principal.getUsername())))
                .flatMapSingle(existingUser -> update(existingUser, principal, afterAuthentication))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof UserNotFoundException) {
                        return create(principal, afterAuthentication);
                    }
                    return Single.error(ex);
                });
    }

    /**
     * Check the user account status
     * @param user Authenticated user
     * @return Completable.complete() or Completable.error(error) if account status is not ok
     */
    private Completable checkAccountStatus(User user) {
        if (!user.isEnabled()) {
            return Completable.error(new AccountDisabledException("Account is disabled for user " + user.getUsername()));
        }
        return Completable.complete();
    }

    /**
     * Update user information with data from the identity provider user
     * @param existingUser existing user in the repository
     * @param principal user from the identity provider
     * @param afterAuthentication if update operation is called after a sign in operation
     * @return updated user
     */
    private Single<User> update(User existingUser, io.gravitee.am.identityprovider.api.User principal, boolean afterAuthentication) {
        LOGGER.debug("Updating user: username[%s]", principal.getUsername());
        // set external id
        existingUser.setExternalId(principal.getId());
        if (afterAuthentication) {
            existingUser.setLoggedAt(new Date());
            existingUser.setLoginsCount(existingUser.getLoginsCount() + 1);
        }
        // set roles
        if (existingUser.getRoles() == null) {
            existingUser.setRoles(principal.getRoles());
        } else if (principal.getRoles() != null) {
            // filter roles
            principal.getRoles().removeAll(existingUser.getRoles());
            existingUser.getRoles().addAll(principal.getRoles());
        }
        Map<String, Object> additionalInformation = principal.getAdditionalInformation();
        extractAdditionalInformation(existingUser, additionalInformation);
        return userService.update(existingUser);
    }

    /**
     * Create user with data from the identity provider user
     * @param principal user from the identity provider
     * @param afterAuthentication if create operation is called after a sign in operation
     * @return created user
     */
    private Single<User> create(io.gravitee.am.identityprovider.api.User principal, boolean afterAuthentication) {
        LOGGER.debug("Creating a new user: username[%s]", principal.getUsername());
        final User newUser = new User();
        // set external id
        newUser.setExternalId(principal.getId());
        newUser.setUsername(principal.getUsername());
        newUser.setEmail(principal.getEmail());
        newUser.setFirstName(principal.getFirstName());
        newUser.setLastName(principal.getLastName());
        newUser.setDomain(domain.getId());
        if (afterAuthentication) {
            newUser.setLoggedAt(new Date());
            newUser.setLoginsCount(1L);
        }
        newUser.setRoles(principal.getRoles());

        Map<String, Object> additionalInformation = principal.getAdditionalInformation();
        extractAdditionalInformation(newUser, additionalInformation);
        return userService.create(newUser);
    }

    private void extractAdditionalInformation(User user, Map<String, Object> additionalInformation) {
        if (additionalInformation != null) {
            Map<String, Object> extraInformation = new HashMap<>(additionalInformation);
            if (user.getLoggedAt() != null) {
                extraInformation.put(Claims.auth_time, user.getLoggedAt().getTime() / 1000);
            }
            extraInformation.put(StandardClaims.PREFERRED_USERNAME, user.getUsername());
            user.setSource((String) extraInformation.remove(SOURCE_FIELD));
            user.setClient((String) extraInformation.remove(Parameters.CLIENT_ID));
            user.setAdditionalInformation(extraInformation);
        }
    }
}

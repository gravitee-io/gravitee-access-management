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
package io.gravitee.am.gateway.handler.account.services.impl;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.account.services.AccountManagementUserService;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.validators.UserValidator;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AccountManagementUserServiceImpl implements AccountManagementUserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountManagementUserServiceImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private UserValidator userValidator;

    @Override
    public Single<User> update(User user) {
        LOGGER.debug("Update a user {} for domain {}", user.getUsername(), domain.getName());

        return userValidator.validate(user).andThen(identityProviderManager.getUserProvider(user.getSource())
                .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                .flatMapSingle(userProvider -> {
                    if (user.getExternalId() == null) {
                        return Single.error(new InvalidRequestException("User does not exist in upstream IDP"));
                    } else {
                        return userProvider.update(user.getExternalId(), convert(user));
                    }
                })
                .flatMap(idpUser -> {
                    return userRepository.update(user);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof UserNotFoundException || ex instanceof UserInvalidException) {
                        // idp user does not exist, only update AM user
                        // clear password
                        user.setPassword(null);
                        return userRepository.update(user);
                    }
                    return Single.error(ex);
                }));

    }


    private io.gravitee.am.identityprovider.api.User convert(io.gravitee.am.model.User user) {
        DefaultUser idpUser = new DefaultUser(user.getUsername());
        idpUser.setId(user.getExternalId());
        idpUser.setCredentials(user.getPassword());

        Map<String, Object> additionalInformation = new HashMap<>();
        if (user.getFirstName() != null) {
            idpUser.setFirstName(user.getFirstName());
            additionalInformation.put(StandardClaims.GIVEN_NAME, user.getFirstName());
        }
        if (user.getLastName() != null) {
            idpUser.setLastName(user.getLastName());
            additionalInformation.put(StandardClaims.FAMILY_NAME, user.getLastName());
        }
        if (user.getEmail() != null) {
            idpUser.setEmail(user.getEmail());
            additionalInformation.put(StandardClaims.EMAIL, user.getEmail());
        }
        if (user.getAdditionalInformation() != null) {
            user.getAdditionalInformation().forEach((k, v) -> additionalInformation.putIfAbsent(k, v));
        }
        idpUser.setAdditionalInformation(additionalInformation);
        return idpUser;
    }

}

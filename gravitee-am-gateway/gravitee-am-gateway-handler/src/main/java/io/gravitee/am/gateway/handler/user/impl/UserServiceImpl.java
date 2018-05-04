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
package io.gravitee.am.gateway.handler.user.impl;

import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserServiceImpl implements UserService {

    private final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Single<User> findOrCreate(io.gravitee.am.identityprovider.api.User user) {
        return userRepository.findByUsernameAndDomain(domain.getId(), user.getUsername())
                .switchIfEmpty(Maybe.error(new UserNotFoundException(user.getUsername())))
                .flatMapSingle(existingUser -> {
                    logger.debug("Updating user: username[%s]", user.getUsername());
                    existingUser.setLoggedAt(new Date());
                    existingUser.setLoginsCount(existingUser.getLoginsCount() + 1);
                    existingUser.setRoles(user.getRoles());
                    existingUser.setAdditionalInformation(user.getAdditionalInformation());
                    //TODO: How to map these informations ?
                    /*
                    if (details != null) {
                        newUser.setSource(details.get(SOURCE));
                        newUser.setClient(CLIENT_ID);
                    }
                    */
                    return userRepository.update(existingUser);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof UserNotFoundException) {
                        logger.debug("Creating a new user: username[%s]", user.getUsername());
                        final User newUser = new User();
                        newUser.setUsername(user.getUsername());
                        //TODO: How to map these informations ?
                        /*
                        if (details != null) {
                            newUser.setSource(details.get(SOURCE));
                            newUser.setClient(CLIENT_ID);
                        }
                        */
                        newUser.setDomain(domain.getId());
                        newUser.setCreatedAt(new Date());
                        newUser.setLoggedAt(new Date());
                        newUser.setLoginsCount(1L);
                        newUser.setRoles(user.getRoles());
                        newUser.setAdditionalInformation(user.getAdditionalInformation());
                        return userRepository.create(newUser);
                    }
                    return Single.error(ex);
                });
    }

    @Override
    public Maybe<User> findById(String id) {
        return userRepository.findById(id);
    }
}

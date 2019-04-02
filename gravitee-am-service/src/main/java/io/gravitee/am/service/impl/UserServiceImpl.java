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
package io.gravitee.am.service.impl;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.GroupRepository;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UserServiceImpl implements UserService {

    private final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final String GROUP_MAPPING_ATTRIBUTE = "_RESERVED_AM_GROUP_MAPPING_";
    private static final String SOURCE_FIELD = "source";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Override
    public Single<Set<User>> findByDomain(String domain) {
        LOGGER.debug("Find users by domain: {}", domain);
        return userRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by domain %s", domain), ex));
                });
    }

    @Override
    public Single<Page<User>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("Find users by domain: {}", domain);
        return userRepository.findByDomain(domain, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by domain %s", domain), ex));
                });
    }

    @Override
    public Single<Page<User>> search(String domain, String query, int limit) {
        LOGGER.debug("Search users for domain {} with query {}", domain, query);
        return userRepository.search(domain, query, limit)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to search users for domain {} and query {}", domain, query, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users for domain %s and query %s", domain, query), ex));
                });
    }

    @Override
    public Single<List<User>> findByIdIn(List<String> ids) {
        String userIds = String.join(",", ids);
        LOGGER.debug("Find users by ids: {}", userIds);
        return userRepository.findByIdIn(ids)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by ids {}", userIds, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by ids %s", userIds), ex));
                });
    }

    @Override
    public Maybe<User> findById(String id) {
        LOGGER.debug("Find user by id : {}", id);
        return userRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s", id), ex));
                });
    }


    @Override
    public Maybe<User> findByDomainAndUsername(String domain, String username) {
        LOGGER.debug("Find user by username and domain: {} {}", username, domain);
        return userRepository.findByUsernameAndDomain(domain, username)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID: {} for the domain {}", username, domain, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s for the domain %s", username, domain), ex));
                });
    }

    @Override
    public Single<User> create(String domain, NewUser newUser) {
        LOGGER.debug("Create a new user {} for domain {}", newUser, domain);

        return userRepository.findByDomainAndUsernameAndSource(domain, newUser.getUsername(), newUser.getSource())
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (!isEmpty) {
                        return Single.error(new UserAlreadyExistsException(newUser.getUsername()));
                    } else {
                        String userId = RandomString.generate();

                        User user = new User();
                        user.setId(userId);
                        user.setExternalId(newUser.getExternalId());
                        user.setDomain(domain);
                        user.setClient(newUser.getClient());
                        user.setUsername(newUser.getUsername());
                        user.setFirstName(newUser.getFirstName());
                        user.setLastName(newUser.getLastName());
                        if (user.getFirstName() != null) {
                            user.setDisplayName(user.getFirstName() + (user.getLastName() != null ? " " + user.getLastName() : ""));
                        }
                        user.setEmail(newUser.getEmail());
                        user.setSource(newUser.getSource());
                        user.setInternal(true);
                        user.setPreRegistration(newUser.isPreRegistration());
                        user.setRegistrationCompleted(newUser.isRegistrationCompleted());
                        user.setAdditionalInformation(newUser.getAdditionalInformation());
                        user.setCreatedAt(new Date());
                        user.setUpdatedAt(user.getCreatedAt());
                        return userRepository.create(user);
                    }
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    } else {
                        LOGGER.error("An error occurs while trying to create a user", ex);
                        return Single.error(new TechnicalManagementException("An error occurs while trying to create a user", ex));
                    }
                });
    }

    @Override
    public Single<User> update(String domain, String id, UpdateUser updateUser) {
        LOGGER.debug("Update a user {} for domain {}", id, domain);

        return userRepository.findById(id)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(id)))
                .flatMapSingle(oldUser -> {
                    oldUser.setClient(updateUser.getClient());
                    oldUser.setExternalId(updateUser.getExternalId());
                    oldUser.setFirstName(updateUser.getFirstName());
                    oldUser.setLastName(updateUser.getLastName());
                    oldUser.setEmail(updateUser.getEmail());
                    oldUser.setEnabled(updateUser.isEnabled());
                    oldUser.setUpdatedAt(new Date());
                    oldUser.setAdditionalInformation(updateUser.getAdditionalInformation());
                    return userRepository.update(oldUser);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a user", ex));
                });
    }

    @Override
    public Completable delete(String userId) {
        LOGGER.debug("Delete user {}", userId);

        return userRepository.findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapCompletable(user -> userRepository.delete(userId))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete user: {}", userId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete user: %s", userId), ex));
                });
    }


    /**
     * Moved from io.gravitee.am.gateway.service.impl.UserServiceImpl to current implementation.
     */
    @Override
    public Single<User> findOrCreate(String domain,io.gravitee.am.identityprovider.api.User user) {
        String source = (String) user.getAdditionalInformation().get("source");
        return userRepository.findByDomainAndUsernameAndSource(domain, user.getUsername(), source)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(user.getUsername())))
                .flatMapSingle(existingUser -> enhanceUserWithGroupRoles(existingUser, user))
                .flatMap(existingUser -> {
                    LOGGER.debug("Updating user: username[%s]", user.getUsername());
                    // set external id
                    existingUser.setExternalId(user.getId());
                    existingUser.setLoggedAt(new Date());
                    existingUser.setLoginsCount(existingUser.getLoginsCount() + 1);
                    // set roles
                    if (existingUser.getRoles() == null) {
                        existingUser.setRoles(user.getRoles());
                    } else if (user.getRoles() != null) {
                        existingUser.getRoles().addAll(user.getRoles());
                    }
                    Map<String, Object> additionalInformation = user.getAdditionalInformation();
                    extractAdditionalInformation(existingUser, additionalInformation);
                    return userRepository.update(existingUser);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof UserNotFoundException) {
                        LOGGER.debug("Creating a new user: username[%s]", user.getUsername());
                        final User newUser = new User();
                        // set external id
                        newUser.setExternalId(user.getId());
                        newUser.setUsername(user.getUsername());
                        newUser.setDomain(domain);
                        newUser.setCreatedAt(new Date());
                        newUser.setLoggedAt(new Date());
                        newUser.setLoginsCount(1L);
                        newUser.setRoles(user.getRoles());

                        Map<String, Object> additionalInformation = user.getAdditionalInformation();
                        extractAdditionalInformation(newUser, additionalInformation);
                        return userRepository.create(newUser);
                    }
                    return Single.error(ex);
                });

    }

    private Single<User> enhanceUserWithGroupRoles(User user, io.gravitee.am.identityprovider.api.User idpUser) {
        if (idpUser.getAdditionalInformation() != null && idpUser.getAdditionalInformation().containsKey(GROUP_MAPPING_ATTRIBUTE)) {
            Map<String, List<String>> groupMapping = (Map<String, List<String>>) idpUser.getAdditionalInformation().get(GROUP_MAPPING_ATTRIBUTE);
            // for each group if current user is member of one of these groups add corresponding role to the user
            return Observable.fromIterable(groupMapping.entrySet())
                    .flatMapSingle(entry -> groupRepository.findByIdIn(entry.getValue())
                            .map(groups -> groups
                                    .stream()
                                    .filter(group -> group.getMembers().contains(user.getId()))
                                    .findFirst())
                            .map(optionalGroup -> optionalGroup.isPresent() ? Optional.of(entry.getKey()) : Optional.<String>empty()))
                    .toList()
                    .map(optionals -> {
                        List<String> roles = optionals.stream().filter(Optional::isPresent).map(opt -> opt.get()).collect(Collectors.toList());
                        user.setRoles(roles);
                        return user;
                    });
        } else {
            return Single.just(user);
        }
    }

    private void extractAdditionalInformation(User user, Map<String, Object> additionalInformation) {
        if (additionalInformation != null) {
            Map<String, Object> extraInformation = new HashMap<>(additionalInformation);
            user.setSource((String) extraInformation.remove(SOURCE_FIELD));
            user.setClient((String) extraInformation.remove(Parameters.CLIENT_ID.value()));
            extraInformation.remove(GROUP_MAPPING_ATTRIBUTE);
            user.setAdditionalInformation(extraInformation);
        }
    }
}

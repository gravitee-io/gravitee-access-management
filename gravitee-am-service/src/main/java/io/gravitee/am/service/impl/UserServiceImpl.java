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

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.User;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

    @Lazy
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private EventService eventService;

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
    public Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        LOGGER.debug("Find users by {}: {}", referenceType, referenceId);
        return userRepository.findAll(referenceType, referenceId, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by {} {}", referenceType, referenceId, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by %s %s", referenceType, referenceId), ex));
                });
    }

    @Override
    public Single<Page<User>> findByDomain(String domain, int page, int size) {
        return findAll(ReferenceType.DOMAIN, domain, page, size);
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size) {
        LOGGER.debug("Search users for {} {} with query {}", referenceType, referenceId, query);
        return userRepository.search(referenceType, referenceId, query, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to search users for {} {} and query {}", referenceType, referenceId, query, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users for %s %s and query %s", referenceType, referenceId, query), ex));
                });
    }

    @Override
    public Single<Page<User>> search(String domain, String query, int page, int size) {
        return search(ReferenceType.DOMAIN, domain, query, page, size);
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
    public Single<List<User>> findByDomainAndEmail(String domain, String email, boolean strict) {
        LOGGER.debug("Find users by domain : {} and email: {}", domain, email);
        return userRepository.findByDomainAndEmail(domain, email, strict)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by domain : {} and email : {} ", domain, email, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by domain %s and email %s", domain, email), ex));
                });
    }


    @Override
    public Single<User> findById(ReferenceType referenceType, String referenceId, String id) {

        LOGGER.debug("Find user by id : {}", id);
        return userRepository.findById(referenceType, referenceId, id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s", id), ex));
                })
                .switchIfEmpty(Single.error(new UserNotFoundException(id)));
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
    public Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source) {
        LOGGER.debug("Find user by {} {}, username and source: {} {}", referenceType, referenceId, username, source);
        return userRepository.findByUsernameAndSource(referenceType, referenceId, username, source)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its username: {} for the {} {}  and source {}", username, referenceType, referenceId, source, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its username: %s for the %s %s and source %s", username, referenceType, referenceId, source), ex));
                });
    }

    @Override
    public Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source) {
        return findByUsernameAndSource(ReferenceType.DOMAIN, domain, username, source);
    }

    @Override
    public Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source) {
        LOGGER.debug("Find user by {} {}, externalId and source: {} {}", referenceType, referenceId, externalId, source);
        return userRepository.findByExternalIdAndSource(referenceType, referenceId, externalId, source)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its externalId: {} for the {} and source {}", externalId, referenceType, referenceId, source, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its externalId: %s for the %s %s and source %s", externalId, referenceType, referenceId, source), ex));
                });
    }

    @Override
    public Single<User> create(String domain, NewUser newUser) {

        return create(ReferenceType.DOMAIN, domain, newUser);
    }

    @Override
    public Single<User> create(ReferenceType referenceType, String referenceId, NewUser newUser) {
        LOGGER.debug("Create a new user {} for {} {}", newUser, referenceType, referenceId);

        return userRepository.findByDomainAndUsernameAndSource(referenceId, newUser.getUsername(), newUser.getSource())
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (!isEmpty) {
                        return Single.error(new UserAlreadyExistsException(newUser.getUsername()));
                    } else {
                        String userId = RandomString.generate();

                        User user = new User();
                        user.setId(userId);
                        user.setExternalId(newUser.getExternalId());
                        user.setReferenceType(referenceType);
                        user.setReferenceId(referenceId);
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
                .flatMap(user -> {
                    // create event for sync process
                    Event event = new Event(Type.USER, new Payload(user.getId(), referenceType == ReferenceType.DOMAIN ? user.getReferenceId() : null, Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(user));
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
    public Single<User> create(User user) {
        LOGGER.debug("Create a user {}", user);
        user.setCreatedAt(new Date());
        user.setUpdatedAt(user.getCreatedAt());
        return userRepository.create(user)
                .flatMap(user1 -> {
                    // create event for sync process
                    Event event = new Event(Type.USER, new Payload(user1.getId(), user1.getReferenceType() == ReferenceType.DOMAIN ? user1.getReferenceId() : null, Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(user1));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a user", ex));
                });
    }

    @Override
    public Single<User> update(ReferenceType referenceType, String referenceId, String id, UpdateUser updateUser) {
        LOGGER.debug("Update a user {} for {} {}", id, referenceType, referenceId);

        return userRepository.findById(referenceType, referenceId, id)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(id)))
                .flatMapSingle(oldUser -> {
                    oldUser.setClient(updateUser.getClient());
                    oldUser.setExternalId(updateUser.getExternalId());
                    oldUser.setFirstName(updateUser.getFirstName());
                    oldUser.setLastName(updateUser.getLastName());
                    oldUser.setEmail(updateUser.getEmail());
                    oldUser.setEnabled(updateUser.isEnabled());
                    oldUser.setLoggedAt(updateUser.getLoggedAt());
                    oldUser.setLoginsCount(updateUser.getLoginsCount());
                    oldUser.setUpdatedAt(new Date());
                    oldUser.setAdditionalInformation(updateUser.getAdditionalInformation());
                    return userRepository.update(oldUser);
                })
                .flatMap(user -> {
                    // create event for sync process
                    Event event = new Event(Type.USER, new Payload(user.getId(), referenceType == ReferenceType.DOMAIN ? user.getReferenceId() : null, Action.UPDATE));
                    return eventService.create(event).flatMap(__ -> Single.just(user));
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
    public Single<User> update(String domain, String id, UpdateUser updateUser) {

        return update(ReferenceType.DOMAIN, domain, id, updateUser);
    }

    @Override
    public Single<User> update(User user) {
        LOGGER.debug("Update a user {}", user);
        // updated date
        user.setUpdatedAt(new Date());
        return userRepository.update(user)
                .flatMap(user1 -> {
                    // create event for sync process
                    Event event = new Event(Type.USER, new Payload(user1.getId(), user1.getReferenceType() == ReferenceType.DOMAIN ? user1.getReferenceId() : null, Action.UPDATE));
                    return eventService.create(event).flatMap(__ -> Single.just(user1));
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
    public Single<User> enhance(User user) {
        LOGGER.debug("Enhance user {} with groups and roles", user.getId());

        // fetch user groups
        return groupService.findByMember(user.getId())
                .flatMap(groups -> {
                    Set<String> roles = new HashSet<>();
                    // get groups roles
                    if (!groups.isEmpty()) {
                        roles.addAll(groups
                                .stream()
                                .filter(group -> group.getRoles() != null && !group.getRoles().isEmpty())
                                .flatMap(group -> group.getRoles().stream())
                                .collect(Collectors.toSet()));
                    }
                    // get user roles
                    if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                        roles.addAll(user.getRoles());
                    }
                    // fetch roles information and enhance user data
                    if (!roles.isEmpty()) {
                        return roleService.findByIdIn(new ArrayList<>(roles))
                                // update role permission for role with scope
                                .map(roles1 -> {
                                    return roles1.stream()
                                            .map(role -> {
                                                if (role.getScope() != null) {
                                                    role.getPermissions().forEach(p -> p = RoleScope.valueOf(role.getScope()).name() + "_" + p);
                                                }
                                                return role;
                                            }).collect(Collectors.toSet());
                                })
                                .map(roles1 -> {
                                    user.setRolesPermissions(roles1);
                                    return user;
                                });

                    }
                    return Single.just(user);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to enhance user {}", user.getId(), ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to enhance user %s", user.getId()), ex));
                });
    }

    @Override
    public Completable delete(String userId) {
        LOGGER.debug("Delete user {}", userId);

        return userRepository.findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapCompletable(user -> {
                    // create event for sync process
                    Event event = new Event(Type.USER, new Payload(user.getId(), user.getReferenceType() == ReferenceType.DOMAIN ? user.getReferenceId() : null, Action.DELETE));
                    return userRepository.delete(userId).andThen(eventService.create(event)).toCompletable();
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete user: {}", userId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete user: %s", userId), ex));
                });
    }

    @Override
    public Single<Long> countByDomain(String domain) {
        LOGGER.debug("Count user by domain {}", domain);

        return userRepository.countByDomain(domain)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to count users by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while count users to delete user: %s", domain), ex));
                });
    }

    @Override
    public Single<Map<Object, Object>> statistics(AnalyticsQuery query) {
        LOGGER.debug("Get user collection analytics {}", query);

        return userRepository.statistics(query)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to get users analytics : {}", query, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while count users analytics : %s", query), ex));
                });
    }
}

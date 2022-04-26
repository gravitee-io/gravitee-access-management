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
import io.gravitee.am.model.Group;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.CommonUserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.utils.UserFactorUpdater;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractUserService<T extends CommonUserRepository> implements CommonUserService {

    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected UserValidator userValidator;

    @Autowired
    protected EventService eventService;

    @Autowired
    protected CredentialService credentialService;

    @Autowired
    protected RoleService roleService;

    @Autowired
    protected GroupService groupService;

    protected abstract T getUserRepository();

    @Override
    public Flowable<User> findByIdIn(List<String> ids) {
        String userIds = String.join(",", ids);
        LOGGER.debug("Find users by ids: {}", userIds);
        return getUserRepository().findByIdIn(ids)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by ids {}", userIds, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by ids %s", userIds), ex));
                });
    }

    @Override
    public Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        LOGGER.debug("Find users by {}: {}", referenceType, referenceId);
        return getUserRepository().findAll(referenceType, referenceId, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by {} {}", referenceType, referenceId, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by %s %s", referenceType, referenceId), ex));
                });
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size) {
        LOGGER.debug("Search users for {} {} with query {}", referenceType, referenceId, query);
        return getUserRepository().search(referenceType, referenceId, query, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to search users for {} {} and query {}", referenceType, referenceId, query, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users for %s %s and query %s", referenceType, referenceId, query), ex));
                });
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria filterCriteria, int page, int size) {
        LOGGER.debug("Search users for {} {} with filter {}", referenceType, referenceId, filterCriteria);
        return getUserRepository().search(referenceType, referenceId, filterCriteria, page, size)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof IllegalArgumentException) {
                        return Single.error(new InvalidParameterException(ex.getMessage()));
                    }
                    LOGGER.error("An error occurs while trying to search users for {} {} and filter {}", referenceType, referenceId, filterCriteria, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users for %s %s and filter %s", referenceType, referenceId, filterCriteria), ex));
                });
    }

    @Override
    public Single<User> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("Find user by id : {}", id);
        return getUserRepository().findById(referenceType, referenceId, id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s", id), ex));
                })
                .switchIfEmpty(Single.error(new UserNotFoundException(id)));
    }

    @Override
    public Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source) {
        LOGGER.debug("Find user by {} {}, username and source: {} {}", referenceType, referenceId, username, source);
        return getUserRepository().findByUsernameAndSource(referenceType, referenceId, username, source)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its username: {} for the {} {}  and source {}", username, referenceType, referenceId, source, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its username: %s for the %s %s and source %s", username, referenceType, referenceId, source), ex));
                });
    }

    @Override
    public Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source) {
        LOGGER.debug("Find user by {} {}, externalId and source: {} {}", referenceType, referenceId, externalId, source);
        return getUserRepository().findByExternalIdAndSource(referenceType, referenceId, externalId, source)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its externalId: {} for the {} {} and source {}", externalId, referenceType, referenceId, source, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its externalId: %s for the %s %s and source %s", externalId, referenceType, referenceId, source), ex));
                });
    }


    @Override
    public Single<User> create(ReferenceType referenceType, String referenceId, NewUser newUser) {
        LOGGER.debug("Create a new user {} for {} {}", newUser, referenceType, referenceId);
        return getUserRepository().findByUsernameAndSource(referenceType, referenceId, newUser.getUsername(), newUser.getSource())
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
                        user.setPreferredLanguage(newUser.getPreferredLanguage());
                        user.setAdditionalInformation(newUser.getAdditionalInformation());
                        user.setCreatedAt(new Date());
                        user.setUpdatedAt(user.getCreatedAt());
                        return create(user);
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
    public Single<User> create(User user) {

        LOGGER.debug("Create a user {}", user);
        user.setCreatedAt(new Date());
        user.setUpdatedAt(user.getCreatedAt());

        return userValidator.validate(user)
                .andThen(getUserRepository().create(user))
                .flatMap(user1 -> {
                    // create event for sync process
                    Event event = new Event(Type.USER, new Payload(user1.getId(), user1.getReferenceType(), user1.getReferenceId(), Action.CREATE));
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

        return getUserRepository().findById(referenceType, referenceId, id)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(id)))
                .flatMapSingle(oldUser -> {
                    User tmpUser = new User();
                    tmpUser.setEmail(updateUser.getEmail());
                    tmpUser.setAdditionalInformation(updateUser.getAdditionalInformation());
                    UserFactorUpdater.updateFactors(oldUser.getFactors(), oldUser, tmpUser);

                    oldUser.setClient(updateUser.getClient());
                    oldUser.setExternalId(updateUser.getExternalId());
                    oldUser.setFirstName(updateUser.getFirstName());
                    oldUser.setLastName(updateUser.getLastName());
                    oldUser.setDisplayName(updateUser.getDisplayName());
                    oldUser.setEmail(updateUser.getEmail());
                    oldUser.setEnabled(updateUser.isEnabled());
                    oldUser.setLoggedAt(updateUser.getLoggedAt());
                    oldUser.setLoginsCount(updateUser.getLoginsCount());
                    if (!StringUtils.isEmpty(updateUser.getPreferredLanguage())) {
                        oldUser.setPreferredLanguage(updateUser.getPreferredLanguage());
                    }
                    oldUser.setUpdatedAt(new Date());
                    oldUser.setAdditionalInformation(updateUser.getAdditionalInformation());

                    return update(oldUser);
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

        return getUserRepository().findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapCompletable(user -> {
                    // create event for sync process
                    Event event = new Event(Type.USER, new Payload(user.getId(), user.getReferenceType(), user.getReferenceId(), Action.DELETE));
                    /// delete WebAuthn credentials
                    return credentialService.findByUserId(user.getReferenceType(), user.getReferenceId(), user.getId())
                            .flatMapCompletable(credential -> credentialService.delete(credential.getId(), false))
                            .andThen(getUserRepository().delete(userId))
                            .andThen(eventService.create(event).ignoreElement());
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
    public Single<User> enhance(User user) {
        LOGGER.debug("Enhance user {}", user.getId());

        // fetch user groups
        return groupService.findByMember(user.getId())
                .toList()
                .flatMap(groups -> {
                    Set<String> roles = new HashSet<>();
                    if (groups != null && !groups.isEmpty()) {
                        // set groups
                        user.setGroups(groups.stream().map(Group::getName).collect(Collectors.toList()));
                        // set groups roles
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
                    if (user.getDynamicRoles() != null && !user.getDynamicRoles().isEmpty()) {
                        roles.addAll(user.getDynamicRoles());
                    }
                    // fetch roles information and enhance user data
                    if (!roles.isEmpty()) {
                        return roleService.findByIdIn(new ArrayList<>(roles)).map(foundRoles -> {
                            user.setRolesPermissions(foundRoles);
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

}

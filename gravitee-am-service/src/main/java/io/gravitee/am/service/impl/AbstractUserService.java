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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.CommonUserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CommonUserService;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.impl.user.UserEnhancer;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.utils.UserFactorUpdater;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.service.utils.UserProfileUtils.buildDisplayName;
import static io.gravitee.am.service.utils.UserProfileUtils.hasGeneratedDisplayName;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractUserService<T extends CommonUserRepository> implements CommonUserService {

    private static final String CREATE_USER_ERROR = "An error occurs while trying to create a user";
    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected UserValidator userValidator;

    @Autowired
    private AuditService auditService;

    @Autowired
    protected CredentialService credentialService;

    @Autowired
    protected RoleService roleService;

    @Autowired
    protected GroupService groupService;

    protected abstract T getUserRepository();

    @Override
    public Flowable<User> findByIdIn(ReferenceType referenceType, String referenceId, List<String> ids) {
        String userIds = String.join(",", ids);
        LOGGER.debug("Find users by ids: {}", userIds);
        return getUserRepository().findByIdIn(referenceType, referenceId, ids)
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
    public Flowable<User> search(ReferenceType referenceType, String referenceId, FilterCriteria filterCriteria) {
        LOGGER.debug("Search users for {} {} with filter {}", referenceType, referenceId, filterCriteria);
        return getUserRepository().search(referenceType, referenceId, filterCriteria)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof IllegalArgumentException) {
                        return Flowable.error(new InvalidParameterException(ex.getMessage()));
                    }
                    LOGGER.error("An error occurs while trying to search users for {} {} and filter {}", referenceType, referenceId, filterCriteria, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find users for %s %s and filter %s", referenceType, referenceId, filterCriteria), ex));
                });
    }

    @Override
    public Single<User> findById(ReferenceType referenceType, String referenceId, String id) {
        return findById(new Reference(referenceType, referenceId), UserId.internal(id));
    }

    @Override
    public Single<User> findById(Reference reference, UserId userId) {
        LOGGER.debug("Find user by id : {}", userId);
        return getUserRepository().findById(reference, userId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID {}", userId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s", userId), ex));
                })
                .switchIfEmpty(Single.error(new UserNotFoundException(userId)));

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
                    if (Boolean.FALSE.equals(isEmpty)) {
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
                            user.setDisplayName(buildDisplayName(user));
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
                        return create(user)
                                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED).user(user1)))
                                .doOnError(err -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED).reference(new Reference(referenceType, referenceId)).throwable(err)));
                    }
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    } else {
                        LOGGER.error(CREATE_USER_ERROR, ex);
                        return Single.error(new TechnicalManagementException(CREATE_USER_ERROR, ex));
                    }
                });
    }

    @Override
    public Single<User> create(User user) {
        LOGGER.debug("Create a user {}", user);
        if (StringUtils.isBlank(user.getUsername())) {
            return Single.error(() -> new UserInvalidException("Field [username] is required"));
        }
        user.setCreatedAt(new Date());
        user.setUpdatedAt(user.getCreatedAt());
        return userValidator.validate(user)
                .andThen(getUserRepository().create(user))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error(CREATE_USER_ERROR, ex);
                    return Single.error(new TechnicalManagementException(CREATE_USER_ERROR, ex));
                });
    }

    @Override
    public Single<User> update(ReferenceType referenceType, String referenceId, String id, UpdateUser updateUser) {
        LOGGER.debug("Update a user {} for {} {}", id, referenceType, referenceId);

        return getUserRepository().findById(new Reference(referenceType, referenceId), UserId.internal(id))
                .switchIfEmpty(Single.error(new UserNotFoundException(id)))
                .flatMap(oldUser -> {
                    User tmpUser = new User();
                    tmpUser.setEmail(updateUser.getEmail());
                    tmpUser.setAdditionalInformation(updateUser.getAdditionalInformation());
                    UserFactorUpdater.updateFactors(oldUser.getFactors(), oldUser, tmpUser);

                    final boolean generatedDisplayName = hasGeneratedDisplayName(oldUser);

                    oldUser.setClient(updateUser.getClient());
                    oldUser.setExternalId(updateUser.getExternalId());
                    oldUser.setFirstName(updateUser.getFirstName());
                    oldUser.setLastName(updateUser.getLastName());
                    if (generatedDisplayName && !isNullOrEmpty(updateUser.getFirstName()) && Objects.equals(updateUser.getDisplayName(), oldUser.getDisplayName())) {
                        oldUser.setDisplayName(buildDisplayName(oldUser));
                    } else {
                        oldUser.setDisplayName(updateUser.getDisplayName());
                    }
                    oldUser.setEmail(updateUser.getEmail());
                    oldUser.setEnabled(updateUser.isEnabled());
                    if (!StringUtils.isEmpty(updateUser.getPreferredLanguage())) {
                        oldUser.setPreferredLanguage(updateUser.getPreferredLanguage());
                    }
                    oldUser.setUpdatedAt(new Date());
                    oldUser.setAdditionalInformation(updateUser.getAdditionalInformation());
                    oldUser.setForceResetPassword(updateUser.getForceResetPassword());

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
    public Single<User> delete(String userId) {
        LOGGER.debug("Delete user {}", userId);

        return getUserRepository().findById(userId)
                .switchIfEmpty(Single.error(new UserNotFoundException(userId)))
                .flatMap(user -> credentialService.findByUserId(user.getReferenceType(), user.getReferenceId(), user.getId())
                            .flatMapCompletable(credential -> credentialService.delete(credential.getId(), false))
                            .andThen(getUserRepository().delete(userId))
                            .toSingleDefault(user))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete user: {}", userId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete user: %s", userId), ex));
                });
    }
}

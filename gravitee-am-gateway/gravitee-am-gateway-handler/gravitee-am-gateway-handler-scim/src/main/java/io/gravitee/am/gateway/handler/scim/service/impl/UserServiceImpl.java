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
package io.gravitee.am.gateway.handler.scim.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.scim.filter.Filter;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.exception.SCIMException;
import io.gravitee.am.gateway.handler.scim.exception.UniquenessException;
import io.gravitee.am.gateway.handler.scim.mapper.UserMapper;
import io.gravitee.am.gateway.handler.scim.model.GraviteeUser;
import io.gravitee.am.gateway.handler.scim.model.ListResponse;
import io.gravitee.am.gateway.handler.scim.model.Member;
import io.gravitee.am.gateway.handler.scim.model.PatchOp;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.RateLimiterService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.am.service.VerifyAttemptService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AbstractNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.utils.UserFactorUpdater;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.text.MessageFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.repository.management.api.CommonUserRepository.UpdateActions;
import static java.lang.Boolean.FALSE;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserServiceImpl implements UserService {
    private static final String PARAMETER_EXIST_ERROR = "User with {0} [{1}] already exists";
    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final String DEFAULT_IDP_PREFIX = "default-idp-";
    public static final String FIELD_PASSWORD_IS_INVALID = "Field [password] is invalid";

    private static final Set<String> SCIM_DECLARED_CLAIMS = Set.of(StandardClaims.SUB,
            StandardClaims.GIVEN_NAME,
            StandardClaims.FAMILY_NAME,
            StandardClaims.MIDDLE_NAME,
            StandardClaims.PROFILE,
            StandardClaims.PICTURE,
            StandardClaims.ZONEINFO,
            StandardClaims.LOCALE);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupService groupService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private Domain domain;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private UserValidator userValidator;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private PasswordHistoryService passwordHistoryService;

    @Autowired
    private VerifyAttemptService verifyAttemptService;

    @Override
    public Single<ListResponse<User>> list(Filter filter, int page, int size, String baseUrl) {
        LOGGER.debug("Find users by domain: {}", domain.getId());
        Single<Page<io.gravitee.am.model.User>> findUsers = filter != null ?
                userRepository.search(ReferenceType.DOMAIN, domain.getId(), FilterCriteria.convert(filter), page, size) :
                userRepository.findAll(ReferenceType.DOMAIN, domain.getId(), page, size);

        return findUsers
                .flatMap(userPage -> {
                    // A negative value SHALL be interpreted as "0".
                    // A value of "0" indicates that no resource results are to be returned except for "totalResults".
                    if (size <= 0) {
                        return Single.just(new ListResponse<User>(null, userPage.getCurrentPage() + 1, userPage.getTotalCount(), 0));
                    } else {
                        // SCIM use 1-based index (increment current page)
                        return Observable.fromIterable(userPage.getData())
                                .map(user1 -> UserMapper.convert(user1, baseUrl, true))
                                // set groups
                                .flatMapSingle(this::setGroups)
                                .toList()
                                .map(users -> new ListResponse<>(users, userPage.getCurrentPage() + 1, userPage.getTotalCount(), users.size()));
                    }
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users for the security domain {}", domain.getName(), ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users the security domain %s", domain.getName()), ex));
                });
    }

    @Override
    public Maybe<User> get(String userId, String baseUrl) {
        LOGGER.debug("Find user by id : {}", userId);
        return innerGet(userId, baseUrl).map(UserContainer::getScimUser);
    }

    public Maybe<UserContainer> innerGet(String userId, String baseUrl) {
        LOGGER.debug("Find user by id : {}", userId);
        return userRepository.findById(userId)
                .map(user1 -> new UserContainer(UserMapper.convert(user1, baseUrl, false), user1))
                .flatMap(containerUser -> setGroups(containerUser.getScimUser()).map(containerUser::replaceScimUserWith).toMaybe())
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID {}", userId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s", userId), ex));
                });
    }

    @Override
    public Single<User> create(User user, String idp, String baseUrl, io.gravitee.am.identityprovider.api.User principal, Client client) {
        LOGGER.debug("Create a new user {} for domain {}", user.getUserName(), domain.getName());

        // set user idp source
        final String source = user.getSource() != null ? user.getSource() : (idp != null ? idp : DEFAULT_IDP_PREFIX + domain.getId());


        io.gravitee.am.model.User userModel = UserMapper.convert(user);
        // set technical ID
        userModel.setId(RandomString.generate());
        userModel.setReferenceType(ReferenceType.DOMAIN);
        userModel.setReferenceId(domain.getId());
        userModel.setSource(source);
        userModel.setInternal(true);
        userModel.setCreatedAt(new Date());
        userModel.setUpdatedAt(userModel.getCreatedAt());
        userModel.setEnabled(userModel.getPassword() != null);

        // check password
        if (isInvalidUserPassword(user.getPassword(), userModel)) {
            return Single.error(new InvalidValueException(FIELD_PASSWORD_IS_INVALID));
        }

        final var rawPassword = user.getPassword();

        // check if user is unique
        return Single.zip(
                userRepository.findByUsernameAndSource(ReferenceType.DOMAIN, domain.getId(), user.getUserName(), source).isEmpty(),
                userRepository.findByExternalIdAndSource(ReferenceType.DOMAIN, domain.getId(), user.getExternalId(), source).isEmpty(),
                (isNoUsername, isNoExternalId) -> {
                    if (FALSE.equals(isNoUsername)) {
                        throw new UniquenessException(MessageFormat.format(PARAMETER_EXIST_ERROR, "username", user.getUserName()));
                    }
                    if (FALSE.equals(isNoExternalId)) {
                        throw new UniquenessException(MessageFormat.format(PARAMETER_EXIST_ERROR, "externalId", user.getExternalId()));
                    }
                    return true;
                })
                // check roles
                .flatMapCompletable(__ -> checkRoles(user.getRoles()))
                // and create the user
                .andThen(Single.defer(() -> {
                    // store user
                    return userValidator.validate(userModel)
                            .andThen(Single.defer(() -> {
                                        final IdentityProvider identityProvider = identityProviderManager.getIdentityProvider(source);
                                        if (identityProvider == null) {
                                            return Single.error(new IdentityProviderNotFoundException(source));
                                        }
                                        return identityProviderManager.getUserProvider(source)
                                                .switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(source)))
                                                .flatMap(userProvider -> userProvider.create(UserMapper.convert(userModel)));
                                    })
                                    .flatMap(idpUser -> {
                                        // AM 'users' collection is not made for authentication (but only management stuff)
                                        // clear password
                                        userModel.setPassword(null);
                                        // set external id
                                        userModel.setExternalId(idpUser.getId());
                                        return userRepository.create(userModel);
                                    })
                                    .onErrorResumeNext(ex -> {
                                        if (ex instanceof UserProviderNotFoundException) {
                                            // just store in AM
                                            userModel.setPassword(null);
                                            // As there are no UserProvider, the user is an external one
                                            userModel.setInternal(false);
                                            // set external id
                                            userModel.setExternalId(user.getExternalId() != null ? user.getExternalId() : userModel.getId());
                                            return userRepository.create(userModel);
                                        }
                                        if (ex instanceof UserAlreadyExistsException) {
                                            return Single.error(new UniquenessException(MessageFormat.format(PARAMETER_EXIST_ERROR, "username", user.getUserName())));
                                        }
                                        return Single.error(ex);
                                    }))
                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).user(user1)));
                }))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).user(userModel).domain(domain.getId()).throwable(throwable)))
                .map(user1 -> {
                    //noinspection ReactiveStreamsUnusedPublisher
                    createPasswordHistory(domain, user1, rawPassword, principal, client);
                    return UserMapper.convert(user1, baseUrl, true);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractNotFoundException) {
                        return Single.error(new InvalidValueException(ex.getMessage()));
                    }

                    if (ex instanceof SCIMException || ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a user", ex));
                });
    }

    @Override
    public Single<User> update(String userId, User user, String idp, String baseUrl, io.gravitee.am.identityprovider.api.User principal, Client client) {
        LOGGER.debug("Update a user {} for domain {}", user.getUserName(), domain.getName());

        return userRepository.findById(userId)
                .switchIfEmpty(Single.error(() ->new UserNotFoundException(userId)))
                .flatMap(existingUser -> innerUpdate(existingUser, user, idp, baseUrl, principal, client));
    }

    public Single<User> innerUpdate(io.gravitee.am.model.User userIntoDb, User scimUser, String idp, String baseUrl, io.gravitee.am.identityprovider.api.User principal, Client client) {
        LOGGER.debug("Update a user {} for domain {}", scimUser.getUserName(), domain.getName());

        final var rawPassword = scimUser.getPassword();
        return Single.just(userIntoDb).flatMap( existingUser -> {
                    // check roles
                    return checkRoles(scimUser.getRoles())
                            // and update the user
                            .andThen(Single.defer(() -> {
                                io.gravitee.am.model.User userToUpdate = UserMapper.convert(scimUser);
                                // set immutable attribute
                                userToUpdate.setId(existingUser.getId());
                                userToUpdate.setExternalId(existingUser.getExternalId());
                                userToUpdate.setUsername(existingUser.getUsername());
                                userToUpdate.setReferenceType(existingUser.getReferenceType());
                                userToUpdate.setReferenceId(existingUser.getReferenceId());
                                userToUpdate.setCreatedAt(existingUser.getCreatedAt());
                                userToUpdate.setUpdatedAt(new Date());
                                userToUpdate.setFactors(existingUser.getFactors());
                                userToUpdate.setDynamicRoles(existingUser.getDynamicRoles());
                                if (Objects.nonNull(existingUser.getAdditionalInformation())) {
                                    // retrieve additionalInformation from the existing user.
                                    // as SCIM doesn't define additionalInformation attributes, we have to
                                    // copy them to avoid data loss
                                    existingUser.getAdditionalInformation().forEach((k,v) -> {
                                        if (!SCIM_DECLARED_CLAIMS.contains(k)) {
                                            // some claims are defined by SCIM
                                            // we do not want to copy them
                                            // as they may be explicitly removed by the user
                                            userToUpdate.getAdditionalInformation().putIfAbsent(k,v);
                                        }
                                    });
                                }
                                // keep previous login attempts information
                                userToUpdate.setLoggedAt(existingUser.getLoggedAt());
                                userToUpdate.setLoginsCount(existingUser.getLoginsCount());
                                if (isNullOrEmpty(userToUpdate.getPassword())) {
                                    // if password is missing, do not unlock the account
                                    userToUpdate.setAccountLockedAt(existingUser.getAccountLockedAt());
                                    userToUpdate.setAccountLockedUntil(existingUser.getAccountLockedUntil());
                                    userToUpdate.setAccountNonLocked(existingUser.isAccountNonLocked());
                                }

                                // We remove the dynamic roles from the user roles to be updated in order to preserve
                                // the roles that were assigned by the RoleMappers so that whenever the rule from the
                                // said RoleMapper does not apply anymore, user loses the role.
                                // If roles existed in the existing user roles, it means it has been assigned manually
                                // we don't want them to be in dynamic roles until the existing static role is removed
                                if (userToUpdate.getDynamicRoles() != null) {
                                    var existingStaticRoles = ofNullable(existingUser.getRoles()).orElse(new ArrayList<>());
                                    var toUpdateStaticRoles = ofNullable(userToUpdate.getRoles()).orElse(new ArrayList<>());
                                    // create a workingCopy of DynamicRoles to avoid altering the one coming from the existing user
                                    var workingCopyDynamicRoles = new ArrayList<>(userToUpdate.getDynamicRoles());
                                    workingCopyDynamicRoles.removeAll(existingStaticRoles);
                                    toUpdateStaticRoles.removeAll(workingCopyDynamicRoles);
                                }

                                UserFactorUpdater.updateFactors(existingUser.getFactors(), existingUser, userToUpdate);

                                // check password
                                if (isInvalidUserPassword(userToUpdate.getPassword(), userToUpdate)) {
                                    return Single.error(new InvalidValueException(FIELD_PASSWORD_IS_INVALID));
                                }

                                // set source
                                String source = scimUser.getSource() != null ? scimUser.getSource() : (idp != null ? idp : existingUser.getSource());
                                userToUpdate.setSource(source);

                                return userValidator.validate(userToUpdate)
                                        .andThen(Single.defer(() -> {
                                                    final IdentityProvider identityProvider = identityProviderManager.getIdentityProvider(source);
                                                    if (identityProvider == null) {
                                                        return Single.error(new IdentityProviderNotFoundException(source));
                                                    }
                                                    return identityProviderManager.getUserProvider(userToUpdate.getSource())
                                                            .switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(userToUpdate.getSource())))
                                                            .flatMap(userProvider -> {
                                                                // no idp user check if we need to create it
                                                                if (userToUpdate.getExternalId() == null) {
                                                                    return userProvider.create(UserMapper.convert(userToUpdate));
                                                                } else {
                                                                    return createPasswordHistory(domain, userToUpdate, rawPassword, principal, client)
                                                                            .switchIfEmpty(Single.just(new PasswordHistory()))
                                                                            .flatMap(ph -> userProvider.update(userToUpdate.getExternalId(), UserMapper.convert(userToUpdate)));
                                                                }
                                                            });
                                                })
                                                .flatMap(idpUser -> {
                                                    // AM 'users' collection is not made for authentication (but only management stuff)
                                                    // clear password
                                                    userToUpdate.setPassword(null);
                                                    // set external id
                                                    userToUpdate.setExternalId(idpUser.getId());
                                                    // if password has been changed, update last update date
                                                    if (scimUser.getPassword() != null) {
                                                        userToUpdate.setLastPasswordReset(new Date());
                                                    }

                                                    return userRepository.update(userToUpdate, UpdateActions.build(existingUser, userToUpdate));
                                                })
                                                .onErrorResumeNext(ex -> {
                                                    if (ex instanceof UserNotFoundException ||
                                                            ex instanceof UserInvalidException ||
                                                            ex instanceof UserProviderNotFoundException) {
                                                        // idp user does not exist, only update AM user
                                                        // clear password
                                                        userToUpdate.setPassword(null);
                                                        return userRepository.update(userToUpdate, UpdateActions.build(existingUser, userToUpdate));
                                                    }
                                                    return Single.error(ex);
                                                })
                                                .doOnSuccess(updatedUser -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).oldValue(existingUser).type(EventType.USER_UPDATED).user(updatedUser)))
                                                .doOnError(error -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).user(existingUser).type(EventType.USER_UPDATED).throwable(error))));
                            }));
                })
                .map(user1 -> UserMapper.convert(user1, baseUrl, false))
                // set groups
                .flatMap(this::setGroups)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof SCIMException || ex instanceof UserNotFoundException) {
                        return Single.error(ex);
                    }

                    if (ex instanceof AbstractNotFoundException) {
                        return Single.error(new InvalidValueException(ex.getMessage()));
                    }

                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a user", ex));
                });
    }

    @Override
    public Single<User> patch(String userId, PatchOp patchOp, String idp, String baseUrl, io.gravitee.am.identityprovider.api.User principal, Client client) {
        LOGGER.debug("Patch user {}", userId);
        return innerGet(userId, baseUrl)
                .switchIfEmpty(Single.error(() -> new UserNotFoundException(userId)))
                .flatMap(userContainer -> {
                    ObjectNode node = objectMapper.convertValue(userContainer.getScimUser(), ObjectNode.class);
                    patchOp.getOperations().forEach(operation -> operation.apply(node));
                    boolean isCustomGraviteeUser = GraviteeUser.SCHEMAS.stream().anyMatch(node::has);
                    User userToPatch = isCustomGraviteeUser ?
                            objectMapper.treeToValue(node, GraviteeUser.class) :
                            objectMapper.treeToValue(node, User.class);

                    // check password
                    if (isInvalidUserPassword(userToPatch.getPassword(), UserMapper.convert(userToPatch))) {
                        return Single.error(new InvalidValueException(FIELD_PASSWORD_IS_INVALID));
                    }

                    return innerUpdate(userContainer.getAmUser(), userToPatch, idp, baseUrl, principal, client);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    } else {
                        LOGGER.error("An error has occurred when trying to patch user: {}", userId, ex);
                        return Single.error(new TechnicalManagementException(
                                String.format("An error has occurred when trying to patch user: %s", userId), ex));
                    }
                });
    }

    @Override
    public Completable delete(String userId, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Delete user {}", userId);
        return userRepository.findById(userId)
                .switchIfEmpty(Maybe.error(() -> new UserNotFoundException(userId)))
                .flatMapCompletable(user -> {
                    return identityProviderManager.getUserProvider(user.getSource())
                            .switchIfEmpty(Maybe.error(() -> new UserProviderNotFoundException(user.getSource())))
                            .flatMapCompletable(userProvider -> userProvider.delete(user.getExternalId()))
                            .onErrorResumeNext(ex -> {
                                if (ex instanceof UserNotFoundException || ex instanceof UserProviderNotFoundException) {
                                    // idp user does not exist, only remove AM user
                                    return Completable.complete();
                                } else if (ex instanceof AbstractManagementException) {
                                    return Completable.error(ex);
                                } else {
                                    LOGGER.error("An error has occurred when trying to delete user: {}", userId, ex);
                                    return Completable.error(new TechnicalManagementException(
                                            String.format("An error has occurred when trying to delete user: %s", userId), ex));
                                }
                            })
                            .andThen(userActivityService.deleteByDomainAndUser(domain.getId(), userId))
                            .andThen(rateLimiterService.deleteByUser(user))
                            .andThen(passwordHistoryService.deleteByUser(userId))
                            .andThen(verifyAttemptService.deleteByUser(user))
                            .andThen(userRepository.delete(userId))
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).domain(domain.getId()).type(EventType.USER_DELETED).user(user)))
                            .doOnError(error -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).domain(domain.getId()).type(EventType.USER_DELETED).throwable(error)));
                });
    }

    private boolean isInvalidUserPassword(String password, io.gravitee.am.model.User user) {
        if (isNull(password)) {
            return false;
        }
        return !passwordService.isValid(password, domain.getPasswordSettings(), user);
    }

    private Single<User> setGroups(User scimUser) {
        // fetch groups
        return groupService.findByMember(scimUser.getId())
                .map(group -> {
                    Member member = new Member();
                    member.setValue(group.getId());
                    member.setDisplay(group.getDisplayName());
                    return member;
                }).toList()
                .map(scimGroups -> {
                    if (!scimGroups.isEmpty()) {
                        scimUser.setGroups(scimGroups);
                        return scimUser;
                    } else {
                        return scimUser;
                    }
                });
    }

    private Completable checkRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Completable.complete();
        }

        return roleService.findByIdIn(roles)
                .map(roles1 -> {
                    if (roles1.size() != roles.size()) {
                        // find difference between the two list
                        roles.removeAll(roles1.stream().map(Role::getId).collect(Collectors.toList()));
                        throw new RoleNotFoundException(String.join(",", roles));
                    }
                    return roles1;
                }).ignoreElement();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Maybe<PasswordHistory> createPasswordHistory(Domain domain, io.gravitee.am.model.User user, String rawPassword, io.gravitee.am.identityprovider.api.User principal, Client client) {
        return passwordHistoryService
                .addPasswordToHistory(DOMAIN, domain.getId(), user, rawPassword , principal, PasswordSettings.getInstance(client, domain).orElse(null));
    }

    private static class UserContainer {
        private User scimUser;
        private io.gravitee.am.model.User amUser;

        public UserContainer(User scimUser, io.gravitee.am.model.User amUser) {
            this.scimUser = scimUser;
            this.amUser = amUser;
        }

        public User getScimUser() {
            return scimUser;
        }

        public io.gravitee.am.model.User getAmUser() {
            return amUser;
        }

        public UserContainer replaceScimUserWith(User scimUser) {
            this.scimUser = scimUser;
            return this;
        }
    }
}

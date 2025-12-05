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
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.repository.management.api.AccountAccessTokenRepository;
import io.gravitee.am.repository.management.api.OrganizationUserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.TooManyAccountTokenException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.model.NewAccountAccessToken;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.utils.UserFactorUpdater;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.model.ReferenceType.ORGANIZATION;
import static io.gravitee.am.service.utils.UserProfileUtils.buildDisplayName;
import static io.gravitee.am.service.utils.UserProfileUtils.hasGeneratedDisplayName;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Slf4j
public class OrganizationUserServiceImpl implements OrganizationUserService {
    private static final String CREATE_USER_ERROR = "An error occurs while trying to create a user";

    @Lazy
    @Autowired
    private OrganizationUserRepository userRepository;

    @Lazy
    @Autowired
    private AccountAccessTokenRepository accountAccessTokenRepository;

    @Autowired
    protected MembershipService membershipService;

    @Autowired
    private PasswordEncoder accountAccessTokenEncoder;

    @Autowired
    protected UserValidator userValidator;

    @Autowired
    private AuditService auditService;

    @Autowired
    protected RoleService roleService;
    
    @Value("${security.accountAccessTokens.limit:20}")
    private int tokensLimit = 20;

    protected OrganizationUserRepository getUserRepository() {
        return this.userRepository;
    }

    public Completable setRoles(io.gravitee.am.model.User user) {
        return setRoles(null, user);
    }

    public Completable setRoles(io.gravitee.am.identityprovider.api.User principal, io.gravitee.am.model.User user) {

        final Maybe<Role> defaultRoleObs = roleService.findDefaultRole(user.getReferenceId(), DefaultRole.ORGANIZATION_USER, ORGANIZATION);
        Maybe<Role> roleObs = defaultRoleObs;

        var fromRoleMapper = new AtomicBoolean(false);
        if (principal != null && principal.getRoles() != null && !principal.getRoles().isEmpty()) {
            // We allow only one role in AM portal. Get the first (should not append).
            String roleId = principal.getRoles().get(0);
            roleObs = roleService.findById(user.getReferenceType(), user.getReferenceId(), roleId)
                    .map(role -> {
                        fromRoleMapper.set(true);
                        return role;
                    })
                    .toMaybe()
                    .onErrorResumeNext(throwable -> {
                        if (throwable instanceof RoleNotFoundException) {
                            return roleService.findById(ReferenceType.PLATFORM, Platform.DEFAULT, roleId).toMaybe()
                                    .switchIfEmpty(defaultRoleObs)
                                    .onErrorResumeNext(exception -> defaultRoleObs);
                        } else {
                            return defaultRoleObs;
                        }
                    });
        }

        return roleObs.switchIfEmpty(Maybe.error(new TechnicalManagementException(String.format("Cannot add user membership to organization %s. Unable to find ORGANIZATION_USER role", user.getReferenceId()))))
                .flatMapCompletable(role -> {
                    Membership membership = new Membership();
                    membership.setMemberType(MemberType.USER);
                    membership.setMemberId(user.getId());
                    membership.setReferenceType(user.getReferenceType());
                    membership.setReferenceId(user.getReferenceId());
                    membership.setRoleId(role.getId());
                    membership.setFromRoleMapper(fromRoleMapper.get());
                    return membershipService.addOrUpdate(user.getReferenceId(), membership).ignoreElement();
                });
    }

    @Override
    public Single<User> update(User user) {
        log.debug("Update a user {}", user);
        // updated date
        user.setUpdatedAt(new Date());
        return userValidator.validate(user).andThen(getUserRepository()
                .findByUsernameAndSource(ORGANIZATION, user.getReferenceId(), user.getUsername(), user.getSource())
                .switchIfEmpty(getUserRepository().findById(Reference.organization(user.getReferenceId()), user.getFullId()))
                .switchIfEmpty(Single.error(new UserNotFoundException(user.getId())))
                .flatMap(oldUser -> {

                    user.setId(oldUser.getId());
                    user.setReferenceType(oldUser.getReferenceType());
                    user.setReferenceId(oldUser.getReferenceId());
                    if (user.getFirstName() != null) {
                        user.setDisplayName(user.getFirstName() + (user.getLastName() != null ? " " + user.getLastName() : ""));
                    } else {
                        user.setDisplayName(user.getUsername());
                    }
                    user.setSource(oldUser.getSource());
                    user.setInternal(oldUser.isInternal());
                    user.setUpdatedAt(new Date());
                    if (user.getLoginsCount() < oldUser.getLoginsCount()) {
                        user.setLoggedAt(oldUser.getLoggedAt());
                        user.setLoginsCount(oldUser.getLoginsCount());
                    }

                    return getUserRepository().update(user);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurs while trying to update a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a user", ex));
                }));
    }

    @Override
    public Single<AccountAccessToken> generateAccountAccessToken(User user, NewAccountAccessToken newAccountToken, String issuer) {
        var rawToken = SecureRandomString.generate();

        var token = AccountAccessToken.builder()
                .tokenId(RandomString.generate())
                .referenceType(ORGANIZATION)
                .referenceId(user.getReferenceId())
                .userId(user.getId())
                .issuerId(issuer)
                .name(newAccountToken.name())
                .token(accountAccessTokenEncoder.encode(rawToken))
                .build();
        return accountAccessTokenRepository.findByUserId(ORGANIZATION, user.getReferenceId(), user.getId())
                .count()
                .flatMap(count -> {
                    if (count >= tokensLimit) {
                        log.debug("Limit of account token per user reached ({})", count);
                        return Single.error(new TooManyAccountTokenException(tokensLimit));
                    }
                    return accountAccessTokenRepository.create(token)
                            .map(created -> created.toCreateResponse(rawToken))
                            .flatMap(i -> prepareTokenToGet(i, true));
                });
    }

    @Override
    public Flowable<AccountAccessToken> findUserAccessTokens(String organisationId, String userId) {
        return accountAccessTokenRepository.findByUserId(ORGANIZATION, organisationId, userId)
                .flatMap(token -> prepareTokenToGet(token).toFlowable());
    }

    @Override
    public Completable revokeUserAccessTokens(ReferenceType referenceType, String referenceId, String userId) {
        return accountAccessTokenRepository.deleteByUserId(referenceType, referenceId, userId);
    }

    private Single<AccountAccessToken> prepareTokenToGet(AccountAccessToken token) {
        return prepareTokenToGet(token, false);
    }

    private Single<AccountAccessToken> prepareTokenToGet(AccountAccessToken token, boolean showToken) {
        var tokenValue = showToken ? token.token() : null;
        return token.issuerId() == null ? Single.just(token.toBuilder().token(tokenValue).build()) :
                userRepository.findById(token.issuerId()).defaultIfEmpty(new User())
                        .map(user -> token.toBuilder().token(tokenValue).issuerUsername(user.getUsername()).build());
    }

    @Override
    public Single<User> findByAccessToken(String tokenId, String tokenValue) {
        return accountAccessTokenRepository.findById(tokenId)
                .filter(token -> accountAccessTokenEncoder.matches(tokenValue, token.token()))
                .flatMapSingle(token -> findById(token.referenceType(), token.referenceId(), token.userId()))
                .toSingle();
    }

    @Override
    public Maybe<AccountAccessToken> revokeToken(String organizationId, String userId, String tokenId) {
        return accountAccessTokenRepository.findById(tokenId)
                .filter(token -> token.referenceId().equals(organizationId))
                .filter(token -> token.userId().equals(userId))
                .flatMap(token -> accountAccessTokenRepository.delete(token.tokenId()).andThen(Maybe.just(token)));
    }

    @Override
    public Single<User> delete(String userId) {
        log.debug("Delete user {}", userId);
        return getUserRepository().findById(userId)
                .switchIfEmpty(Single.error(new UserNotFoundException(userId)))
                .flatMap(user -> getUserRepository().delete(userId).toSingleDefault(user))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    log.error("An error occurs while trying to delete user: {}", userId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete user: %s", userId), ex));
                }).flatMap(user -> accountAccessTokenRepository.deleteByUserId(user.getReferenceType(), user.getReferenceId(), user.getId()).toSingleDefault(user));
    }

    @Override
    public Flowable<User> findByIdIn(List<String> ids) {
        String userIds = String.join(",", ids);
        log.debug("Find users by ids: {}", userIds);
        return getUserRepository().findByIdIn(ids)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find users by ids {}", userIds, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by ids %s", userIds), ex));
                });
    }

    @Override
    public Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        log.debug("Find users by {}: {}", referenceType, referenceId);
        return getUserRepository().findAll(referenceType, referenceId, page, size)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find users by {} {}", referenceType, referenceId, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by %s %s", referenceType, referenceId), ex));
                });
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size) {
        log.debug("Search users for {} {} with query {}", referenceType, referenceId, query);
        return getUserRepository().search(referenceType, referenceId, query, page, size)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to search users for {} {} and query {}", referenceType, referenceId, query, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users for %s %s and query %s", referenceType, referenceId, query), ex));
                });
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria filterCriteria, int page, int size) {
        log.debug("Search users for {} {} with filter {}", referenceType, referenceId, filterCriteria);
        return getUserRepository().search(referenceType, referenceId, filterCriteria, page, size)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof IllegalArgumentException) {
                        return Single.error(new InvalidParameterException(ex.getMessage()));
                    }
                    log.error("An error occurs while trying to search users for {} {} and filter {}", referenceType, referenceId, filterCriteria, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users for %s %s and filter %s", referenceType, referenceId, filterCriteria), ex));
                });
    }

    @Override
    public Flowable<User> search(ReferenceType referenceType, String referenceId, FilterCriteria filterCriteria) {
        log.debug("Search users for {} {} with filter {}", referenceType, referenceId, filterCriteria);
        return getUserRepository().search(referenceType, referenceId, filterCriteria)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof IllegalArgumentException) {
                        return Flowable.error(new InvalidParameterException(ex.getMessage()));
                    }
                    log.error("An error occurs while trying to search users for {} {} and filter {}", referenceType, referenceId, filterCriteria, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find users for %s %s and filter %s", referenceType, referenceId, filterCriteria), ex));
                });
    }

    @Override
    public Single<User> findById(ReferenceType referenceType, String referenceId, String id) {
        return findById(new Reference(referenceType, referenceId), UserId.internal(id));
    }

    @Override
    public Single<User> findById(Reference reference, UserId userId) {
        log.debug("Find user by id : {}", userId);
        return getUserRepository().findById(reference, userId)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find a user using its ID {}", userId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s", userId), ex));
                })
                .switchIfEmpty(Single.error(new UserNotFoundException(userId)));

    }

    @Override
    public Maybe<User> findByUsernameAndSource(Reference reference, String username, String source) {
        log.debug("Find user by {}, username and source: {} {}", reference, username, source);
        return getUserRepository().findByUsernameAndSource(reference.type(), reference.id(), username, source)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find a user using its username: {} for the {}  and source {}", username, reference, source, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its username: %s for the %s and source %s", username, reference, source), ex));
                });
    }

    @Override
    public Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source) {
        log.debug("Find user by {} {}, externalId and source: {} {}", referenceType, referenceId, externalId, source);
        return getUserRepository().findByExternalIdAndSource(referenceType, referenceId, externalId, source)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find a user using its externalId: {} for the {} {} and source {}", externalId, referenceType, referenceId, source, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its externalId: %s for the %s %s and source %s", externalId, referenceType, referenceId, source), ex));
                });
    }


    @Override
    public Single<User> create(ReferenceType referenceType, String referenceId, NewUser newUser) {
        log.debug("Create a new user {} for {} {}", newUser, referenceType, referenceId);
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
                        log.error(CREATE_USER_ERROR, ex);
                        return Single.error(new TechnicalManagementException(CREATE_USER_ERROR, ex));
                    }
                });
    }

    @Override
    public Single<User> create(User user) {
        log.debug("Create a user {}", user);
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
                    log.error(CREATE_USER_ERROR, ex);
                    return Single.error(new TechnicalManagementException(CREATE_USER_ERROR, ex));
                });
    }

    @Override
    public Single<User> update(ReferenceType referenceType, String referenceId, String id, UpdateUser updateUser) {
        log.debug("Update a user {} for {} {}", id, referenceType, referenceId);

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

                    log.error("An error occurs while trying to update a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a user", ex));
                });
    }
}

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
package io.gravitee.am.management.service.impl;

import com.google.common.base.Strings;
import io.gravitee.am.business.user.UpdateUsernameOrganizationRule;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.model.AbstractNewUser;
import io.gravitee.am.service.model.NewAccountAccessToken;
import io.gravitee.am.service.model.NewOrganizationUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.management.service.impl.IdentityProviderManagerImpl.IDP_GRAVITEE;
import static io.gravitee.am.model.ReferenceType.ORGANIZATION;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementOrganizationUserService")
@Slf4j
public class OrganizationUserServiceImpl implements OrganizationUserService {

    public static final BCryptPasswordEncoder PWD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private io.gravitee.am.service.OrganizationUserService userService;

    @Autowired
    protected IdentityProviderManager identityProviderManager;

    @Autowired
    protected PasswordService passwordService;

    @Autowired
    protected UserValidator userValidator;

    @Autowired
    protected AuditService auditService;

    @Autowired
    protected MembershipService membershipService;

    protected io.gravitee.am.service.OrganizationUserService getUserService() {
        return this.userService;
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size) {
        return userService.search(referenceType, referenceId, query, page, size);
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria filterCriteria, int page, int size) {
        return userService.search(referenceType, referenceId, filterCriteria, page, size);
    }

    @Override
    public Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        return userService.findAll(referenceType, referenceId, page, size);
    }

    @Override
    public Single<User> createOrUpdate(ReferenceType referenceType, String referenceId, NewOrganizationUser newUser) {
        return userService.findByExternalIdAndSource(referenceType, referenceId, newUser.getExternalId(), newUser.getSource())
                .switchIfEmpty(Maybe.defer(() -> userService.findByUsernameAndSource(referenceType, referenceId, newUser.getUsername(), newUser.getSource())))
                .flatMap(existingUser -> {
                    updateInfos(existingUser, newUser);
                    return userService.update(existingUser).toMaybe();
                })
                .switchIfEmpty(Single.defer(() -> {
                    if (StringUtils.isBlank(newUser.getUsername())) {
                        return Single.error(() -> new UserInvalidException("Field [username] is required"));
                    }
                    User user = transform(newUser, referenceType, referenceId);
                    return userService.create(user)
                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED).user(user1)))
                            .doOnError(err -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED).reference(new Reference(referenceType, referenceId)).throwable(err)));
                }));
    }

    public Single<User> createGraviteeUser(Organization organization, NewOrganizationUser newUser, io.gravitee.am.identityprovider.api.User principal) {
        if (StringUtils.isBlank(newUser.getUsername())) {
            return Single.error(() -> new UserInvalidException("Field [username] is required"));
        }
        // Organization user are linked to the Gravitee Idp only
        if (!Strings.isNullOrEmpty(newUser.getSource()) && !IDP_GRAVITEE.equals(newUser.getSource())) {
            return Single.error(new UserInvalidException("Invalid identity provider for ['" + newUser.getUsername() + "']"));
        }
        // force the value to avoid null reference
        newUser.setSource(IDP_GRAVITEE);

        // check user
        return userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, organization.getId(), newUser.getUsername(), newUser.getSource())
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (Boolean.FALSE.equals(isEmpty)) {
                        return Single.error(new UserAlreadyExistsException(newUser.getUsername()));
                    } else {
                        // check user provider
                        return identityProviderManager.getUserProvider(newUser.getSource())
                                .switchIfEmpty(Single.error(new UserProviderNotFoundException(newUser.getSource())))
                                .flatMap(userProvider -> {
                                    newUser.setDomain(null);
                                    newUser.setClient(null);
                                    // user is flagged as internal user
                                    newUser.setInternal(true);
                                    if (!newUser.isServiceAccount()) {
                                        String password = newUser.getPassword();
                                        if (password == null || !passwordService.isValid(password)) {
                                            return Single.error(InvalidPasswordException.of("Field [password] is invalid"));
                                        }
                                    }
                                    newUser.setRegistrationCompleted(true);
                                    newUser.setEnabled(true);

                                    // store user in its identity provider:
                                    // - perform first validation of user to avoid error status 500 when the IDP is based on relational databases
                                    // - in case of error, trace the event otherwise continue the creation process
                                    final User userToPersist = transform(newUser, ReferenceType.ORGANIZATION, organization.getId());
                                    userToPersist.setReferenceId(organization.getId());
                                    userToPersist.setReferenceType(ReferenceType.ORGANIZATION);

                                    return userValidator.validate(userToPersist)
                                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).reference(Reference.organization(organization.getId())).throwable(throwable)))
                                            .andThen(userProvider.create(convert(newUser))
                                                    .map(idpUser -> {
                                                        // Excepted for GraviteeIDP that manage Organization Users
                                                        // AM 'users' collection is not made for authentication (but only management stuff)
                                                        if(!newUser.isServiceAccount()) {
                                                            userToPersist.setPassword(PWD_ENCODER.encode(newUser.getPassword()));
                                                        }
                                                        // set external id
                                                        // id and external id are the same for GraviteeIdP users
                                                        userToPersist.setId(RandomString.generate());
                                                        userToPersist.setExternalId(userToPersist.getId());
                                                        return userToPersist;
                                                    })
                                                    .flatMap(newOrgUser -> userService.create(newOrgUser)
                                                            .flatMap(newlyCreatedUser -> userService.setRoles(newlyCreatedUser).andThen(Single.just(newlyCreatedUser)))
                                                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).user(user1)))
                                                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_CREATED).reference(Reference.organization(organization.getId())).throwable(throwable)))
                                                    ));
                                });

                    }
                });
    }

    @Override
    public Completable resetPassword(String organizationId, User user, String password, io.gravitee.am.identityprovider.api.User principal) {
        if (password == null || !passwordService.isValid(password)) {
            return Completable.error(InvalidPasswordException.of("Field [password] is invalid"));
        }

        if (!IDP_GRAVITEE.equals(user.getSource())) {
            return Completable.error(new InvalidUserException("Unsupported source for this action"));
        }

        // update 'users' collection for management and audit purpose
        user.setLastPasswordReset(new Date());
        user.setUpdatedAt(new Date());
        user.setPassword(PWD_ENCODER.encode(password));
        return userService.update(user)
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_PASSWORD_RESET).user(user)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_PASSWORD_RESET).reference(Reference.organization(organizationId)).throwable(throwable)))
                .ignoreElement();
    }

    @Override
    public Single<User> updateLogoutDate(ReferenceType referenceType, String referenceId, String id) {
        return getUserService().findById(referenceType, referenceId, id)
                .flatMap(user -> {
                    final Date now = new Date();
                    user.setLastLogoutAt(now);
                    user.setUpdatedAt(now);
                    return getUserService().update(user);
                });
    }

    @Override
    public Flowable<AccountAccessToken> findAccountAccessTokens(String organizationId, String userId) {
        return userService.findUserAccessTokens(organizationId, userId);
    }

    @Override
    public Single<AccountAccessToken> createAccountAccessToken(String organizationId, String userId, NewAccountAccessToken newAccountToken, io.gravitee.am.identityprovider.api.User principal) {
        if (!hasText(newAccountToken.name())) {
            return Single.error(new InvalidParameterException("Token name is required"));
        }

        if (newAccountToken.name().length() > 254) {
            return Single.error(new InvalidParameterException("Token name is too long"));
        }

        return getUserService().findById(ReferenceType.ORGANIZATION, organizationId, userId)
                .flatMap(user -> getUserService().generateAccountAccessToken(user, newAccountToken, principal.getId())
                        .doOnSuccess(token -> auditService.report(createAccountAccessTokenAudit(principal)
                                .user(user)
                                .accountToken(token)
                        )))
                .doOnError(throwable -> auditService.report(createAccountAccessTokenAudit(principal)
                        .reference(Reference.organization(organizationId))
                        .throwable(throwable)));
    }

    private UserAuditBuilder createAccountAccessTokenAudit(io.gravitee.am.identityprovider.api.User principal) {
        return AuditBuilder.builder(UserAuditBuilder.class)
                .principal(principal)
                .type(EventType.ACCOUNT_ACCESS_TOKEN_CREATED);
    }

    @Override
    public Single<User> findByAccessToken(String tokenId, String accountToken) {
        return getUserService().findByAccessToken(tokenId, accountToken);
    }

    @Override
    public Maybe<AccountAccessToken> revokeToken(String organizationId, String userId, String tokenId, io.gravitee.am.identityprovider.api.User principal) {
        return getUserService().findById(Reference.organization(organizationId), UserId.internal(userId))
                .flatMapMaybe(user ->
                        getUserService().revokeToken(organizationId, userId, tokenId)
                                .doOnSuccess(revoked -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                                        .principal(principal)
                                        .type(EventType.ACCOUNT_ACCESS_TOKEN_REVOKED)
                                        .user(user)
                                        .accountToken(revoked)))
                                .doOnError(x -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                                        .principal(principal)
                                        .type(EventType.ACCOUNT_ACCESS_TOKEN_REVOKED)
                                        .user(user)
                                        .accountToken(tokenId)
                                        .throwable(x))));
    }

    @Override
    public Single<User> delete(ReferenceType referenceType, String referenceId, String userId, io.gravitee.am.identityprovider.api.User principal) {
        return getUserService().findById(referenceType, referenceId, userId)
                .flatMap(user -> identityProviderManager.getUserProvider(user.getSource())
                        .map(Optional::ofNullable)
                        .flatMapCompletable(optUserProvider -> {
                            // no user provider found, continue
                            if (optUserProvider.isEmpty()) {
                                return Completable.complete();
                            }
                            // user has never been created in the identity provider, continue
                            if (user.getExternalId() == null || user.getExternalId().isEmpty()) {
                                return Completable.complete();
                            }
                            return optUserProvider.get().delete(user.getExternalId())
                                    .onErrorResumeNext(ex -> {
                                        if (ex instanceof UserNotFoundException) {
                                            // idp user does not exist, continue
                                            return Completable.complete();
                                        }
                                        return Completable.error(ex);
                                    });
                        })
                        .andThen(getUserService().delete(userId).ignoreElement())
                        // remove from memberships if user is an administrative user
                        .andThen(membershipService.findByMember(userId, MemberType.USER)
                                .flatMapCompletable(membership -> membershipService.delete(new Reference(membership.getReferenceType(), membership.getReferenceId()), membership.getId())))
                        .toSingleDefault(user))
                .doOnSuccess(u -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_DELETED).user(u)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_DELETED).reference(new Reference(referenceType, referenceId)).throwable(throwable)))
                .flatMap(user -> getUserService().revokeUserAccessTokens(user.getReferenceType(), user.getReferenceId(), user.getId()).toSingleDefault(user));
    }

    @Override
    public Single<User> updateUsername(ReferenceType referenceType, String referenceId, String id, String
            username, io.gravitee.am.identityprovider.api.User principal) {
        return new UpdateUsernameOrganizationRule(userValidator,
                getUserService()::findByUsernameAndSource,
                getUserService()::update,
                auditService)
                .updateUsername(username, principal,
                        (User user) -> identityProviderManager.getUserProvider(user.getSource()).switchIfEmpty(Single.error(() -> new UserProviderNotFoundException(user.getSource()))),
                        () -> getUserService().findById(referenceType, referenceId, id));
    }

    @Override
    public Single<User> updateStatus(String organizationId, String userId, boolean status, io.gravitee.am.identityprovider.api.User principal) {
        return updateStatus(ORGANIZATION, organizationId, userId, status, principal);
    }

    @Override
    public Single<User> findById(ReferenceType referenceType, String referenceId, String id) {
        return getUserService().findById(referenceType, referenceId, id);
    }

    @Override
    public Single<User> update(ReferenceType referenceType, String referenceId, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal) {
        return this.updateUser(referenceType, referenceId, id, updateUser, principal);
    }

    private Single<User> updateUser(ReferenceType referenceType, String referenceId, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal) {
        if (id == null) {
            return Single.error(new InvalidUserException("User id is required"));
        }
        return getUserService().findById(referenceType, referenceId, id).flatMap(user -> {

                    if (Boolean.FALSE.equals(user.isInternal()) && Boolean.TRUE.equals(updateUser.getForceResetPassword())) {
                        return Single.error(new InvalidUserException("forceResetPassword is forbidden on external users"));
                    }

                    // This handles identity providers not enforcing email
                    final boolean validateEmailIfNecessary = !(isNullOrEmpty(user.getEmail()) && isNullOrEmpty(updateUser.getEmail()));
                    return userValidator.validate(updateUser, validateEmailIfNecessary).andThen(Single.just(user));
                })
                .flatMap(user -> updateWithProviderIfNecessary(updateUser, user))
                .flatMap(user -> getUserService().update(referenceType, referenceId, id, user)
                        .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).oldValue(user).user(user1)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).reference(new Reference(referenceType, referenceId)).throwable(throwable))));
    }

    private Single<UpdateUser> updateWithProviderIfNecessary(UpdateUser updateUser, User user) {
        final Maybe<UserProvider> userProvider = identityProviderManager.getUserProvider(user.getSource());
        return userProvider.isEmpty().flatMap(noProvider -> noProvider ?
                Single.just(updateUser) :
                updateWithUserProvider(updateUser, user, userProvider.toSingle())
        );
    }

    private Single<UpdateUser> updateWithUserProvider(UpdateUser updateUser, User
            user, Single<UserProvider> userProvider) {
        return userProvider.flatMap(provider -> provider.findByUsername(user.getUsername())
                .switchIfEmpty(Single.error(() -> new UserNotFoundException(user.getUsername())))
                .flatMap(idpUser -> provider.update(idpUser.getId(), convert(user.getUsername(), updateUser)))
                .map(idpUser -> {
                    updateUser.setExternalId(idpUser.getId());
                    return updateUser;
                })).onErrorResumeNext(ex -> {
            if (ex instanceof UserNotFoundException) {
                // idp user does not exist, return the updated user to save in AM
                return Single.just(updateUser);
            }
            return Single.error(ex);
        });
    }

    @Override
    public Single<User> updateStatus(ReferenceType referenceType, String referenceId, String userId, boolean status, io.
            gravitee.am.identityprovider.api.User principal) {
        return getUserService().findById(referenceType, referenceId, userId)
                .flatMap(user -> {
                    user.setEnabled(status);
                    return getUserService().update(user);
                })
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type((status ? EventType.USER_ENABLED : EventType.USER_DISABLED)).user(user1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type((status ? EventType.USER_ENABLED : EventType.USER_DISABLED)).reference(new Reference(referenceType, referenceId)).throwable(throwable)));
    }

    protected io.gravitee.am.identityprovider.api.User convert(AbstractNewUser newUser) {
        DefaultUser user = new DefaultUser(newUser.getUsername());
        user.setCredentials(newUser.getPassword());

        Map<String, Object> additionalInformation = new HashMap<>();
        if (newUser.getFirstName() != null) {
            user.setFirstName(newUser.getFirstName());
            additionalInformation.put(StandardClaims.GIVEN_NAME, newUser.getFirstName());
        }
        if (newUser.getLastName() != null) {
            user.setLastName(newUser.getLastName());
            additionalInformation.put(StandardClaims.FAMILY_NAME, newUser.getLastName());
        }
        if (newUser.getEmail() != null) {
            user.setEmail(newUser.getEmail());
            additionalInformation.put(StandardClaims.EMAIL, newUser.getEmail());
        }
        if (newUser.getAdditionalInformation() != null) {
            newUser.getAdditionalInformation().forEach(additionalInformation::putIfAbsent);
        }
        user.setAdditionalInformation(additionalInformation);

        return user;
    }

    protected User transform(NewOrganizationUser newUser, ReferenceType referenceType, String referenceId) {
        User user = new User();
        user.setId(RandomString.generate());
        user.setExternalId(newUser.getExternalId());
        user.setReferenceId(referenceId);
        user.setReferenceType(referenceType);
        user.setClient(newUser.getClient());
        user.setEnabled(newUser.isEnabled());
        user.setUsername(newUser.getUsername());
        user.setFirstName(newUser.getFirstName());
        user.setLastName(newUser.getLastName());
        user.setEmail(newUser.getEmail());
        user.setSource(newUser.getSource());
        user.setInternal(newUser.isInternal());
        user.setPreRegistration(newUser.isPreRegistration());
        user.setRegistrationCompleted(newUser.isRegistrationCompleted());
        user.setPreferredLanguage(newUser.getPreferredLanguage());
        user.setAdditionalInformation(newUser.getAdditionalInformation());
        user.setForceResetPassword(newUser.getForceResetPassword());
        user.setCreatedAt(new Date());
        user.setUpdatedAt(user.getCreatedAt());
        if (newUser.getLastPasswordReset() != null) {
            if (newUser.getLastPasswordReset().toInstant().isAfter(Instant.now())) {
                throw new UserInvalidException("lastPasswordReset cannot be in the future");
            }
            user.setLastPasswordReset(newUser.getLastPasswordReset());
        } else if(!newUser.isPreRegistration()){
            user.setLastPasswordReset(user.getCreatedAt());
        }
        user.setServiceAccount(newUser.isServiceAccount());
        return user;
    }

    protected void updateInfos(User user, NewOrganizationUser newUser) {
        user.setFirstName(newUser.getFirstName());
        user.setLastName(newUser.getLastName());
        user.setEmail(newUser.getEmail());
        user.setAdditionalInformation(newUser.getAdditionalInformation());
    }

    protected io.gravitee.am.identityprovider.api.User convert(String username, UpdateUser updateUser) {
        // update additional information
        DefaultUser user = new DefaultUser(username);
        Map<String, Object> additionalInformation = new HashMap<>();
        if (updateUser.getFirstName() != null) {
            user.setFirstName(updateUser.getFirstName());
            additionalInformation.put(StandardClaims.GIVEN_NAME, updateUser.getFirstName());
        }
        if (updateUser.getLastName() != null) {
            user.setLastName(updateUser.getLastName());
            additionalInformation.put(StandardClaims.FAMILY_NAME, updateUser.getLastName());
        }
        if (updateUser.getEmail() != null) {
            user.setEmail(updateUser.getEmail());
            additionalInformation.put(StandardClaims.EMAIL, updateUser.getEmail());
        }
        if (updateUser.getAdditionalInformation() != null) {
            updateUser.getAdditionalInformation().forEach(additionalInformation::putIfAbsent);
        }
        user.setAdditionalInformation(additionalInformation);
        if (updateUser.getForceResetPassword() != null) {
            user.setForceResetPassword(updateUser.getForceResetPassword());
        }
        return user;
    }

    protected io.gravitee.am.identityprovider.api.User convert(User user) {
        DefaultUser idpUser = new DefaultUser(user.getUsername());
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
            user.getAdditionalInformation().forEach(additionalInformation::putIfAbsent);
        }
        idpUser.setAdditionalInformation(additionalInformation);
        idpUser.setForceResetPassword(Boolean.FALSE);
        return idpUser;
    }
}

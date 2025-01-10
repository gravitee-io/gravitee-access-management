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
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.NotImplementedException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.model.NewAccountAccessToken;
import io.gravitee.am.service.model.NewOrganizationUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.function.BiFunction;

import static io.gravitee.am.management.service.impl.IdentityProviderManagerImpl.IDP_GRAVITEE;
import static org.springframework.util.StringUtils.hasText;
import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.model.ReferenceType.ORGANIZATION;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementOrganizationUserService")
public class OrganizationUserServiceImpl extends AbstractUserService<io.gravitee.am.service.OrganizationUserService> implements OrganizationUserService {

    public static final BCryptPasswordEncoder PWD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private io.gravitee.am.service.OrganizationUserService userService;

    @Override
    protected io.gravitee.am.service.OrganizationUserService getUserService() {
        return this.userService;
    }

    @Override
    protected BiFunction<String, String, Maybe<Application>> checkClientFunction() {
        return (x, y) -> Maybe.error(new NotImplementedException());
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

    protected User transform(NewOrganizationUser newUser, ReferenceType referenceType, String referenceId) {
        var user = super.transform(newUser, referenceType, referenceId);
        user.setServiceAccount(newUser.isServiceAccount());
        return user;
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
                                            return Single.error(InvalidPasswordException.of("Field [password] is invalid", "invalid_password_value"));
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
            return Completable.error(InvalidPasswordException.of("Field [password] is invalid", "invalid_password_value"));
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
    public Completable revokeToken(String organizationId, String userId, String tokenId, io.gravitee.am.identityprovider.api.User principal) {
        return getUserService().findById(Reference.organization(organizationId), UserId.internal(userId))
                .flatMap(user ->
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
                                        .throwable(x)))
                )
                .ignoreElement();
    }

    @Override
    public Single<User> delete(ReferenceType referenceType, String referenceId, String userId, io.gravitee.am.identityprovider.api.User principal) {
        return super.delete(referenceType, referenceId, userId, principal)
                .flatMap(user -> getUserService().revokeUserAccessTokens(user.getReferenceType(), user.getReferenceId(), user.getId()).toSingleDefault(user));
    }
    @Override
    public Single<User> updateStatus(String organizationId, String userId, boolean status, io.gravitee.am.identityprovider.api.User principal) {
        return updateStatus(ORGANIZATION, organizationId, userId, status, principal);
    }
}

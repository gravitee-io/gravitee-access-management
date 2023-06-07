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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.gateway.handler.common.audit.AuditReporterManager;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.root.service.response.ResetPasswordResponse;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.*;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.CredentialAuditBuilder;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * @author Donald Courtney (donald.courtney at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountServiceImpl implements AccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountServiceImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private UserValidator userValidator;

    @Autowired
    private UserService userService;

    @Autowired
    private io.gravitee.am.gateway.handler.root.service.user.UserService gatewayUserService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private FactorService factorService;

    @Autowired
    private AuditReporterManager auditReporterManager;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private ScopeApprovalService scopeApprovalService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<User> get(String userId) {
        return userService.findById(userId);
    }

    @Override
    public Single<Page<Audit>> getActivity(User user, AuditReportableCriteria criteria, int page, int size) {
        try {
            Single<Page<Audit>> reporter = auditReporterManager.getReporter().search(ReferenceType.DOMAIN, user.getReferenceId(), criteria, page, size);
            return reporter.map(result -> {
                if (Objects.isNull(result) || Objects.isNull(result.getData())) {
                    return new Page<>(new ArrayList<>(), 0, 0);
                }
                return result;
            });
        } catch (Exception ex) {
            LOGGER.error("An error occurs during audits search for {}}: {}", ReferenceType.DOMAIN, user.getReferenceId(), ex);
            return Single.error(ex);
        }
    }

    @Override
    public Single<User> update(User user) {
        LOGGER.debug("Update a user {} for domain {}", user.getUsername(), domain.getName());

        return userValidator.validate(user).andThen(identityProviderManager.getUserProvider(user.getSource())
                .switchIfEmpty(Single.error(new UserProviderNotFoundException(user.getSource())))
                .flatMap(userProvider -> {
                    if (user.getExternalId() == null) {
                        return Single.error(new InvalidRequestException("User does not exist in upstream IDP"));
                    } else {
                        return userProvider.update(user.getExternalId(), convert(user));
                    }
                })
                .flatMap(idpUser -> userRepository.update(user))
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

    @Override
    public Single<ResetPasswordResponse> resetPassword(User user, Client client, String password, io.gravitee.am.identityprovider.api.User principal, Optional<String> olPassword) {
        return Single.defer(() -> {
            PasswordSettings passwordSettings = PasswordSettings.getInstance(client, this.domain).orElse(null);
            passwordService.validate(password, passwordSettings, user);
            user.setPassword(password);

            final boolean needOldPassword = domain.getSelfServiceAccountManagementSettings() != null && domain.getSelfServiceAccountManagementSettings().resetPasswordWithOldValue();
            if (needOldPassword && olPassword.isEmpty()) {
                return Single.error(InvalidPasswordException.of("oldPassword is missing", InvalidPasswordException.INVALID_PASSWORD_VALUE));
            }
            var pwdCheck = needOldPassword ? gatewayUserService.checkPassword(user, olPassword.get(), principal) : Completable.complete();

            return pwdCheck.andThen(Single.defer(() -> gatewayUserService.resetPassword(client, user, principal)));
        });
    }

    @Override
    public Single<List<Factor>> getFactors(String domain) {
        return factorService.findByDomain(domain).toList();
    }

    @Override
    public Maybe<Factor> getFactor(String id) {
        return factorService.findById(id);
    }

    @Override
    public Single<User> upsertFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal) {
        return userService.upsertFactor(userId, enrolledFactor, principal);
    }

    @Override
    public Completable removeFactor(String userId, String factorId, io.gravitee.am.identityprovider.api.User principal) {
        return userService.removeFactor(userId, factorId, principal);
    }

    @Override
    public Single<List<Credential>> getWebAuthnCredentials(User user) {
        return credentialService.findByUserId(ReferenceType.DOMAIN, user.getReferenceId(), user.getId())
                .map(credential -> {
                    removeSensitiveData(credential);
                    return credential;
                })
                .toList();
    }

    @Override
    public Single<Credential> getWebAuthnCredential(String id) {
        return credentialService.findById(id)
                .switchIfEmpty(Single.error(new CredentialNotFoundException(id)))
                .map(credential -> {
                    removeSensitiveData(credential);
                    return credential;
                });
    }

    @Override
    public Completable removeWebAuthnCredential(String userId, String id, io.gravitee.am.identityprovider.api.User principal) {
        return credentialService.findById(id)
                .flatMapCompletable(credential -> {
                    if (!userId.equals(credential.getUserId())) {
                        LOGGER.debug("Webauthn credential ID {} does not belong to the user ID {}, skip delete action", id, userId);
                        return Completable.complete();
                    }
                    return credentialService.delete(id)
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(CredentialAuditBuilder.class).principal(principal).type(EventType.CREDENTIAL_DELETED).credential(credential)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(CredentialAuditBuilder.class).principal(principal).type(EventType.CREDENTIAL_DELETED).throwable(throwable)));
                });
    }

    @Override
    public Single<Credential> updateWebAuthnCredential(String userId, String id, String deviceName, io.gravitee.am.identityprovider.api.User principal) {
        return credentialService.findById(id)
                .switchIfEmpty(Single.error(new CredentialNotFoundException(id)))
                .flatMap(credential -> {
                    if (!userId.equals(credential.getUserId())) {
                        LOGGER.debug("Webauthn credential ID {} does not belong to the user ID {}, skip update action", id, userId);
                        return Single.just(credential);
                    }
                    credential.setDeviceName(deviceName);
                    credential.setUpdatedAt(new Date());
                    return credentialService.update(credential)
                            .doOnSuccess(credential1 -> auditService.report(AuditBuilder.builder(CredentialAuditBuilder.class).principal(principal).type(EventType.CREDENTIAL_UPDATED).oldValue(credential).credential(credential1)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(CredentialAuditBuilder.class).principal(principal).type(EventType.CREDENTIAL_UPDATED).throwable(throwable)));
                });
    }

    @Override
    public Single<List<ScopeApproval>> getConsentList(User user, Client client) {
        return scopeApprovalService.findByDomainAndUserAndClient(domain.getId(),  user.getId(), client.getClientId()).toList();
    }

    @Override
    public Single<ScopeApproval> getConsent(String id) {
        return scopeApprovalService.findById(id)
                .switchIfEmpty(Single.error(new ScopeApprovalNotFoundException(id)));
    }

    @Override
    public Completable removeConsent(String userId, String consentId, io.gravitee.am.identityprovider.api.User principal) {
        return scopeApprovalService.revokeByConsent(domain.getId(), userId, consentId, principal);
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
        if (user.getPicture() != null) {
            additionalInformation.put(StandardClaims.PICTURE, user.getPicture());
        }
        if (user.getMiddleName() != null) {
            additionalInformation.put(StandardClaims.MIDDLE_NAME, user.getMiddleName());
        }
        if (user.getNickName() != null) {
            additionalInformation.put(StandardClaims.NICKNAME, user.getNickName());
        }
        if (user.getProfile() != null) {
            additionalInformation.put(StandardClaims.PROFILE, user.getProfile());
        }
        if (user.getWebsite() != null) {
            additionalInformation.put(StandardClaims.WEBSITE, user.getWebsite());
        }
        if (user.getBirthdate() != null) {
            additionalInformation.put(StandardClaims.BIRTHDATE, user.getBirthdate());
        }
        if (user.getZoneInfo() != null) {
            additionalInformation.put(StandardClaims.ZONEINFO, user.getZoneInfo());
        }
        if (user.getLocale() != null) {
            additionalInformation.put(StandardClaims.LOCALE, user.getLocale());
        }
        if (user.getPhoneNumber() != null) {
            additionalInformation.put(StandardClaims.PHONE_NUMBER, user.getPhoneNumber());
        }
        if (user.getAddress() != null) {
            additionalInformation.put(StandardClaims.ADDRESS, user.getAddress());
        }
        if (user.getAdditionalInformation() != null) {
            user.getAdditionalInformation().forEach(additionalInformation::putIfAbsent);
        }
        idpUser.setAdditionalInformation(additionalInformation);
        return idpUser;
    }

    private void removeSensitiveData(Credential credential) {
        credential.setReferenceType(null);
        credential.setReferenceId(null);
        credential.setUserId(null);
        credential.setUsername(null);
        credential.setCounter(null);
    }

}

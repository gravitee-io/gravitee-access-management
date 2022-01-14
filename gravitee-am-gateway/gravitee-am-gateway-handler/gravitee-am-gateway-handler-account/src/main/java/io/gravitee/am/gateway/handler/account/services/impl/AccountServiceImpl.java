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
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.CredentialNotFoundException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
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
                .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                .flatMapSingle(userProvider -> {
                    if (user.getExternalId() == null) {
                        return Single.error(new InvalidRequestException("User does not exist in upstream IDP"));
                    } else {
                        return userProvider.update(user.getExternalId(), convert(user));
                    }
                })
                .flatMap(idpUser -> {
                    return userRepository.update(user);
                })
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
    public Single<ResetPasswordResponse> resetPassword(User user, Client client, String password, io.gravitee.am.identityprovider.api.User principal) {
        return Single.defer(() -> {
            PasswordSettings passwordSettings = PasswordSettings.getInstance(client, this.domain).orElse(null);
            passwordService.validate(password, passwordSettings, user);
            user.setPassword(password);
            return gatewayUserService.resetPassword(client, user, principal);
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
    public Completable removeWebAuthnCredential(String id) {
        return credentialService.delete(id);
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

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

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.repository.management.api.LoginAttemptRepository;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.LoginAttemptNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginAttemptServiceImpl.class);

    @Lazy
    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Override
    public Single<LoginAttempt> loginFailed(LoginAttemptCriteria criteria, AccountSettings accountSettings) {
        LOGGER.debug("Add login attempt for {}", criteria);
        return loginAttemptRepository.findByCriteria(criteria)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMapSingle(optionalLoginAttempt -> {
                    if (optionalLoginAttempt.isPresent()) {
                        LoginAttempt loginAttempt = optionalLoginAttempt.get();
                        loginAttempt.setAttempts(loginAttempt.getAttempts() + 1);
                        if (loginAttempt.getAttempts() >= accountSettings.getMaxLoginAttempts()) {
                            loginAttempt.setExpireAt(new Date(System.currentTimeMillis() + (accountSettings.getAccountBlockedDuration() * 1000)));
                        }
                        loginAttempt.setUpdatedAt(new Date());
                        return loginAttemptRepository.update(loginAttempt);
                    } else {
                        LoginAttempt loginAttempt = new LoginAttempt();
                        loginAttempt.setId(RandomString.generate());
                        loginAttempt.setDomain(criteria.domain());
                        loginAttempt.setClient(criteria.client());
                        loginAttempt.setIdentityProvider(criteria.identityProvider());
                        loginAttempt.setUsername(criteria.username());
                        loginAttempt.setAttempts(1);
                        if (loginAttempt.getAttempts() >= accountSettings.getMaxLoginAttempts()) {
                            loginAttempt.setExpireAt(new Date(System.currentTimeMillis() + (accountSettings.getAccountBlockedDuration() * 1000)));
                        } else {
                            loginAttempt.setExpireAt(new Date(System.currentTimeMillis() + (accountSettings.getLoginAttemptsResetTime() * 1000)));
                        }
                        loginAttempt.setCreatedAt(new Date());
                        loginAttempt.setUpdatedAt(loginAttempt.getCreatedAt());
                        return loginAttemptRepository.create(loginAttempt);
                    }
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to add a login attempt", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to add a login attempt", ex));
                });
    }

    @Override
    public Completable loginSucceeded(LoginAttemptCriteria criteria) {
        LOGGER.debug("Delete login attempt for {}", criteria);
        return loginAttemptRepository.delete(criteria)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete login attempt for", criteria, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete login attempt: %s", criteria), ex));
                });
    }

    @Override
    public Completable reset(LoginAttemptCriteria criteria) {
        return loginSucceeded(criteria);
    }

    @Override
    public Maybe<LoginAttempt> checkAccount(LoginAttemptCriteria criteria, AccountSettings accountSettings) {
        LOGGER.debug("Check account status for {}", criteria);
        return loginAttemptRepository.findByCriteria(criteria);
    }

    @Override
    public Maybe<LoginAttempt> findById(String id) {
        LOGGER.debug("Find login attempt by id {}", id);
        return loginAttemptRepository.findById(id)
                .switchIfEmpty(Maybe.error(new LoginAttemptNotFoundException(id)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Maybe.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to find login attempt by id {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to fin login attempt by id: %s", id), ex));
                });
    }
}

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

package io.gravitee.am.gateway.handler.common.service.impl;


import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria;
import io.gravitee.am.gateway.handler.common.service.LoginAttemptGatewayService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.LoginAttemptNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@AllArgsConstructor
public class LoginAttemptGatewayServiceImpl implements LoginAttemptGatewayService {

    private final DataPlaneRegistry dataPlaneRegistry;

    @Override
    public Single<LoginAttempt> loginFailed(Domain domain, LoginAttemptCriteria criteria, AccountSettings accountSettings) {
        log.debug("Add login attempt for {}", criteria);
        final var loginAttemptRepository = dataPlaneRegistry.getLoginAttemptRepository(domain);
        return loginAttemptRepository.findByCriteria(criteria)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalLoginAttempt -> {
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
                    log.error("An error occurs while trying to add a login attempt", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to add a login attempt", ex));
                });
    }

    @Override
    public Completable loginSucceeded(Domain domain, LoginAttemptCriteria criteria) {
        log.debug("Delete login attempt for {}", criteria);
        return dataPlaneRegistry.getLoginAttemptRepository(domain).delete(criteria)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to delete login attempt for {}", criteria, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete login attempt: %s", criteria), ex));
                });
    }

    @Override
    public Maybe<LoginAttempt> checkAccount(Domain domain, LoginAttemptCriteria criteria, AccountSettings accountSettings) {
        log.debug("Check account status for {}", criteria);
        return dataPlaneRegistry.getLoginAttemptRepository(domain).findByCriteria(criteria);
    }

    @Override
    public Maybe<LoginAttempt> findById(Domain domain, String id) {
        log.debug("Find login attempt by id {}", id);
        return dataPlaneRegistry.getLoginAttemptRepository(domain).findById(id)
                .switchIfEmpty(Maybe.error(new LoginAttemptNotFoundException(id)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Maybe.error(ex);
                    }
                    log.error("An error occurs while trying to find login attempt by id {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to fin login attempt by id: %s", id), ex));
                });
    }
}

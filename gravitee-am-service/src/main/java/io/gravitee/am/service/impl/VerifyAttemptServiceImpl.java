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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.VerifyAttempt;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.gateway.api.VerifyAttemptRepository;
import io.gravitee.am.repository.management.api.search.VerifyAttemptCriteria;
import io.gravitee.am.service.VerifyAttemptService;
import io.gravitee.am.service.exception.MFAValidationAttemptException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class VerifyAttemptServiceImpl implements VerifyAttemptService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VerifyAttemptServiceImpl.class);

    @Lazy
    @Autowired
    VerifyAttemptRepository verifyAttemptRepository;

    @Override
    public Maybe<VerifyAttempt> checkVerifyAttempt(User user, String factorId, Client client, Domain domain) {
        final AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
        if (accountSettings == null || !accountSettings.isMfaChallengeAttemptsDetectionEnabled()) {
            LOGGER.debug("MFA brute force detection is disabled, won't check verify attempt.");
            return Maybe.empty();
        }

        final VerifyAttemptCriteria criteria = buildCriteria(user.getId(), factorId, client.getId());

        return getVerifyAttemptIfExists(criteria, accountSettings)
                .doOnSuccess(verifyAttempt -> {
                    LOGGER.debug("VerifyAttempt value: [{}]", verifyAttempt);
                    if (!verifyAttempt.isAllowRequest()) {
                        throw new MFAValidationAttemptException(verifyAttempt, "Maximum verification limit exceed");
                    }
                });
    }

    @Override
    public Completable incrementAttempt(String userId, String factorId, Client client, Domain domain, Optional<VerifyAttempt> optionalVerifyAttempt) {
        final AccountSettings accountSettings = AccountSettings.getInstance(domain, client);

        if (accountSettings == null || !accountSettings.isMfaChallengeAttemptsDetectionEnabled()) {
            return Completable.complete();
        }
        if (optionalVerifyAttempt.isPresent()) {
            final VerifyAttempt verifyAttempt = optionalVerifyAttempt.get();
            final int attempts = verifyAttempt.getAttempts() + 1;
            if (attempts >= accountSettings.getMfaChallengeMaxAttempts()) {
                verifyAttempt.setAllowRequest(false);
                verifyAttempt.setAttempts(accountSettings.getMfaChallengeMaxAttempts());
            } else {
                verifyAttempt.setAttempts(attempts);
                verifyAttempt.setAllowRequest(true);
            }
            verifyAttempt.setUpdatedAt(new Date());

            return verifyAttemptRepository.update(verifyAttempt).ignoreElement();

        } else {
            final VerifyAttempt verifyAttempt = new VerifyAttempt();
            verifyAttempt.setUserId(userId);
            verifyAttempt.setFactorId(factorId);
            verifyAttempt.setClient(client.getId());
            verifyAttempt.setReferenceId(domain.getId());
            verifyAttempt.setReferenceType(ReferenceType.DOMAIN);
            final int attempts = 1;
            verifyAttempt.setAttempts(attempts);
            verifyAttempt.setAllowRequest(attempts < accountSettings.getMfaChallengeMaxAttempts());
            verifyAttempt.setCreatedAt(new Date());
            verifyAttempt.setUpdatedAt(verifyAttempt.getCreatedAt());

            return verifyAttemptRepository.create(verifyAttempt).ignoreElement();
        }
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete verify attempt id: {}", id);
        return verifyAttemptRepository.delete(id);
    }

    @Override
    public Completable deleteByUser(User user) {
        LOGGER.debug("deleteByUser userID: {}", user.getId());
        return verifyAttemptRepository.deleteByUser(user.getId());
    }

    @Override
    public Completable deleteByDomain(Domain domain, ReferenceType referenceType) {
        LOGGER.debug("deleteByDomain domainId: {}", domain.getId());
        return verifyAttemptRepository.deleteByDomain(domain.getId(), referenceType);
    }

    @Override
    public boolean shouldSendEmail(Client client, Domain domain) {
        final AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
        return accountSettings.isMfaChallengeSendVerifyAlertEmail();
    }

    private Maybe<VerifyAttempt> getVerifyAttemptIfExists(VerifyAttemptCriteria criteria, AccountSettings accountSettings) {
        return verifyAttemptRepository.findByCriteria(criteria)
                .flatMap(verifyAttempt -> {
                        final Date resetTime = new Date(verifyAttempt.getUpdatedAt().getTime() + (accountSettings.getMfaChallengeAttemptsResetTime() * 1000));
                        final Date currentDate = new Date();

                        if (currentDate.getTime() > resetTime.getTime()) {
                            verifyAttempt.setAttempts(0);
                            verifyAttempt.setAllowRequest(true);
                        } else {
                            verifyAttempt.setAllowRequest(verifyAttempt.getAttempts() < accountSettings.getMfaChallengeMaxAttempts());
                        }
                        return Maybe.just(verifyAttempt);
                });
    }

    private VerifyAttemptCriteria buildCriteria(String userId, String factorId, String client) {
        return new VerifyAttemptCriteria.Builder()
                .userId(userId)
                .factorId(factorId)
                .client(client)
                .build();
    }
}

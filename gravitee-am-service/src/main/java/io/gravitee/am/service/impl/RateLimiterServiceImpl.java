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
import io.gravitee.am.model.RateLimit;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.gateway.api.RateLimitRepository;
import io.gravitee.am.repository.management.api.search.RateLimitCriteria;
import io.gravitee.am.service.RateLimiterService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class RateLimiterServiceImpl implements RateLimiterService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiterServiceImpl.class);

    @Value("${mfa_rate.enabled:false}")
    private boolean isEnabled;

    @Value("${mfa_rate.limit:5}")
    private int limit;

    @Value("${mfa_rate.timePeriod:15}")
    private int timePeriod;

    @Value("${mfa_rate.timeUnit:Minutes}")
    private String timeUnit;

    @Lazy
    @Autowired
    RateLimitRepository rateLimitRepository;

    @Override
    public boolean isRateLimitEnabled() {
        return isEnabled;
    }

    @Override
    public Single<Boolean> tryConsume(String userId, String factorId, String client, String domainId) {
        if (timePeriod <= 0 || limit <= 0) {
            LOGGER.warn("Either timePeriod or limit is set to 0. Current value timePeriod: {}, limit: {}", limit, timePeriod);
            return Single.just(false);
        }

        final RateLimitCriteria criteria = buildCriteria(userId, factorId, client);
        return getRateLimit(criteria, domainId).flatMap(rateLimit -> {
            LOGGER.debug("RateLimit value: [{}]", rateLimit);
            return Single.just(rateLimit.isAllowRequest());
        });
    }

    @Override
    public Completable deleteByUser(User user) {
        LOGGER.debug("deleteByUser userID: {}", user.getId());
        return rateLimitRepository.deleteByUser(user.getId());
    }

    @Override
    public Completable deleteByDomain(Domain domain, ReferenceType referenceType) {
        LOGGER.debug("deleteByDomain domainId: {}", domain.getId());
        return rateLimitRepository.deleteByDomain(domain.getId(), referenceType);
    }

    private Single<RateLimit> getRateLimit(RateLimitCriteria criteria, String domainId) {
        return rateLimitRepository.findByCriteria(criteria)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalRateLimit -> {
                    if (optionalRateLimit.isPresent()) {
                        final RateLimit rateLimit = optionalRateLimit.get();
                        calculateAndSetTokenLeft(rateLimit, timeUnit, timePeriod, limit);
                        rateLimit.setUpdatedAt(new Date());
                        return rateLimitRepository.update(rateLimit);
                    } else {
                        final RateLimit rateLimit = new RateLimit();
                        rateLimit.setUserId(criteria.userId());
                        rateLimit.setFactorId(criteria.factorId());
                        rateLimit.setClient(criteria.client());
                        rateLimit.setReferenceId(domainId);
                        rateLimit.setReferenceType(ReferenceType.DOMAIN);
                        rateLimit.setCreatedAt(new Date());
                        rateLimit.setUpdatedAt(rateLimit.getCreatedAt());
                        //value of left tokens should be "limit -1" for the first request
                        rateLimit.setTokenLeft(limit - 1L);
                        rateLimit.setAllowRequest(true);
                        return rateLimitRepository.create(rateLimit);
                    }

                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to add/update rate limit", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to add/update rate limit.", ex));
                });
    }

    private RateLimitCriteria buildCriteria(String userId, String factorId, String client) {
        return new RateLimitCriteria.Builder()
                .userId(userId)
                .factorId(factorId)
                .client(client)
                .build();
    }
}

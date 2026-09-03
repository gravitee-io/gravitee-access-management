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
package io.gravitee.am.gateway.handler.common.service.ratelimit;

import io.gravitee.am.model.RateLimit;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.gateway.api.RateLimitRepository;
import io.gravitee.am.repository.gateway.api.search.RateLimitCriteria;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Date;
import java.util.Optional;

/**
 * Reads, refills and stores the bucket a criteria identifies. Subclasses only bring their own
 * configuration keys, so that one feature's thresholds never bind another's.
 *
 * A peek refills on a copy that is never written back, so asking whether a request would be
 * allowed does not itself cost the caller an attempt.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public abstract class AbstractRateLimiterService implements TokenBucketLimiter {

    @Lazy
    @Autowired
    protected RateLimitRepository rateLimitRepository;

    protected abstract int getLimit();

    protected abstract int getTimePeriod();

    protected abstract String getTimeUnit();

    protected Single<Boolean> peek(RateLimitCriteria criteria) {
        return rateLimitRepository.findByCriteria(criteria)
                .map(rateLimit -> {
                    calculateAndSetTokenLeft(rateLimit, getTimeUnit(), getTimePeriod(), getLimit());
                    return rateLimit.isAllowRequest();
                })
                .defaultIfEmpty(true);
    }

    protected Single<Boolean> tryConsume(RateLimitCriteria criteria, String domainId) {
        if (getTimePeriod() <= 0 || getLimit() <= 0) {
            log.warn("Either timePeriod or limit is set to 0. Current value timePeriod: {}, limit: {}", getLimit(), getTimePeriod());
            return Single.just(false);
        }

        return getRateLimit(criteria, domainId).flatMap(rateLimit -> {
            log.debug("RateLimit value: [{}]", rateLimit);
            return Single.just(rateLimit.isAllowRequest());
        });
    }

    private Single<RateLimit> getRateLimit(RateLimitCriteria criteria, String domainId) {
        return rateLimitRepository.findByCriteria(criteria)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalRateLimit -> {
                    if (optionalRateLimit.isPresent()) {
                        final RateLimit rateLimit = optionalRateLimit.get();
                        calculateAndSetTokenLeft(rateLimit, getTimeUnit(), getTimePeriod(), getLimit());
                        rateLimit.setUpdatedAt(new Date());
                        return rateLimitRepository.update(rateLimit);
                    } else {
                        final RateLimit rateLimit = new RateLimit();
                        rateLimit.setUserId(criteria.userId());
                        rateLimit.setFactorId(criteria.factorId());
                        rateLimit.setPurpose(criteria.purpose());
                        rateLimit.setClient(criteria.client());
                        rateLimit.setReferenceId(domainId);
                        rateLimit.setReferenceType(ReferenceType.DOMAIN);
                        rateLimit.setCreatedAt(new Date());
                        rateLimit.setUpdatedAt(rateLimit.getCreatedAt());
                        //value of left tokens should be "limit -1" for the first request
                        rateLimit.setTokenLeft(getLimit() - 1L);
                        rateLimit.setAllowRequest(true);
                        return rateLimitRepository.create(rateLimit);
                    }

                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurs while trying to add/update rate limit", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to add/update rate limit.", ex));
                });
    }
}

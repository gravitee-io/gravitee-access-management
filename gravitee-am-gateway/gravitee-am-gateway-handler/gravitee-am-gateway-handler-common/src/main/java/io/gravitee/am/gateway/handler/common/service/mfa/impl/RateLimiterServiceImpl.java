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
package io.gravitee.am.gateway.handler.common.service.mfa.impl;

import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.gravitee.am.gateway.handler.common.service.ratelimit.AbstractRateLimiterService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.gateway.api.search.RateLimitCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class RateLimiterServiceImpl extends AbstractRateLimiterService implements RateLimiterService {
    @Value("${mfa_rate.enabled:false}")
    private boolean isEnabled;

    @Value("${mfa_rate.limit:5}")
    private int limit;

    @Value("${mfa_rate.timePeriod:15}")
    private int timePeriod;

    @Value("${mfa_rate.timeUnit:Minutes}")
    private String timeUnit;

    @Override
    public boolean isRateLimitEnabled() {
        return isEnabled;
    }

    @Override
    public Single<Boolean> tryConsume(String userId, String factorId, String client, String domainId) {
        return tryConsume(buildCriteria(userId, factorId, client), domainId);
    }

    @Override
    public Completable deleteByUser(User user) {
        log.debug("deleteByUser userID: {}", user.getId());
        return rateLimitRepository.deleteByUser(user.getId());
    }

    @Override
    public Completable deleteByDomain(Domain domain, ReferenceType referenceType) {
        log.debug("deleteByDomain domainId: {}", domain.getId());
        return rateLimitRepository.deleteByDomain(domain.getId(), referenceType);
    }

    @Override
    protected int getLimit() {
        return limit;
    }

    @Override
    protected int getTimePeriod() {
        return timePeriod;
    }

    @Override
    protected String getTimeUnit() {
        return timeUnit;
    }

    private RateLimitCriteria buildCriteria(String userId, String factorId, String client) {
        return new RateLimitCriteria.Builder()
                .userId(userId)
                .factorId(factorId)
                .client(client)
                .build();
    }
}

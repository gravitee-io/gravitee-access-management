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
package io.gravitee.am.gateway.handler.common.service.ratelimit.impl;

import io.gravitee.am.gateway.handler.common.service.ratelimit.AbstractRateLimiterService;
import io.gravitee.am.gateway.handler.common.service.ratelimit.DeviceFlowRateLimiterService;
import io.gravitee.am.repository.gateway.api.search.RateLimitCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author GraviteeSource Team
 */
public class DeviceFlowRateLimiterServiceImpl extends AbstractRateLimiterService implements DeviceFlowRateLimiterService {

    @Value("${device_flow_rate.enabled:false}")
    private boolean isEnabled;

    @Value("${device_flow_rate.limit:5}")
    private int limit;

    @Value("${device_flow_rate.timePeriod:15}")
    private int timePeriod;

    @Value("${device_flow_rate.timeUnit:Minutes}")
    private String timeUnit;

    @Override
    public boolean isRateLimitEnabled() {
        return isEnabled;
    }

    @Override
    public Single<Boolean> hasAttemptsLeft(String userId, String domainId) {
        return peek(criteria(userId));
    }

    @Override
    public Completable recordWrongAttempt(String userId, String domainId) {
        return tryConsume(criteria(userId), domainId).ignoreElement();
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

    private RateLimitCriteria criteria(String userId) {
        return new RateLimitCriteria.Builder()
                .userId(userId)
                .purpose(PURPOSE)
                .build();
    }
}

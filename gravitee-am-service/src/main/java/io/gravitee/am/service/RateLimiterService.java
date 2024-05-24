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
package io.gravitee.am.service;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.RateLimit;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface RateLimiterService {
    Single<Boolean> tryConsume(String userId, String factorId, String applicationId, String domainId);

    boolean isRateLimitEnabled();

    Completable deleteByUser(User user);

    Completable deleteByDomain(Domain domain, ReferenceType referenceType);

    default void calculateAndSetTokenLeft(RateLimit rateLimit, String timeUnit, int timePeriod, int limit) {
        final int consumeOne = 1;
        long now = Instant.now().toEpochMilli();
        long lastRequested = rateLimit.getUpdatedAt().toInstant().toEpochMilli();
        long timeElapsed = now - lastRequested;
        long periodDuration = timePeriodToMillSeconds(timeUnit, timePeriod) / limit;
        // We need to know how many tokens could be generated in between current time and the last request time (which is timeElapsed variable)
        //periodDuration is calculated form timeUnit and timePeriod
        long newTokens = Math.max(0, timeElapsed / periodDuration);
        long tokenLeft = Math.max(0, rateLimit.getTokenLeft() + newTokens);

        if (tokenLeft > limit) {
            rateLimit.setTokenLeft(limit - consumeOne);
            rateLimit.setAllowRequest(true);
        } else {
            if (tokenLeft > 0) {
                rateLimit.setTokenLeft(tokenLeft - consumeOne);
                rateLimit.setAllowRequest(true);
            } else {
                rateLimit.setTokenLeft(0);
                rateLimit.setAllowRequest(false);
            }
        }
    }

    private long timePeriodToMillSeconds(String timeUnit, int timePeriod){
        ChronoUnit unit = ChronoUnit.valueOf(timeUnit.trim().toUpperCase());
        long seconds = switch (unit) {
            case HOURS -> timePeriod * 60L * 60L;
            case MINUTES -> timePeriod * 60L;
            case SECONDS -> timePeriod;
            default -> Duration.of(timePeriod, unit).toMillis();
        };

        return TimeUnit.SECONDS.toMillis(seconds);
    }
}

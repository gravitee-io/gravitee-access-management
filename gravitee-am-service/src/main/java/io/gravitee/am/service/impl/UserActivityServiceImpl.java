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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.model.UserActivity.Type;
import io.gravitee.am.repository.management.api.UserActivityRepository;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.am.service.impl.user.activity.configuration.UserActivityConfiguration;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static io.gravitee.am.service.impl.user.activity.utils.CoordinateUtils.computeCoordinate;
import static io.gravitee.am.service.impl.user.activity.utils.HashedKeyUtils.computeHash;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UserActivityServiceImpl implements UserActivityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserActivityServiceImpl.class);

    private static final String LONGITUDE_KEY = "lon";
    private static final String LATITUDE_KEY = "lat";
    private static final String USER_AGENT = "user_agent";
    private static final String LOGIN_ATTEMPTS = "login_attempts";
    private static final int LONGITUDE_BOUNDARY = 180;
    private static final int LATITUDE_BOUNDARY = 90;

    private final UserActivityConfiguration configuration;
    private final UserActivityRepository userActivityRepository;

    public UserActivityServiceImpl(
            UserActivityConfiguration configuration,
            @Lazy UserActivityRepository userActivityRepository
    ) {
        this.configuration = configuration;
        this.userActivityRepository = userActivityRepository;
    }

    public boolean canSaveUserActivity() {
        return this.configuration.isEnabled();
    }

    public long getRetentionTime() {
        return this.configuration.getRetentionTime();
    }

    public ChronoUnit getRetentionUnit() {
        return this.configuration.getRetentionUnit();
    }

    public Flowable<UserActivity> findByDomainAndTypeAndUserAndLimit(String domain, Type type, String userId, int limit) {
        return userActivityRepository.findByDomainAndTypeAndKeyAndLimit(domain, type, buildKey(userId), limit);
    }

    public Completable save(
            String domain,
            String userId,
            UserActivity.Type type,
            Map<String, Object> data) {
        final Date createdAt = new Date();
        return Single.defer(() -> Single.just(new UserActivity()
                                .setReferenceType(ReferenceType.DOMAIN)
                                .setReferenceId(domain)
                                .setUserActivityType(type)
                                .setUserActivityKey(buildKey(userId))
                                .setLatitude(buildLatitude(data))
                                .setLongitude(buildLongitude(data))
                                .setUserAgent((String) data.get(USER_AGENT))
                                .setLoginAttempts((Integer) data.getOrDefault(LOGIN_ATTEMPTS, 0))
                                .setCreatedAt(createdAt)
                                .setExpireAt(getExpireAtDate(createdAt))
                        )
                ).flatMap(userActivityRepository::create)
                .doOnSuccess(ua -> LOGGER.debug("UserActivity with id '{}' created", ua.getId()))
                .doOnError(err ->
                        LOGGER.error("An unexpected error has occurred while saving UserActivity '{}'", err.getMessage(), err)
                ).ignoreElement();
    }

    public Completable deleteByDomainAndUser(String domain, String userId) {
        return userActivityRepository.deleteByDomainAndKey(domain, buildKey(userId)).doOnError(err ->
                LOGGER.error("An unexpected error has occurred while deleting userActivity '{}'", err.getMessage(), err)
        );
    }

    public Completable deleteByDomain(String domain) {
        return userActivityRepository.deleteByDomain(domain).doOnError(err ->
                LOGGER.error("An unexpected error has occurred while deleting userActivity '{}'", err.getMessage(), err)
        );
    }

    private String buildKey(String userId) {
        return computeHash(configuration.getAlgorithmKey(), userId, configuration.getSalt());
    }

    private Double buildLatitude(Map<String, Object> data) {
        return computeCoordinate(data, LATITUDE_KEY, configuration.getLatitudeVariation(), LATITUDE_BOUNDARY);
    }

    private Double buildLongitude(Map<String, Object> data) {
        return computeCoordinate(data, LONGITUDE_KEY, configuration.getLongitudeVariation(), LONGITUDE_BOUNDARY);
    }

    private Date getExpireAtDate(Date createdAt) {
        final ChronoUnit chronoUnit = configuration.getRetentionUnit();
        final long retentionTime = configuration.getRetentionTime();
        var expireTime = chronoUnit.getDuration().toMillis() * Math.abs(retentionTime);
        return new Date(createdAt.getTime() + expireTime);
    }
}

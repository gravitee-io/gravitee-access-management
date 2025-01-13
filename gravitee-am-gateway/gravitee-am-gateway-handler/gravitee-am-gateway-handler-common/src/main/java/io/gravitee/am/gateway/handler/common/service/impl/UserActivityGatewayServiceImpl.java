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


import io.gravitee.am.gateway.handler.common.service.UserActivityGatewayService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.dataplane.user.activity.configuration.UserActivityConfiguration;
import io.gravitee.am.service.dataplane.user.activity.utils.UserActivityFunctions;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import static io.gravitee.am.service.dataplane.user.activity.utils.UserActivityFunctions.LOGIN_ATTEMPTS;
import static io.gravitee.am.service.dataplane.user.activity.utils.UserActivityFunctions.USER_AGENT;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class UserActivityGatewayServiceImpl implements UserActivityGatewayService {

    private final UserActivityConfiguration configuration;
    private final UserActivityFunctions activityFunctions;
    private final DataPlaneRegistry dataPlaneRegistry;

    public UserActivityGatewayServiceImpl(UserActivityConfiguration configuration, DataPlaneRegistry dataPlaneRegistry) {
        this.configuration = configuration;
        this.activityFunctions = new UserActivityFunctions(configuration);
        this.dataPlaneRegistry = dataPlaneRegistry;
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

    public Flowable<UserActivity> findByDomainAndTypeAndUserAndLimit(Domain domain, UserActivity.Type type, String userId, int limit) {
        return dataPlaneRegistry.getUserActivityRepository(domain)
                .flatMapPublisher(repository -> repository.findByDomainAndTypeAndKeyAndLimit(domain.getId(), type, activityFunctions.buildKey(userId), limit));
    }

    public Completable save(
            Domain domain,
            String userId,
            UserActivity.Type type,
            Map<String, Object> data) {
        final Date createdAt = new Date();

        final var activity = new UserActivity()
                .setReferenceType(ReferenceType.DOMAIN)
                .setReferenceId(domain.getId())
                .setUserActivityType(type)
                .setUserActivityKey(activityFunctions.buildKey(userId))
                .setLatitude(activityFunctions.buildLatitude(data))
                .setLongitude(activityFunctions.buildLongitude(data))
                .setUserAgent((String) data.get(USER_AGENT))
                .setLoginAttempts((Integer) data.getOrDefault(LOGIN_ATTEMPTS, 0))
                .setCreatedAt(createdAt)
                .setExpireAt(activityFunctions.getExpireAtDate(createdAt));

        return dataPlaneRegistry.getUserActivityRepository(domain)
                .flatMap(repository -> repository.create(activity))
                .doOnSuccess(ua -> log.debug("UserActivity with id '{}' created", ua.getId()))
                .doOnError(err ->
                        log.error("An unexpected error has occurred while saving UserActivity '{}'", err.getMessage(), err)
                ).ignoreElement();
    }

    public Completable deleteByDomainAndUser(Domain domain, String userId) {
        return dataPlaneRegistry.getUserActivityRepository(domain)
                .flatMapCompletable(repository -> repository.deleteByDomainAndKey(domain.getId(), activityFunctions.buildKey(userId)))
                .doOnError(err ->
                        log.error("An unexpected error has occurred while deleting userActivity '{}'", err.getMessage(), err)
                );
    }
}

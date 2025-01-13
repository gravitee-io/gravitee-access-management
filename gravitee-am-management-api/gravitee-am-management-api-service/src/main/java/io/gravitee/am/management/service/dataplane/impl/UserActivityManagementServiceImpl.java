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

package io.gravitee.am.management.service.dataplane.impl;


import io.gravitee.am.management.service.dataplane.UserActivityManagementService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.impl.user.activity.configuration.UserActivityConfiguration;
import io.gravitee.am.service.impl.user.activity.utils.UserActivityFunctions;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class UserActivityManagementServiceImpl implements UserActivityManagementService {

    private final UserActivityFunctions activityFunctions;
    private final DataPlaneRegistry dataPlaneRegistry;

    public UserActivityManagementServiceImpl(UserActivityConfiguration activityFunctions, DataPlaneRegistry dataPlaneRegistry) {
        this.activityFunctions = new UserActivityFunctions(activityFunctions);
        this.dataPlaneRegistry = dataPlaneRegistry;
    }

    public Completable deleteByDomainAndUser(Domain domain, String userId) {
        return dataPlaneRegistry.getUserActivityRepository(domain)
                .flatMapCompletable(repository -> repository.deleteByDomainAndKey(domain.getId(), activityFunctions.buildKey(userId)))
                .doOnError(err ->
                        log.error("An unexpected error has occurred while deleting userActivity '{}'", err.getMessage(), err)
                );
    }

    public Completable deleteByDomain(Domain domain) {
        return dataPlaneRegistry.getUserActivityRepository(domain)
                .flatMapCompletable(repository -> repository.deleteByDomain(domain.getId()))
                .doOnError(err ->
                        log.error("An unexpected error has occurred while deleting userActivity '{}'", err.getMessage(), err)
                );
    }
}

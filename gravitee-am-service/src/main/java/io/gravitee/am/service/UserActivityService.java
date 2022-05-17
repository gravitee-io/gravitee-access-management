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

import io.gravitee.am.model.UserActivity;
import io.gravitee.am.model.UserActivity.Type;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserActivityService {

    boolean canSaveUserActivity();

    long getRetentionTime();

    ChronoUnit getRetentionUnit();

    Flowable<UserActivity> findByDomainAndTypeAndUserAndLimit(String domain, Type type, String userId, int limit);

    Completable save(String domain, String userId, UserActivity.Type type, Map<String, Object> data);

    Completable deleteByDomainAndUser(String domain, String userId);

    Completable deleteByDomain(String domain);
}

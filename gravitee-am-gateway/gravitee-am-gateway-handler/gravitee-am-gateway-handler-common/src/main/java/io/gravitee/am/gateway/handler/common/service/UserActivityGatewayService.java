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

package io.gravitee.am.gateway.handler.common.service;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserActivity;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;

import java.time.temporal.ChronoUnit;
import java.util.Map;

public interface UserActivityGatewayService {

    boolean canSaveUserActivity();

    long getRetentionTime();

    ChronoUnit getRetentionUnit();

    Flowable<UserActivity> findByDomainAndTypeAndUserAndLimit(Domain domain, UserActivity.Type type, String userId, int limit);

    Completable save(Domain domain, String userId, UserActivity.Type type, Map<String, Object> data);

    Completable deleteByDomainAndUser(Domain domain, String userId);

}

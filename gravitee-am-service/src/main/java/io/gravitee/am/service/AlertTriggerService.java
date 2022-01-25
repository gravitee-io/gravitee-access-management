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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.gravitee.am.service.model.PatchAlertTrigger;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AlertTriggerService {
    Single<AlertTrigger> getById(String id);

    Single<AlertTrigger> getById(ReferenceType referenceType, String referenceId, String id);

    Flowable<AlertTrigger> findByDomainAndCriteria(String domainId, AlertTriggerCriteria criteria);

    Single<AlertTrigger> createOrUpdate(ReferenceType referenceType, String referenceId, PatchAlertTrigger patchAlertTrigger, User byUser);

    Completable delete(ReferenceType referenceType, String referenceId, String alertTriggerId, User byUser);

    Completable deleteByReference(ReferenceType referenceType, String referenceId);
}

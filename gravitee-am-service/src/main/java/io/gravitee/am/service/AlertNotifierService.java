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
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.repository.management.api.search.AlertNotifierCriteria;
import io.gravitee.am.service.model.NewAlertNotifier;
import io.gravitee.am.service.model.PatchAlertNotifier;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AlertNotifierService {
    Single<AlertNotifier> getById(ReferenceType referenceType, String referenceId, String notifierId);

    Flowable<AlertNotifier> findByDomainAndCriteria(String domainId, AlertNotifierCriteria criteria);

    Flowable<AlertNotifier> findByReferenceAndCriteria(ReferenceType referenceType, String referenceId, AlertNotifierCriteria criteria);

    Single<AlertNotifier> create(ReferenceType referenceType, String referenceId, NewAlertNotifier newAlertNotifier, User byUser);

    Single<AlertNotifier> update(ReferenceType referenceType, String referenceId, String alertNotifierId, PatchAlertNotifier patchAlertNotifier, User byUser);

    Completable delete(ReferenceType referenceType, String referenceId, String notifierId, User byUser);
}

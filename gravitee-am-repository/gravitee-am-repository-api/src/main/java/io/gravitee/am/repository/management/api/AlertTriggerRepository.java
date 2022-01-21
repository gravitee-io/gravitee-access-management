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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.reactivex.Completable;
import io.reactivex.Flowable;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AlertTriggerRepository extends CrudRepository<AlertTrigger, String> {

    /**
     * Find all alert triggers for a given reference type and id.
     *
     * @param referenceType the reference type.
     * @param referenceId the reference id.
     * @return the list of alert triggers.
     */
    Flowable<AlertTrigger> findAll(ReferenceType referenceType, String referenceId);

    Completable deleteByReference(ReferenceType referenceType, String referenceId);

    /**
     * Find all alert triggers for a given reference type and id and matching specified criteria.
     *
     * @param referenceType the reference type.
     * @param referenceId the reference id.
     * @return the list of alert triggers.
     */
    Flowable<AlertTrigger> findByCriteria(ReferenceType referenceType, String referenceId, AlertTriggerCriteria criteria);
}

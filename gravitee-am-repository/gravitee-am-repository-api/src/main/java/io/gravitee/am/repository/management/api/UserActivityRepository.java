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
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.model.UserActivity.Type;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserActivityRepository extends CrudRepository<UserActivity, String> {

    Flowable<UserActivity> findByReferenceAndTypeAndKeyAndLimit(ReferenceType referenceType, String referenceId, Type type, String key, int limit);

    default Flowable<UserActivity> findByDomainAndTypeAndKey(String domain, Type type, String key) {
        return findByDomainAndTypeAndKeyAndLimit(domain, type, key, 0);
    }

    default Flowable<UserActivity> findByDomainAndTypeAndKeyAndLimit(String domain, Type type, String key, int limit) {
        return findByReferenceAndTypeAndKeyAndLimit(ReferenceType.DOMAIN, domain, type, key, limit);
    }

    Completable deleteByReferenceAndKey(ReferenceType referenceType, String referenceId, String key);

    default Completable deleteByDomainAndKey(String domain, String key) {
        return deleteByReferenceAndKey(ReferenceType.DOMAIN, domain, key);
    }

    Completable deleteByReference(ReferenceType referenceType, String referenceId);

    default Completable deleteByDomain(String domain) {
        return deleteByReference(ReferenceType.DOMAIN, domain);
    }

    default Completable purgeExpiredData() {
        return Completable.complete();
    }

}

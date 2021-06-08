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
package io.gravitee.am.management.service;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.Completable;
import io.reactivex.Single;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CommonUserService {

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size);

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria filterCriteria, int page, int size);

    Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size);

    Single<User> findById(ReferenceType referenceType, String referenceId, String id);

    Single<User> update(ReferenceType referenceType, String referenceId, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal);

    Single<User> updateStatus(ReferenceType referenceType, String referenceId, String id, boolean status, io.gravitee.am.identityprovider.api.User principal);

    default Completable delete(ReferenceType referenceType, String referenceId, String userId) {
        return delete(referenceType, referenceId, userId, null);
    }

    Completable delete(ReferenceType referenceType, String referenceId, String userId, io.gravitee.am.identityprovider.api.User principal);

}

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
package io.gravitee.am.dataplane.api.repository;

import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.Reference;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;

public interface PasswordHistoryRepository extends CrudRepository<PasswordHistory, String> {

    Flowable<PasswordHistory> findByReference(Reference reference);

    Flowable<PasswordHistory> findUserHistory(Reference reference, String userId);

    Completable deleteByUserId(String userId);

    Completable deleteByReference(Reference reference);
}

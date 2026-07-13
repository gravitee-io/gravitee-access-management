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

import io.gravitee.am.model.License;
import io.gravitee.am.model.ReferenceType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * The {@link License} entity is identified by the composite key (referenceType, referenceId),
 * so this repository does not extend {@code CrudRepository} (single identifier).
 *
 * @author GraviteeSource Team
 */
public interface LicenseRepository {

    Flowable<License> findAll();

    Maybe<License> findById(String referenceId, ReferenceType referenceType);

    Single<License> create(License license);

    Single<License> update(License license);

    Completable delete(String referenceId, ReferenceType referenceType);
}

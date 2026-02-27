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

import io.gravitee.am.model.EntityStoreVersion;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Repository for immutable {@link EntityStoreVersion} snapshots.
 *
 * @author GraviteeSource Team
 */
public interface EntityStoreVersionRepository extends CrudRepository<EntityStoreVersion, String> {

    /**
     * Returns all versions for a given entity store, ordered by version descending (newest first).
     */
    Flowable<EntityStoreVersion> findByEntityStoreId(String entityStoreId);

    /**
     * Returns a specific version of an entity store.
     */
    Maybe<EntityStoreVersion> findByEntityStoreIdAndVersion(String entityStoreId, int version);

    /**
     * Returns the latest (highest version number) snapshot for a given entity store.
     */
    Maybe<EntityStoreVersion> findLatestByEntityStoreId(String entityStoreId);

    /**
     * Deletes all version records for a given entity store (cascade).
     */
    Completable deleteByEntityStoreId(String entityStoreId);
}

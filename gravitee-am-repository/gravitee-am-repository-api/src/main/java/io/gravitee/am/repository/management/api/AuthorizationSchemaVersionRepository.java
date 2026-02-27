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

import io.gravitee.am.model.AuthorizationSchemaVersion;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Repository for immutable {@link AuthorizationSchemaVersion} snapshots.
 *
 * @author GraviteeSource Team
 */
public interface AuthorizationSchemaVersionRepository extends CrudRepository<AuthorizationSchemaVersion, String> {

    /**
     * Returns all versions for a given schema, ordered by version descending (newest first).
     */
    Flowable<AuthorizationSchemaVersion> findBySchemaId(String schemaId);

    /**
     * Returns a specific version of a schema.
     */
    Maybe<AuthorizationSchemaVersion> findBySchemaIdAndVersion(String schemaId, int version);

    /**
     * Returns the latest (highest version number) snapshot for a given schema.
     */
    Maybe<AuthorizationSchemaVersion> findLatestBySchemaId(String schemaId);

    /**
     * Deletes all version records for a given schema (cascade).
     */
    Completable deleteBySchemaId(String schemaId);
}

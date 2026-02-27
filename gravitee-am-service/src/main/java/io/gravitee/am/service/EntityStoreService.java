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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EntityStore;
import io.gravitee.am.model.EntityStoreVersion;
import io.gravitee.am.service.model.NewEntityStore;
import io.gravitee.am.service.model.UpdateEntityStore;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author GraviteeSource Team
 */
public interface EntityStoreService {

    Flowable<EntityStore> findByDomain(String domainId);

    Maybe<EntityStore> findById(String id);

    Maybe<EntityStore> findByDomainAndId(String domainId, String id);

    Single<EntityStore> create(Domain domain, NewEntityStore request, User principal);

    Single<EntityStore> update(Domain domain, String id, UpdateEntityStore request, User principal);

    Completable delete(Domain domain, String id, User principal);

    Completable deleteByDomain(String domainId);

    Flowable<EntityStoreVersion> getVersions(String entityStoreId);

    Maybe<EntityStoreVersion> getVersion(String entityStoreId, int version);

    Single<EntityStore> restoreVersion(Domain domain, String id, int version, User principal);
}

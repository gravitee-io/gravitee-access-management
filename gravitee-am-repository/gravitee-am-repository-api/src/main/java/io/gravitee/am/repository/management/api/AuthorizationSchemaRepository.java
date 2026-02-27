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

import io.gravitee.am.model.AuthorizationSchema;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * @author GraviteeSource Team
 */
public interface AuthorizationSchemaRepository extends CrudRepository<AuthorizationSchema, String> {

    Flowable<AuthorizationSchema> findByDomain(String domainId);

    Maybe<AuthorizationSchema> findByDomainAndId(String domainId, String id);

    Completable deleteByDomain(String domainId);
}

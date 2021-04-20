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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.Collection;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DomainRepository extends CrudRepository<Domain, String> {

    Flowable<Domain> findAllByReferenceId(String environmentId);

    Flowable<Domain> search(String environmentId, String query);

    Maybe<Domain> findByHrid(ReferenceType referenceType, String referenceId, String hrid);

    Single<Set<Domain>> findAll();

    Single<Set<Domain>> findByIdIn(Collection<String> ids);

    Flowable<Domain> findAllByCriteria(DomainCriteria criteria);
}

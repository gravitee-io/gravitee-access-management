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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.model.Policy;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.management.api.PolicyRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcPolicyRepository extends AbstractJdbcRepository implements PolicyRepository {

    @Override
    public Single<List<Policy>> findAll() {
        throw new IllegalStateException("Method not implemented");
    }

    @Override
    public Single<List<Policy>> findByDomain(String domain) {
        return Single.just(Collections.emptyList());
    }

    @Override
    public Maybe<Policy> findById(String id) {
        throw new IllegalStateException("Method not implemented");
    }

    @Override
    public Single<Policy> create(Policy item) {
        throw new IllegalStateException("Method not implemented");
    }

    @Override
    public Single<Policy> update(Policy item) {
        throw new IllegalStateException("Method not implemented");
    }

    @Override
    public Completable delete(String id) {
        throw new IllegalStateException("Method not implemented");
    }

    @Override
    public Single<Boolean> collectionExists() {
        return Single.just(false);
    }

    @Override
    public Completable deleteCollection() {
        return Completable.complete();
    }
}

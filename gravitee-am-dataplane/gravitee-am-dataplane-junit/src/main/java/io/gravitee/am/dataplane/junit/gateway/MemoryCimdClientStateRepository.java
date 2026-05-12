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
package io.gravitee.am.dataplane.junit.gateway;

import io.gravitee.am.dataplane.api.repository.CimdClientStateRepository;
import io.gravitee.am.dataplane.junit.MemoryRepository;
import io.gravitee.am.model.CimdClientState;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Date;
import java.util.UUID;

public class MemoryCimdClientStateRepository extends MemoryRepository<CimdClientState, String> implements CimdClientStateRepository {

    @Override
    public Maybe<CimdClientState> findByDomainAndClientId(String domainId, String clientId) {
        return findOne(s -> s.getDomainId().equals(domainId) && s.getClientId().equals(clientId));
    }

    @Override
    public Single<CimdClientState> upsert(CimdClientState state) {
        return findByDomainAndClientId(state.getDomainId(), state.getClientId())
                .flatMapSingle(existing -> {
                    state.setId(existing.getId());
                    state.setUpdatedAt(new Date());
                    return update(state);
                })
                .switchIfEmpty(create(state));
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        return findMany(s -> s.getDomainId().equals(domainId))
                .flatMapCompletable(s -> delete(s.getId()));
    }

    @Override
    protected String getId(CimdClientState item) {
        return item.getId();
    }

    @Override
    protected String generateAndSetId(CimdClientState item) {
        var id = UUID.randomUUID().toString();
        item.setId(id);
        return id;
    }
}

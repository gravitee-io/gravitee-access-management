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
package io.gravitee.am.management.repository.proxy;

import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ScopeApprovalRepositoryProxy extends AbstractProxy<ScopeApprovalRepository> implements ScopeApprovalRepository {

    public Single<Set<ScopeApproval>> findByDomainAndUserAndClient(String domain, String userId, String clientId) {
        return target.findByDomainAndUserAndClient(domain, userId, clientId);
    }

    public Single<Set<ScopeApproval>> findByDomainAndUser(String domain, String user) {
        return target.findByDomainAndUser(domain, user);
    }

    public Maybe<ScopeApproval> findById(String id) {
        return target.findById(id);
    }

    public Single<ScopeApproval> create(ScopeApproval item) {
        return target.create(item);
    }

    public Single<ScopeApproval> update(ScopeApproval item) {
        return target.update(item);
    }

    public Single<ScopeApproval> upsert(ScopeApproval scopeApproval) {
        return target.upsert(scopeApproval);
    }

    public Completable delete(String id) {
        return target.delete(id);
    }

    public Completable deleteByDomainAndScopeKey(String domain, String scope) {
        return target.deleteByDomainAndScopeKey(domain, scope);
    }

    public Completable deleteByDomainAndUserAndClient(String domain, String user, String client) {
        return target.deleteByDomainAndUserAndClient(domain, user, client);
    }

    public Completable deleteByDomainAndUser(String domain, String user) {
        return target.deleteByDomainAndUser(domain, user);
    }
}

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
package io.gravitee.am.repository.gateway.api;

import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ScopeApprovalRepository extends CrudRepository<ScopeApproval, String> {

    Flowable<ScopeApproval> findByDomainAndUserAndClient(String domain, String userId, String clientId);

    Flowable<ScopeApproval> findByDomainAndUser(String domain, String user);

    Single<ScopeApproval> upsert(ScopeApproval scopeApproval);

    Completable deleteByDomainAndScopeKey(String domain, String scope);

    Completable deleteByDomainAndUserAndClient(String domain, String user, String client);

    Completable deleteByDomainAndUser(String domain, String user);

    default Completable purgeExpiredData() {
        return Completable.complete();
    }

}

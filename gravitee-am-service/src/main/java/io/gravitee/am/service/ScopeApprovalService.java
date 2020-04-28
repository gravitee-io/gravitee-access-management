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
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ScopeApprovalService {

    Maybe<ScopeApproval> findById(String id);

    Single<Set<ScopeApproval>> findByDomainAndUser(String domain, String user);

    Single<Set<ScopeApproval>> findByDomainAndUserAndClient(String domain, String user, String client);

    Single<List<ScopeApproval>> saveConsent(String domain, Client client, List<ScopeApproval> approvals, User principal);

    Completable revokeByConsent(String domain, String userId, String consentId, User principal);

    Completable revokeByUser(String domain, String user, User principal);

    Completable revokeByUserAndClient(String domain, String user, String clientId, User principal);

    default Single<List<ScopeApproval>> saveConsent(String domain, Client client, List<ScopeApproval> approvals) {
        return saveConsent(domain, client, approvals, null);
    }

    default Completable revokeByConsent(String domain, String userId, String consentId) {
        return revokeByConsent(domain, userId, consentId, null);
    }

    default Completable revokeByUser(String domain, String userId) {
        return revokeByUser(domain, userId, null);
    }

    default Completable revokeByUserAndClient(String domain, String userId, String clientId) {
        return revokeByUserAndClient(domain, userId, clientId, null);
    }
}

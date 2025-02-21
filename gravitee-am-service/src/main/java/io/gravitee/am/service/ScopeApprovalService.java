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
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.token.RevokeToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.function.BiFunction;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ScopeApprovalService {

    Maybe<ScopeApproval> findById(Domain domain, String id);

    Flowable<ScopeApproval> findByDomainAndUser(Domain domain, UserId userId);

    Flowable<ScopeApproval> findByDomainAndUserAndClient(Domain domain, UserId userId, String client);

    Single<List<ScopeApproval>> saveConsent(Domain domain, Client client, List<ScopeApproval> approvals, User principal);

    Completable revokeByConsent(Domain domain, UserId userId, String consentId, BiFunction<Domain, RevokeToken, Completable> revokeTokenProcessor, User principal);

    Completable revokeByUser(Domain domain, UserId userId, BiFunction<Domain, RevokeToken, Completable> revokeTokenProcessor, User principal);

    Completable revokeByUserAndClient(Domain domain, UserId userId, String clientId, BiFunction<Domain, RevokeToken, Completable> revokeTokenProcessor, User principal);

}

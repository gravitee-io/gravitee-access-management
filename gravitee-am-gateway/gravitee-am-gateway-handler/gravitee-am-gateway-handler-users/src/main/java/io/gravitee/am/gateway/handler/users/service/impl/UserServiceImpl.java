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
package io.gravitee.am.gateway.handler.users.service.impl;

import io.gravitee.am.gateway.handler.users.service.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.exception.ScopeApprovalNotFoundException;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserServiceImpl implements UserService {

    @Autowired
    private io.gravitee.am.service.UserService userService;

    @Autowired
    private Domain domain;

    @Autowired
    private ScopeApprovalService scopeApprovalService;

    @Override
    public Maybe<User> findById(String id) {
        return userService.findById(id);
    }

    @Override
    public Single<Set<ScopeApproval>> consents(String userId) {
        return scopeApprovalService.findByDomainAndUser(domain.getId(), userId);
    }

    @Override
    public Single<Set<ScopeApproval>> consents(String userId, String clientId) {
        return scopeApprovalService.findByDomainAndUserAndClient(domain.getId(), userId, clientId);
    }

    @Override
    public Maybe<ScopeApproval> consent(String consentId) {
        return scopeApprovalService.findById(consentId).switchIfEmpty(Maybe.error(new ScopeApprovalNotFoundException(consentId)));
    }

    @Override
    public Completable revokeConsent(String userId, String consentId, io.gravitee.am.identityprovider.api.User principal) {
        return scopeApprovalService.revokeByConsent(domain.getId(), userId, consentId, principal);
    }

    @Override
    public Completable revokeConsents(String userId, io.gravitee.am.identityprovider.api.User principal) {
        return scopeApprovalService.revokeByUser(domain.getId(), userId, principal);
    }

    @Override
    public Completable revokeConsents(String userId, String clientId, io.gravitee.am.identityprovider.api.User principal) {
        return scopeApprovalService.revokeByUserAndClient(domain.getId(), userId, clientId, principal);
    }
}

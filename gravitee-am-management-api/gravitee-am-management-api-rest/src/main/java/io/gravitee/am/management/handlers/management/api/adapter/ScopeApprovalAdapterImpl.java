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
package io.gravitee.am.management.handlers.management.api.adapter;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.ApplicationEntity;
import io.gravitee.am.management.handlers.management.api.model.ScopeApprovalEntity;
import io.gravitee.am.management.handlers.management.api.model.ScopeEntity;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.UserId;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor

public class ScopeApprovalAdapterImpl implements ScopeApprovalAdapter {

    public static final String UNKNOWN_ID = "unknown-id";


    private final DomainService domainService;
    private final ScopeApprovalService scopeApprovalService;
    private final ApplicationService applicationService;
    private final ScopeService scopeService;
    private final UserService userService;


    public Single<List<ScopeApprovalEntity>> getUserConsents(String domain, String rawUserId, String clientId) {
        var userId = UserId.internal(rawUserId);
        return domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                // in management context, all users either have the internal ID, or aren't available anyway
                .flatMapSingle(d -> userService.findById(Reference.domain(d.getId()), userId))
                .flatMapPublisher(u -> {
                    if (clientId == null || clientId.isEmpty()) {
                        return scopeApprovalService.findByDomainAndUser(domain, u.getFullId());
                    }
                    return scopeApprovalService.findByDomainAndUserAndClient(domain, u.getFullId(), clientId);
                })
                .flatMapSingle(scopeApproval ->
                        getClient(scopeApproval.getDomain(), scopeApproval.getClientId())
                                .zipWith(getScope(scopeApproval.getDomain(), scopeApproval.getScope()), ((clientEntity, scopeEntity) -> {
                                    ScopeApprovalEntity scopeApprovalEntity = new ScopeApprovalEntity(scopeApproval);
                                    scopeApprovalEntity.setClientEntity(clientEntity);
                                    scopeApprovalEntity.setScopeEntity(scopeEntity);
                                    return scopeApprovalEntity;
                                })))
                .toList();
    }

    public Completable revokeUserConsents(String domain, String rawUserId, String clientId, User authenticatedUser) {
        var userId = UserId.internal(rawUserId);
        return domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapCompletable(__ -> {
                    if (clientId == null || clientId.isEmpty()) {
                        return scopeApprovalService.revokeByUser(domain, userId, authenticatedUser);
                    }
                    return scopeApprovalService.revokeByUserAndClient(domain, userId, clientId, authenticatedUser);
                });
    }

    private String getScopeBase(String scope) {
        return scope.contains(":") ? scope.substring(0, scope.indexOf(':')) : scope;
    }

    private Single<ScopeEntity> getScope(String domain, String scopeKey) {
        return scopeService.findByDomainAndKey(domain, scopeKey)
                .switchIfEmpty(scopeService.findByDomainAndKey(domain, getScopeBase(scopeKey)).map(entity -> {
                    // set the right scopeKey since the one returned by the service contains the scope definition without parameter
                    entity.setId(UNKNOWN_ID);
                    entity.setKey(scopeKey);
                    return entity;
                }))
                .map(ScopeEntity::new)
                .defaultIfEmpty(new ScopeEntity(UNKNOWN_ID, scopeKey, "unknown-scope-name", "unknown-scope-description"))
                .cache();
    }

    private Single<ApplicationEntity> getClient(String domain, String clientId) {
        return applicationService.findByDomainAndClientId(domain, clientId)
                .map(ApplicationEntity::new)
                .defaultIfEmpty(new ApplicationEntity(UNKNOWN_ID, clientId, "unknown-client-name"))
                .cache();
    }
}

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
package io.gravitee.am.repository.junit.gateway;

import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.gateway.api.ScopeApprovalRepository;
import io.gravitee.am.repository.junit.MemoryRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.util.Objects;
import java.util.UUID;

public class MemoryScopeApprovalRepository extends MemoryRepository<ScopeApproval, String> implements ScopeApprovalRepository {


    @Override
    public Flowable<ScopeApproval> findByDomainAndUserAndClient(String domain, UserId userId, String clientId) {
        return findMany(approval -> approval.getDomain().equals(domain) && approval.getClientId().equals(clientId) && userIdMatches(userId, approval));
    }

    boolean userIdMatches(UserId userId, ScopeApproval approval) {
        var approvalUserId = approval.getUserId();
        return Objects.equals(approvalUserId.id(), userId.id()) || (Objects.equals(approvalUserId.source(), userId.source()) && Objects.equals(approvalUserId.externalId(), userId.externalId()));
    }

    @Override
    public Flowable<ScopeApproval> findByDomainAndUser(String domain, UserId userId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Single<ScopeApproval> upsert(ScopeApproval scopeApproval) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Completable deleteByDomainAndScopeKey(String domain, String scope) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Completable deleteByDomainAndUserAndClient(String domain, UserId userId, String client) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Completable deleteByDomainAndUser(String domain, UserId userId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Completable purgeExpiredData() {
        return ScopeApprovalRepository.super.purgeExpiredData();
    }

    @Override
    protected String getId(ScopeApproval item) {
        return item.getId();
    }

    @Override
    protected String generateAndSetId(ScopeApproval item) {
        var id = UUID.randomUUID().toString();
        item.setId(id);
        return id;
    }
}

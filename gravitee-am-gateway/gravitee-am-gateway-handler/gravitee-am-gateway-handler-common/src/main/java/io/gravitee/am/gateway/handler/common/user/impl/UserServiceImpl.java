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
package io.gravitee.am.gateway.handler.common.user.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.repository.management.api.CommonUserRepository.UpdateActions;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserServiceImpl implements UserService {

    @Autowired
    private io.gravitee.am.service.UserService userService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<User> findById(String id) {
        return userService.findById(id);
    }

    @Override
    public Maybe<User> findByDomainAndExternalIdAndSource(String domain, String externalId, String source) {
        return userService.findByExternalIdAndSource(ReferenceType.DOMAIN, domain, externalId, source);
    }

    @Override
    public Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source) {
        return userService.findByDomainAndUsernameAndSource(domain, username, source);
    }

    @Override
    public Single<List<User>> findByDomainAndCriteria(String domain, FilterCriteria criteria) {
        return userService.search(ReferenceType.DOMAIN, domain, criteria).toList();
    }

    @Override
    public Single<User> create(User user) {
        return userService.create(user)
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED).user(user1)))
                .doOnError(err -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED).throwable(err)));
    }

    @Override
    public Single<User> update(User user, UpdateActions updateActions) {
        return userService.update(user, updateActions);
    }

    @Override
    public Single<User> enhance(User user) {
        return userService.enhance(user);
    }

    @Override
    public Single<User> addFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal) {
        return userService.upsertFactor(userId, enrolledFactor, principal);
    }

    @Override
    public Single<User> updateFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal) {
        return userService.upsertFactor(userId, enrolledFactor, principal);
    }
}

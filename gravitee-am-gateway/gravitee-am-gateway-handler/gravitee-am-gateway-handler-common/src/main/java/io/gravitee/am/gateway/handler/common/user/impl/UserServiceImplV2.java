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

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.repository.exceptions.RepositoryConnectionException;
import io.gravitee.am.repository.management.api.CommonUserRepository.UpdateActions;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserServiceImplV2 extends UserServiceImpl {

    private static final String SEPARATOR = ":";

    @Value("${resilience.enabled:false}")
    private boolean resilientMode = false;
    
    @Override
    public Maybe<User> findByDomainAndExternalIdAndSource(String domain, String externalId, String source) {
        // TODO create SubjectManager method to build gis from ext+src
        // userStore only if Domain v2 + resilient
        return userStore.getByInternalSub(generateInternalSubFrom(source, externalId))
                .switchIfEmpty(Maybe.defer(() -> userService.findByExternalIdAndSource(ReferenceType.DOMAIN, domain, externalId, source)
                        .onErrorResumeNext(error -> {
                            if (doesResilientModeEnabled(error)) {
                                // TODO log.Debug
                                return Maybe.empty();
                            }
                            return Maybe.error(error);
                        })));
    }

    @Override
    public Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source) {
        return userService.findByDomainAndUsernameAndSource(domain, username, source).onErrorResumeNext(error -> {
            if (doesResilientModeEnabled(error)) {
                // TODO log.Debug
                return Maybe.empty();
            }
            return Maybe.error(error);
        });
    }

    @Override
    public Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source, boolean includeLinkedIdentities) {
        return userService.findByUsernameAndSource(ReferenceType.DOMAIN, domain, username, source, includeLinkedIdentities).onErrorResumeNext(error -> {
            if (doesResilientModeEnabled(error)) {
                // TODO log.Debug
                return Maybe.empty();
            }
            return Maybe.error(error);
        });
    }

    @Override
    public Single<List<User>> findByDomainAndCriteria(String domain, FilterCriteria criteria) {
        return userService.search(ReferenceType.DOMAIN, domain, criteria).toList();
    }

    @Override
    public Single<User> create(User user) {
        return userService.create(user)
                .flatMap(persistedUser -> userStore.add(persistedUser).switchIfEmpty(Single.just(user)))
                .onErrorResumeNext(throwable -> cacheUserOnError(user, throwable));
    }

    @Override
    public Single<User> update(User user, UpdateActions updateActions) {
        return userService.update(user, updateActions)
                .flatMap(persistedUser -> userStore.add(persistedUser).switchIfEmpty(Single.just(user)))
                .onErrorResumeNext(throwable -> cacheUserOnError(user, throwable));
    }

    private Single<User> cacheUserOnError(User user, Throwable throwable) {
        if (doesResilientModeEnabled(throwable)) {
            // TODO log.info
            user.setId(StringUtils.hasLength(user.getId()) ? user.getId() : RandomString.generate());
            return userStore.add(user).switchIfEmpty(Single.just(user));
        }
        return Single.error(throwable);
    }

    private boolean doesResilientModeEnabled(Throwable throwable) {
        return resilientMode && (throwable instanceof RepositoryConnectionException || throwable.getCause() instanceof RepositoryConnectionException);
    }

    @Override
    public Single<User> addFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal) {
        return userService.upsertFactor(userId, enrolledFactor, principal);
    }

    @Override
    public Single<User> updateFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal) {
        return userService.upsertFactor(userId, enrolledFactor, principal);
    }


    private String generateInternalSubFrom(String src, String externalId) {
        return src + SEPARATOR + externalId;
    }
}

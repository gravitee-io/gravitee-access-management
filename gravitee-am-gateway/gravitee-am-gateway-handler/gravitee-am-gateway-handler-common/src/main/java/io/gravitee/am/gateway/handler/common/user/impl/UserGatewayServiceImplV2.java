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

import io.gravitee.am.business.user.CreateUserRule;
import io.gravitee.am.business.user.UpdateUserRule;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.repository.UserRepository.UpdateActions;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.exceptions.RepositoryConnectionException;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class UserGatewayServiceImplV2 extends UserGatewayServiceImpl {

    public static final String SEPARATOR = ":";

    @Value("${resilience.enabled:false}")
    private boolean resilientMode = false;
    
    @Override
    public Maybe<User> findByExternalIdAndSource(String externalId, String source) {
        return userStore.getByInternalSub(generateInternalSubFrom(source, externalId))
                .switchIfEmpty(Maybe.defer(() -> getUserRepository().findByExternalIdAndSource(domain.asReference(), externalId, source)
                        .onErrorResumeNext(error -> {
                            if (doesResilientModeEnabled(error)) {
                                log.debug("Resilient mode enabled for domain {}, ignore connection error during find user by externalId", domain);
                                return Maybe.empty();
                            }
                            return Maybe.error(error);
                        })));
    }

    @Override
    public Maybe<User> findByUsernameAndSource(String username, String source) {
        return getUserRepository().findByUsernameAndSource(domain.asReference(), username, source).onErrorResumeNext(error -> {
            if (doesResilientModeEnabled(error)) {
                log.debug("Resilient mode enabled for domain {}, ignore connection error during find user by username", domain);
                return Maybe.empty();
            }
            return Maybe.error(error);
        });
    }

    @Override
    public Maybe<User> findByUsernameAndSource(String username, String source, boolean includeLinkedIdentities) {
        return getUserRepository().findByUsernameAndSource(domain.asReference(), username, source, includeLinkedIdentities).onErrorResumeNext(error -> {
            if (doesResilientModeEnabled(error)) {
                log.debug("Resilient mode enabled for domain {}, ignore connection error during find user by username", domain);
                return Maybe.empty();
            }
            return Maybe.error(error);
        });
    }

    @Override
    public Single<List<User>> findByCriteria(FilterCriteria criteria) {
        return getUserRepository().search(domain.asReference(), criteria).toList();
    }

    @Override
    public Single<Page<User>> findByCriteria(FilterCriteria criteria, int page, int size) {
        return getUserRepository().search(domain.asReference(), criteria, page, size);
    }

    @Override
    public Single<User> create(User user) {
        return new CreateUserRule(getUserValidator(), getUserRepository()::create)
                .create(user)
                .flatMap(persistedUser -> userStore.add(persistedUser).switchIfEmpty(Single.just(user)))
                .onErrorResumeNext(throwable -> cacheUserOnError(user, throwable));
    }

    @Override
    public Single<User> update(User user, UpdateActions updateActions) {
        return new UpdateUserRule(getUserValidator(), getUserRepository()::update).update(user, updateActions)
                .flatMap(persistedUser -> userStore.add(persistedUser).switchIfEmpty(Single.just(user)))
                .onErrorResumeNext(throwable -> cacheUserOnError(user, throwable));
    }

    private Single<User> cacheUserOnError(User user, Throwable throwable) {
        if (doesResilientModeEnabled(throwable)) {
            log.debug("Resilient mode enabled, ignore connection error and keep in cache user with externalId '{}' and source '{}", user.getExternalId(), user.getSource());
            user.setId(StringUtils.hasLength(user.getId()) ? user.getId() : RandomString.generate());
            return userStore.add(user).switchIfEmpty(Single.just(user));
        }
        return Single.error(throwable);
    }

    private boolean doesResilientModeEnabled(Throwable throwable) {
        return resilientMode && (throwable instanceof RepositoryConnectionException || throwable.getCause() instanceof RepositoryConnectionException);
    }

    public static String generateInternalSubFrom(String src, String externalId) {
        return src + SEPARATOR + externalId;
    }
}

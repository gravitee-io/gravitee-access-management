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
package io.gravitee.am.repository.jdbc.oauth2.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcRefreshToken;
import io.gravitee.am.repository.jdbc.oauth2.api.spring.SpringRefreshTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava2Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcRefreshTokenRepository extends AbstractJdbcRepository implements RefreshTokenRepository {

    @Autowired
    private SpringRefreshTokenRepository refreshTokenRepository;

    protected RefreshToken toEntity(JdbcRefreshToken entity) {
        return mapper.map(entity, RefreshToken.class);
    }

    protected JdbcRefreshToken toJdbcEntity(RefreshToken entity) {
        return mapper.map(entity, JdbcRefreshToken.class);
    }

    @Override
    public Maybe<RefreshToken> findByToken(String token) {
        LOGGER.debug("findByToken({token})", token);
        return refreshTokenRepository.findByToken(token, LocalDateTime.now())
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve RefreshToken", error));
    }

    @Override
    public Single<RefreshToken> create(RefreshToken refreshToken) {
        refreshToken.setId(refreshToken.getId() == null ? RandomString.generate() : refreshToken.getId());
        LOGGER.debug("Create refreshToken with id {}", refreshToken.getId());

        Mono<Integer> action = dbClient.insert()
                .into(JdbcRefreshToken.class)
                .using(toJdbcEntity(refreshToken))
                .fetch().rowsUpdated();

        return monoToSingle(action)
                .flatMap((i) -> refreshTokenRepository.findById(refreshToken.getId()).map(this::toEntity).toSingle())
                .doOnError((error) -> LOGGER.error("Unable to create refreshToken with id {}", refreshToken.getId(), error));
    }

    @Override
    public Completable bulkWrite(List<RefreshToken> refreshTokens) {
        return Flowable.fromIterable(refreshTokens)
                .flatMap(refreshToken -> create(refreshToken).toFlowable())
                .ignoreElements()
                .doOnError(error -> LOGGER.error("Unable to bulk load refresh tokens", error));
    }

    @Override
    public Completable delete(String token) {
        LOGGER.debug("delete({})", token);
        return monoToCompletable(dbClient.delete()
                .from(JdbcRefreshToken.class)
                .matching(from(where("token").is(token)))
                .fetch().rowsUpdated())
                .doOnError(error -> LOGGER.error("Unable to delete RefreshToken", error));
    }

    @Override
    public Completable deleteByUserId(String userId) {
        LOGGER.debug("deleteByUserId({})", userId);
        return monoToCompletable(dbClient.delete()
                .from(JdbcRefreshToken.class)
                .matching(from(where("subject").is(userId)))
                .then())
                .doOnError(error -> LOGGER.error("Unable to delete refresh token with subject {}", userId, error));
    }

    @Override
    public Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, String userId) {
        LOGGER.debug("deleteByDomainIdClientIdAndUserId({},{},{})", domainId, clientId, userId);
        return monoToCompletable(dbClient.delete()
                .from(JdbcRefreshToken.class)
                .matching(from(where("subject").is(userId)
                                .and(where("domain").is(domainId))
                                .and(where("client").is(clientId))))
                .then())
                .doOnError(error -> LOGGER.error("Unable to delete refresh token with domain {}, client {} and subject {}",
                        domainId, clientId, userId, error));
    }

    @Override
    public Completable deleteByDomainIdAndUserId(String domainId, String userId) {
        LOGGER.debug("deleteByDomainIdAndUserId({},{})", domainId, userId);
        return monoToCompletable(dbClient.delete()
                .from(JdbcRefreshToken.class)
                .matching(from(where("subject").is(userId)
                                .and(where("domain").is(domainId))))
                .then())
                .doOnError(error -> LOGGER.error("Unable to delete refresh token with domain {} and subject {}",
                        domainId, userId, error));
    }
}

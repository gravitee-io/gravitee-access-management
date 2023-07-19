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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

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
        return refreshTokenRepository.findByToken(token, LocalDateTime.now(UTC))
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve RefreshToken", error));
    }

    @Override
    public Single<RefreshToken> create(RefreshToken refreshToken) {
        refreshToken.setId(refreshToken.getId() == null ? RandomString.generate() : refreshToken.getId());
        LOGGER.debug("Create refreshToken with id {}", refreshToken.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(refreshToken))).map(this::toEntity)
                .doOnError((error) -> LOGGER.error("Unable to create refreshToken with id {}", refreshToken.getId(), error));
    }

    @Override
    public Completable delete(String token) {
        LOGGER.debug("delete({})", token);
        return monoToCompletable(getTemplate().delete(JdbcRefreshToken.class)
                .matching(Query.query(where("token").is(token))).all())
                .doOnError(error -> LOGGER.error("Unable to delete RefreshToken", error));
    }

    @Override
    public Completable deleteByUserId(String userId) {
        LOGGER.debug("deleteByUserId({})", userId);
        return monoToCompletable(getTemplate().delete(JdbcRefreshToken.class)
                .matching(Query.query(where("subject").is(userId)))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete refresh token with subject {}", userId, error));
    }

    @Override
    public Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, String userId) {
        LOGGER.debug("deleteByDomainIdClientIdAndUserId({},{},{})", domainId, clientId, userId);
        return monoToCompletable(getTemplate().delete(JdbcRefreshToken.class)
                .matching(Query.query(where("subject").is(userId)
                        .and(where("domain").is(domainId))
                        .and(where("client").is(clientId)))).all())
                .doOnError(error -> LOGGER.error("Unable to delete refresh token with domain {}, client {} and subject {}",
                        domainId, clientId, userId, error));
    }

    @Override
    public Completable deleteByDomainIdAndUserId(String domainId, String userId) {
        LOGGER.debug("deleteByDomainIdAndUserId({},{})", domainId, userId);
        return monoToCompletable(getTemplate().delete(JdbcRefreshToken.class)
                .matching(Query.query(where("subject").is(userId)
                        .and(where("domain").is(domainId))))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete refresh token with domain {} and subject {}",
                        domainId, userId, error));
    }

    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(JdbcRefreshToken.class).matching(Query.query(where("expire_at").lessThan(now))).all())
                .doOnError(error -> LOGGER.error("Unable to purge refresh tokens", error));
    }
}
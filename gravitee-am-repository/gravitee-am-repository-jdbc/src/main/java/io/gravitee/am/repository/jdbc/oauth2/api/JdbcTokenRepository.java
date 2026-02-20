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
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcBaseToken;
import io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcToken;
import io.gravitee.am.repository.jdbc.oauth2.api.spring.SpringTokenRepository;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.am.repository.oauth2.model.Token;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Date;

import static io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcBaseToken.SUBJECT;
import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

@Repository
public class JdbcTokenRepository extends AbstractJdbcRepository implements TokenRepository {

    @Autowired
    private SpringTokenRepository spring;

    @Override
    public Maybe<RefreshToken> findRefreshTokenByJti(String jti) {
        LOGGER.debug("findRefreshTokenByJti({})", jti);
        return spring.findNotExpiredRefreshTokenByJti(jti, LocalDateTime.now(UTC))
                .map(this::toRefreshToken)
                .doOnError(error -> LOGGER.error("Unable to retrieve RefreshToken", error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<RefreshToken> create(RefreshToken refreshToken) {
        refreshToken.setId(refreshToken.getId() == null ? RandomString.generate() : refreshToken.getId());
        LOGGER.debug("Create refreshToken with id {}", refreshToken.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(refreshToken)))
                .map(this::toRefreshToken)
                .doOnError(error -> LOGGER.error("Unable to create refreshToken with id {}", refreshToken.getId(), error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AccessToken> findAccessTokenByJti(String jti) {
        LOGGER.debug("findAccessTokenByJti({})", jti);
        return spring.findNotExpiredAccessTokenByJti(jti, LocalDateTime.now(UTC))
                .map(this::toAccessToken)
                .doOnError(error -> LOGGER.error("Unable to retrieve AccessToken", error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AccessToken> create(AccessToken accessToken) {
        accessToken.setId(accessToken.getId() == null ? RandomString.generate() : accessToken.getId());
        LOGGER.debug("Create accessToken with id {}", accessToken.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(accessToken)))
                .map(this::toAccessToken)
                .doOnError(error -> LOGGER.error("Unable to create accessToken with id {}", accessToken.getId(), error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByJti(String jti) {
        LOGGER.debug("deleteByJti({})", jti);
        return monoToCompletable(getTemplate().delete(JdbcToken.class)
                .matching(Query.query(where("token").is(jti)))
                .all());
    }

    @Override
    public Observable<AccessToken> findAccessTokenByAuthorizationCode(String authorizationCode) {
        LOGGER.debug("findAccessTokenByAuthorizationCode({})", authorizationCode);
        return spring.findNotExpiredAccessTokenByAuthorizationCode(authorizationCode, LocalDateTime.now(UTC))
                .map(this::toAccessToken)
                .toObservable()
                .doOnError(error -> LOGGER.error("Unable to retrieve access tokens with authorization code {}",
                        authorizationCode, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUserId(String userId) {
        LOGGER.debug("deleteByUserId({})", userId);
        return monoToCompletable(getTemplate().delete(JdbcToken.class)
                .matching(Query.query(where(SUBJECT).is(userId)))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete access tokens with subject {}",
                        userId, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, UserId userId) {
        LOGGER.debug("deleteByDomainIdClientIdAndUserId({},{},{})", domainId, clientId, userId);
        return monoToCompletable(getTemplate().delete(JdbcToken.class)
                .matching(Query.query(
                        where(SUBJECT).is(userId.id())
                                .and(where("domain").is(domainId))
                                .and(where("client").is(clientId))))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete access token with domain {}, client {} and subject {}",
                        domainId, clientId, userId, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdAndUserId(String domainId, UserId userId) {
        LOGGER.debug("deleteByDomainIdAndUserId({},{})", domainId, userId);
        return monoToCompletable(getTemplate().delete(JdbcToken.class)
                .matching(Query.query(
                        where(SUBJECT).is(userId.id())
                                .and(where("domain").is(domainId))))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete access tokens with domain {} and subject {}",
                        domainId, userId, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdAndClientId(String domainId, String clientId) {
        LOGGER.debug("deleteByDomainIdClientId({},{})", domainId, clientId);
        return monoToCompletable(getTemplate().delete(JdbcToken.class)
                .matching(Query.query(where("domain").is(domainId)
                        .and(where("client").is(clientId))))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete access token with domain {}, client {}",
                        domainId, clientId, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(JdbcToken.class)
                .matching(Query.query(where("expire_at").lessThan(now))).all())
                .doOnError(error -> LOGGER.error("Unable to purge access tokens", error))
                .observeOn(Schedulers.computation());
    }

    private AccessToken toAccessToken(JdbcToken entity) {
        var result = new AccessToken();
        result.setAuthorizationCode(entity.getAuthorizationCode());
        result.setRefreshToken(entity.getRefreshToken());
        result.setToken(entity.getToken());
        result.setId(entity.getId());
        result.setClient(entity.getClient());
        result.setDomain(entity.getDomain());
        result.setSubject(entity.getSubject());
        if (entity.getCreatedAt() != null) {
            result.setCreatedAt(Date.from(entity.getCreatedAt().atZone(UTC).toInstant()));
        }
        if (entity.getExpireAt() != null) {
            result.setExpireAt(Date.from(entity.getExpireAt().atZone(UTC).toInstant()));
        }
        return result;
    }

    private RefreshToken toRefreshToken(JdbcToken entity) {
        var result = new RefreshToken();
        result.setToken(entity.getToken());
        result.setId(entity.getId());
        result.setClient(entity.getClient());
        result.setDomain(entity.getDomain());
        result.setSubject(entity.getSubject());
        if (entity.getCreatedAt() != null) {
            result.setCreatedAt(Date.from(entity.getCreatedAt().atZone(UTC).toInstant()));
        }
        if (entity.getExpireAt() != null) {
            result.setExpireAt(Date.from(entity.getExpireAt().atZone(UTC).toInstant()));
        }
        return result;
    }

    private JdbcToken toJdbcEntity(Token token, TokenType tokenType) {
        var result = new JdbcToken();
        result.setType(tokenType.name());

        result.setId(token.getId());
        result.setToken(token.getToken());
        result.setClient(token.getClient());
        result.setDomain(token.getDomain());
        result.setSubject(token.getSubject());
        if (token.getCreatedAt() != null) {
            result.setCreatedAt(LocalDateTime.ofInstant(token.getCreatedAt().toInstant(), UTC));
        }
        if (token.getExpireAt() != null) {
            result.setExpireAt(LocalDateTime.ofInstant(token.getExpireAt().toInstant(), UTC));
        }
        return result;
    }

    private JdbcToken toJdbcEntity(AccessToken token) {
        var result = toJdbcEntity(token, TokenType.ACCESS_TOKEN);
        result.setAuthorizationCode(token.getAuthorizationCode());
        result.setRefreshToken(token.getRefreshToken());
        return result;
    }

    private JdbcToken toJdbcEntity(RefreshToken token) {
        return toJdbcEntity(token, TokenType.REFRESH_TOKEN);
    }
}

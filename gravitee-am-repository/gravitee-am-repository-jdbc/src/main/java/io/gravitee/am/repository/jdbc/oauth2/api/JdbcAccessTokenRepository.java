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
import io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcAccessToken;
import io.gravitee.am.repository.jdbc.oauth2.api.spring.SpringAccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Date;

import static java.time.ZoneOffset.UTC;
import static java.time.ZoneOffset.ofHours;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcAccessTokenRepository extends AbstractJdbcRepository implements AccessTokenRepository {
    @Autowired
    private SpringAccessTokenRepository accessTokenRepository;

    protected AccessToken toEntity(JdbcAccessToken entity) {
        var result = new AccessToken();
        result.setAuthorizationCode(entity.getAuthorizationCode());
        result.setToken(entity.getToken());
        result.setRefreshToken(entity.getRefreshToken());
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

    protected JdbcAccessToken toJdbcEntity(AccessToken entity) {
        var result = new JdbcAccessToken();
        result.setAuthorizationCode(entity.getAuthorizationCode());
        result.setToken(entity.getToken());
        result.setRefreshToken(entity.getRefreshToken());
        result.setId(entity.getId());
        result.setClient(entity.getClient());
        result.setDomain(entity.getDomain());
        result.setSubject(entity.getSubject());
        if (entity.getCreatedAt() != null) {
            result.setCreatedAt(LocalDateTime.ofInstant(entity.getCreatedAt().toInstant(), UTC));
        }
        if (entity.getExpireAt() != null) {
            result.setExpireAt(LocalDateTime.ofInstant(entity.getExpireAt().toInstant(), UTC));
        }
        return result;
    }

    @Override
    public Maybe<AccessToken> findByToken(String token) {
        LOGGER.debug("findByToken({})", token);
        return accessTokenRepository.findByToken(token, LocalDateTime.now(UTC))
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve AccessToken", error));
    }

    @Override
    public Single<AccessToken> create(AccessToken accessToken) {
        accessToken.setId(accessToken.getId() == null ? RandomString.generate() : accessToken.getId());
        LOGGER.debug("Create accessToken with id {}", accessToken.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(accessToken))).map(this::toEntity)
                .doOnError((error) -> LOGGER.error("Unable to create accessToken with id {}", accessToken.getId(), error));
    }

    @Override
    public Completable delete(String token) {
        LOGGER.debug("delete({})", token);
        return Completable.fromMaybe(findByToken(token).flatMap(accessToken ->
            monoToMaybe(getTemplate().delete(Query.query(where("token").is(token)), JdbcAccessToken.class)
                    .map(i -> accessToken)
        ).doOnError(error -> LOGGER.error("Unable to delete AccessToken", error))));
    }

    @Override
    public Observable<AccessToken> findByClientIdAndSubject(String clientId, String subject) {
        LOGGER.debug("findByClientIdAndSubject({}, {})", clientId, subject);
        return accessTokenRepository.findByClientIdAndSubject(clientId, subject, LocalDateTime.now(UTC))
                .map(this::toEntity)
                .toObservable()
                .doOnError(error -> LOGGER.error("Unable to retrieve access tokens with client {} and subject {}",
                        clientId, subject, error));
    }

    @Override
    public Observable<AccessToken> findByClientId(String clientId) {
        LOGGER.debug("findByClientId({})", clientId);
        return accessTokenRepository.findByClientId(clientId, LocalDateTime.now(UTC))
                .map(this::toEntity)
                .toObservable()
                .doOnError(error -> LOGGER.error("Unable to retrieve access tokens with client {}",
                        clientId, error));
    }

    @Override
    public Observable<AccessToken> findByAuthorizationCode(String authorizationCode) {
        LOGGER.debug("findByAuthorizationCode({})", authorizationCode);
        return accessTokenRepository.findByAuthorizationCode(authorizationCode, LocalDateTime.now(UTC))
                .map(this::toEntity)
                .toObservable()
                .doOnError(error -> LOGGER.error("Unable to retrieve access tokens with authorization code {}",
                        authorizationCode, error));
    }

    @Override
    public Single<Long> countByClientId(String clientId) {
        return accessTokenRepository.countByClientId(clientId, LocalDateTime.now(UTC));
    }

    @Override
    public Completable deleteByUserId(String userId) {
        LOGGER.debug("deleteByUserId({})", userId);
        return monoToCompletable(getTemplate().delete(JdbcAccessToken.class)
                .matching(Query.query(where("subject").is(userId)))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete access tokens with subject {}",
                userId, error));
    }

    @Override
    public Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, String userId) {
        LOGGER.debug("deleteByDomainIdClientIdAndUserId({},{},{})", domainId, clientId, userId);
        return monoToCompletable(getTemplate().delete(JdbcAccessToken.class)
                .matching(Query.query(
                        where("subject").is(userId)
                                .and(where("domain").is(domainId))
                                .and(where("client").is(clientId))))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete access token with domain {}, client {} and subject {}",
                        domainId, clientId, userId, error));
    }

    @Override
    public Completable deleteByDomainIdAndUserId(String domainId, String userId) {
        LOGGER.debug("deleteByDomainIdAndUserId({},{})", domainId, userId);
        return monoToCompletable(getTemplate().delete(JdbcAccessToken.class)
                .matching(Query.query(
                        where("subject").is(userId)
                                .and(where("domain").is(domainId))))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete access tokens with domain {} and subject {}",
                        domainId, userId, error));
    }

    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(JdbcAccessToken.class)
                .matching(Query.query(where("expire_at").lessThan(now))).all())
                .doOnError(error -> LOGGER.error("Unable to purge access tokens", error));
    }
}
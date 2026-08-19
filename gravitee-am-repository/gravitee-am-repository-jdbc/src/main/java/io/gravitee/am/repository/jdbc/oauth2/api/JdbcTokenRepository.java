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
import io.gravitee.am.repository.jdbc.common.RetryOnConcurrencyFailure;
import io.gravitee.am.repository.jdbc.common.RetryOnConcurrencyFailureConfiguration;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcBaseToken.SUBJECT;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.toSet;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

@Repository
public class JdbcTokenRepository extends AbstractJdbcRepository implements TokenRepository, InitializingBean {

    @Autowired
    private SpringTokenRepository spring;

    @Autowired
    private RetryOnConcurrencyFailureConfiguration retryOnConcurrencyFailureConfiguration;

    private static final String ACCESS_TOKEN_PREFIX = "at_";
    private static final String REFRESH_TOKEN_PREFIX = "rt_";

    private static final List<FieldSpec<JdbcToken, ?>> TOKEN_FIELDS = List.of(
            new FieldSpec<>("id", JdbcToken::getId, String.class),
            new FieldSpec<>("domain", JdbcToken::getDomain, String.class),
            new FieldSpec<>("client", JdbcToken::getClient, String.class),
            new FieldSpec<>(SUBJECT, JdbcToken::getSubject, String.class),
            new FieldSpec<>("token", JdbcToken::getToken, String.class),
            new FieldSpec<>("created_at", JdbcToken::getCreatedAt, LocalDateTime.class),
            new FieldSpec<>("expire_at", JdbcToken::getExpireAt, LocalDateTime.class),
            new FieldSpec<>("authorization_code", JdbcToken::getAuthorizationCode, String.class),
            new FieldSpec<>("refresh_token", JdbcToken::getRefreshToken, String.class),
            new FieldSpec<>("type", JdbcToken::getType, String.class),
            new FieldSpec<>("parent_jti_1", JdbcToken::getParentJti1, String.class),
            new FieldSpec<>("parent_jti_2", JdbcToken::getParentJti2, String.class),
            new FieldSpec<>("jkt", JdbcToken::getJkt, String.class));

    private static final List<FieldSpec<JdbcToken, ?>> ACCESS_TOKEN_FIELDS = prefixed(ACCESS_TOKEN_PREFIX);
    private static final List<FieldSpec<JdbcToken, ?>> REFRESH_TOKEN_FIELDS = prefixed(REFRESH_TOKEN_PREFIX);

    private String batchInsertStatement;

    @Override
    public void afterPropertiesSet() {
        this.batchInsertStatement = createBatchInsertStatement();
    }

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
    public Completable create(AccessToken accessToken, RefreshToken refreshToken) {
        if (refreshToken == null) {
            return create(accessToken).ignoreElement();
        }
        accessToken.setId(accessToken.getId() == null ? RandomString.generate() : accessToken.getId());
        refreshToken.setId(refreshToken.getId() == null ? RandomString.generate() : refreshToken.getId());
        LOGGER.debug("Create accessToken with id {} and refreshToken with id {}", accessToken.getId(), refreshToken.getId());

        DatabaseClient.GenericExecuteSpec spec = getTemplate().getDatabaseClient().sql(batchInsertStatement);
        spec = addQuotedFields(spec, ACCESS_TOKEN_FIELDS, toJdbcEntity(accessToken));
        spec = addQuotedFields(spec, REFRESH_TOKEN_FIELDS, toJdbcEntity(refreshToken));

        return monoToCompletable(spec.fetch().rowsUpdated())
                .doOnError(error -> LOGGER.error("Unable to create accessToken with id {} and refreshToken with id {}",
                        accessToken.getId(), refreshToken.getId(), error))
                .observeOn(Schedulers.computation());
    }

    private String createBatchInsertStatement() {
        String columns = TOKEN_FIELDS.stream()
                .map(FieldSpec::columnName)
                .map(SqlIdentifier::quoted)
                .map(databaseDialectHelper::toSql)
                .collect(Collectors.joining(","));
        return "INSERT INTO tokens (" + columns + ") VALUES ("
                + bindMarkers(ACCESS_TOKEN_FIELDS) + "),("
                + bindMarkers(REFRESH_TOKEN_FIELDS) + ")";
    }

    private static String bindMarkers(List<FieldSpec<JdbcToken, ?>> fields) {
        return fields.stream().map(field -> ":" + field.columnName()).collect(Collectors.joining(","));
    }

    private static List<FieldSpec<JdbcToken, ?>> prefixed(String prefix) {
        return TOKEN_FIELDS.stream().<FieldSpec<JdbcToken, ?>>map(field -> withPrefix(prefix, field)).toList();
    }

    private static <T> FieldSpec<JdbcToken, T> withPrefix(String prefix, FieldSpec<JdbcToken, T> field) {
        return new FieldSpec<>(prefix + field.columnName(), field.valueGetter(), field.valueType());
    }

    @Override
    public Completable deleteByJti(String jti) {
        LOGGER.debug("deleteByJti({})", jti);
        String query = databaseDialectHelper.recursiveTokenDeleteQuery("token = :jti");
        return executeDelete("deleteByJti", getTemplate().getDatabaseClient().sql(query)
                .bind("jti", jti))
                .doOnError(error -> LOGGER.error("Unable to delete tokens with parent jti {}", jti, error))
                .observeOn(Schedulers.computation());
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
        String query = databaseDialectHelper.recursiveTokenDeleteQuery(SUBJECT + " = :userId");
        return executeDelete("deleteByUserId", getTemplate().getDatabaseClient().sql(query)
                .bind("userId", userId))
                .doOnError(error -> LOGGER.error("Unable to delete tokens with subject {}", userId, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, UserId userId) {
        LOGGER.debug("deleteByDomainIdClientIdAndUserId({},{},{})", domainId, clientId, userId);
        String query = databaseDialectHelper.recursiveTokenDeleteQuery("domain = :domainId AND client = :clientId AND " + SUBJECT + " = :userId");
        return executeDelete("deleteByDomainIdClientIdAndUserId", getTemplate().getDatabaseClient().sql(query)
                .bind("domainId", domainId)
                .bind("clientId", clientId)
                .bind("userId", userId.id()))
                .doOnError(error -> LOGGER.error("Unable to delete access token with domain {}, client {} and subject {}",
                                domainId, clientId, userId, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdAndUserId(String domainId, UserId userId) {
        LOGGER.debug("deleteByDomainIdAndUserId({},{})", domainId, userId);
        String query = databaseDialectHelper.recursiveTokenDeleteQuery("domain = :domainId AND " + SUBJECT + " = :userId");
        return executeDelete("deleteByDomainIdAndUserId", getTemplate().getDatabaseClient().sql(query)
                .bind("domainId", domainId)
                .bind("userId", userId.id()))
                .doOnError(error -> LOGGER.error("Unable to delete access tokens with domain {} and subject {}",
                        domainId, userId, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdAndClientId(String domainId, String clientId) {
        LOGGER.debug("deleteByDomainIdClientId({},{})", domainId, clientId);
        String query = databaseDialectHelper.recursiveTokenDeleteQuery("domain = :domainId AND client = :clientId");
        return executeDelete("deleteByDomainIdAndClientId", getTemplate().getDatabaseClient().sql(query)
                .bind("domainId", domainId)
                .bind("clientId", clientId))
                .doOnError(error -> LOGGER.error("Unable to delete access token with domain {}, client {}",
                        domainId, clientId, error))
                .observeOn(Schedulers.computation());
    }

    private Completable executeDelete(String operation, DatabaseClient.GenericExecuteSpec spec) {
        return monoToCompletable(spec.fetch().rowsUpdated())
                .retryWhen(new RetryOnConcurrencyFailure(operation, retryOnConcurrencyFailureConfiguration));
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
        updateToken(result, entity);
        return result;
    }

    private RefreshToken toRefreshToken(JdbcToken entity) {
        var result = new RefreshToken();
        updateToken(result, entity);
        return result;
    }

    private void updateToken(Token result, JdbcToken entity) {
        result.setToken(entity.getToken());
        result.setId(entity.getId());
        result.setClient(entity.getClient());
        result.setDomain(entity.getDomain());
        result.setSubject(entity.getSubject());
        result.setJkt(entity.getJkt());
        result.setAllParentJtis(new HashSet<>()); // parentIds are only needed to perform RECURSIVE delete
        if (entity.getCreatedAt() != null) {
            result.setCreatedAt(Date.from(entity.getCreatedAt().atZone(UTC).toInstant()));
        }
        if (entity.getExpireAt() != null) {
            result.setExpireAt(Date.from(entity.getExpireAt().atZone(UTC).toInstant()));
        }
    }

    private JdbcToken toJdbcEntity(Token token, TokenType tokenType) {
        var result = new JdbcToken();
        result.setType(tokenType.name());

        result.setId(token.getId());
        result.setToken(token.getToken());
        result.setClient(token.getClient());
        result.setDomain(token.getDomain());
        result.setSubject(token.getSubject());
        result.setJkt(token.getJkt());

        Iterator<String> it = (token.getAllParentJtis() == null ? Stream.<String>empty() : token.getAllParentJtis().stream())
                .filter(jti -> !jti.equals(result.getToken()))
                .iterator();

        // adds only two as for JBDC hierarchy is maintained
        if(it.hasNext()) {
            result.setParentJti1(it.next());
        }
        if(it.hasNext()) {
            result.setParentJti2(it.next());
        }
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

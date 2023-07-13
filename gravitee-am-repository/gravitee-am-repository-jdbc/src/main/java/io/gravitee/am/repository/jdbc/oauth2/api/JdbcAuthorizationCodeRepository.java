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
import io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcAuthorizationCode;
import io.gravitee.am.repository.jdbc.oauth2.api.spring.SpringAuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcAuthorizationCodeRepository extends AbstractJdbcRepository implements AuthorizationCodeRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_CLIENT_ID = "client_id";
    public static final String COL_CODE = "code";
    public static final String COL_REDIRECT_URI = "redirect_uri";
    public static final String COL_SUBJECT = "subject";
    public static final String COL_TRANSACTION_ID = "transaction_id";
    public static final String COL_CONTEXT_VERSION = "context_version";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_EXPIRE_AT = "expire_at";
    public static final String COL_SCOPES = "scopes";
    public static final String COL_REQUEST_PARAMETERS = "request_parameters";
    private static final List<String> columns = List.of(
            COL_ID,
            COL_CLIENT_ID,
            COL_CODE,
            COL_REDIRECT_URI,
            COL_SUBJECT,
            COL_TRANSACTION_ID,
            COL_CONTEXT_VERSION,
            COL_CREATED_AT,
            COL_EXPIRE_AT,
            COL_SCOPES,
            COL_REQUEST_PARAMETERS
    );

    private String INSERT_STATEMENT;

    @Autowired
    private SpringAuthorizationCodeRepository authorizationCodeRepository;

    protected AuthorizationCode toEntity(JdbcAuthorizationCode entity) {
        return mapper.map(entity, AuthorizationCode.class);
    }

    protected JdbcAuthorizationCode toJdbcEntity(AuthorizationCode entity) {
        return mapper.map(entity, JdbcAuthorizationCode.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("authorization_codes", columns);
    }

    @Override
    public Single<AuthorizationCode> create(AuthorizationCode authorizationCode) {
        authorizationCode.setId(authorizationCode.getId() == null ? RandomString.generate() : authorizationCode.getId());
        LOGGER.debug("Create authorizationCode with id {} and code {}", authorizationCode.getId(), authorizationCode.getCode());

        DatabaseClient.GenericExecuteSpec insertSpec = template.getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec, COL_ID, authorizationCode.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CLIENT_ID, authorizationCode.getClientId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CODE, authorizationCode.getCode(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REDIRECT_URI, authorizationCode.getRedirectUri(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_SUBJECT, authorizationCode.getSubject(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_TRANSACTION_ID, authorizationCode.getTransactionId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CONTEXT_VERSION, authorizationCode.getContextVersion(), int.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(authorizationCode.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_EXPIRE_AT, dateConverter.convertTo(authorizationCode.getExpireAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, COL_SCOPES, authorizationCode.getScopes());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, COL_REQUEST_PARAMETERS, authorizationCode.getRequestParameters());

        Mono<Long> insertAction = insertSpec.fetch().rowsUpdated();

        return monoToSingle(insertAction).flatMap((i) -> authorizationCodeRepository.findById(authorizationCode.getId()).map(this::toEntity).toSingle())
                .doOnError((error) -> LOGGER.error("Unable to create authorizationCode with id {}", authorizationCode.getId(), error));
    }

    @Override
    public Maybe<AuthorizationCode> delete(String id) {
        LOGGER.debug("delete({})", id);
        return authorizationCodeRepository.findById(id).map(this::toEntity)
                .flatMap(authCode ->
                    monoToMaybe(template.delete(JdbcAuthorizationCode.class)
                            .matching(Query.query(where(COL_ID).is(id))).all())
                            .map(i -> authCode));
    }

    @Override
    public Maybe<AuthorizationCode> findByCode(String code) {
        LOGGER.debug("findByCode({})", code);
        return authorizationCodeRepository.findByCode(code, LocalDateTime.now(UTC))
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve AuthorizationCode with code {}", code));
    }

    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(template.delete(JdbcAuthorizationCode.class)
                .matching(Query.query(where(COL_EXPIRE_AT).lessThan(now))).all())
                .doOnError(error -> LOGGER.error("Unable to purge authorization tokens", error));
    }
}

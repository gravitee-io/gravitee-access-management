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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcApplication;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAuthenticationFlowContext;
import io.gravitee.am.repository.management.api.AuthenticationFlowContextRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.from;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcAuthenticationFlowContextRepository extends AbstractJdbcRepository implements AuthenticationFlowContextRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_DATA = "data";
    public static final String COL_VERSION = "version";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_EXPIRE_AT = "expire_at";
    public static final String COL_TRANSACTION_ID = "transaction_id";

    private static final List<String> columns = List.of(COL_ID,
            COL_TRANSACTION_ID,
            COL_VERSION,
            COL_CREATED_AT,
            COL_EXPIRE_AT,
            COL_DATA);

    private String INSERT_STATEMENT;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("auth_flow_ctx", columns);
    }

    protected AuthenticationFlowContext toEntity(JdbcAuthenticationFlowContext entity) {
        return mapper.map(entity, AuthenticationFlowContext.class);
    }

    protected JdbcAuthenticationFlowContext toJdbcEntity(AuthenticationFlowContext entity) {
        return mapper.map(entity, JdbcAuthenticationFlowContext.class);
    }

    @Override
    public Maybe<AuthenticationFlowContext> findById(String id) {
        LOGGER.debug("findById({})", id);
        if (id == null) {
            return Maybe.empty();
        }
        return monoToMaybe(getTemplate().select(JdbcAuthenticationFlowContext.class)
                .matching(Query.query(where(COL_ID).is(id))).one())
                .map(this::toEntity);
    }

    @Override
    public Maybe<AuthenticationFlowContext> findLastByTransactionId(String transactionId) {
        LOGGER.debug("findLastByTransactionId({})", transactionId);
        if (transactionId == null) {
            return Maybe.empty();
        }
        
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(JdbcAuthenticationFlowContext.class)
                .matching(Query.query(where(COL_TRANSACTION_ID).is(transactionId).and(where(COL_EXPIRE_AT).greaterThan(now)))
                        .sort(Sort.by(COL_VERSION).descending())
                ).first())
                .map(this::toEntity);
    }

    @Override
    public Flowable<AuthenticationFlowContext> findByTransactionId(String transactionId) {
        LOGGER.debug("findByTransactionId({})", transactionId);
        if (transactionId == null) {
            return Flowable.empty();
        }

        LocalDateTime now = LocalDateTime.now(UTC);
        return fluxToFlowable(getTemplate().select(JdbcAuthenticationFlowContext.class)
                .matching(Query.query(where(COL_TRANSACTION_ID).is(transactionId).and(where(COL_EXPIRE_AT).greaterThan(now)))
                        .sort(Sort.by(COL_VERSION).descending())).all())
                .map(this::toEntity);
    }

    @Override
    public Single<AuthenticationFlowContext> create(AuthenticationFlowContext context) {
       String id = context.getTransactionId() + "-" + context.getVersion();
        LOGGER.debug("Create AuthenticationContext with id {}", id);

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec,COL_ID, id, String.class);
        insertSpec = addQuotedField(insertSpec,COL_TRANSACTION_ID, context.getTransactionId(), String.class);
        insertSpec = addQuotedField(insertSpec,COL_VERSION, context.getVersion(), Integer.class);
        insertSpec = addQuotedField(insertSpec,COL_CREATED_AT, dateConverter.convertTo(context.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec,COL_EXPIRE_AT, dateConverter.convertTo(context.getExpireAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec,COL_DATA, context.getData());

        Mono<Long> insertAction = insertSpec.fetch().rowsUpdated();
        return monoToSingle(insertAction)
                .flatMap((i) -> this.findById(id).toSingle());
    }

    @Override
    public Completable delete(String transactionId) {
        LOGGER.debug("delete({})", transactionId);
        return monoToCompletable(getTemplate().delete(JdbcAuthenticationFlowContext.class)
                .matching(Query.query(where(COL_TRANSACTION_ID).is(transactionId))).all());
    }

    @Override
    public Completable delete(String transactionId, int version) {
        LOGGER.debug("delete({}, {})", transactionId, version);
        return monoToCompletable(getTemplate().delete(JdbcAuthenticationFlowContext.class)
                .matching(Query.query(where(COL_TRANSACTION_ID).is(transactionId).and(where(COL_VERSION).is(version)))).all());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(JdbcAuthenticationFlowContext.class).matching(Query.query(where(COL_EXPIRE_AT).lessThan(now))).all().then()).doOnError(error -> LOGGER.error("Unable to purge authentication contexts", error));
    }
}

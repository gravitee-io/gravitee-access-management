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

import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAuthenticationFlowContext;
import io.gravitee.am.repository.management.api.AuthenticationFlowContextRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.from;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcAuthenticationFlowContextRepository extends AbstractJdbcRepository implements AuthenticationFlowContextRepository {

    protected AuthenticationFlowContext toEntity(JdbcAuthenticationFlowContext entity) {
        return mapper.map(entity, AuthenticationFlowContext.class);
    }

    @Override
    public Maybe<AuthenticationFlowContext> findById(String id) {
        LOGGER.debug("findById({})", id);
        return monoToMaybe(dbClient.select()
                .from(JdbcAuthenticationFlowContext.class)
                .matching(from(where("id").is(id)))
                .as(JdbcAuthenticationFlowContext.class).one())
                .map(this::toEntity);
    }

    @Override
    public Maybe<AuthenticationFlowContext> findLastByTransactionId(String transactionId) {
        LOGGER.debug("findLastByTransactionId({})", transactionId);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(dbClient.select()
                .from(JdbcAuthenticationFlowContext.class)
                .matching(from(where("transaction_id").is(transactionId).and(where("expire_at").greaterThan(now))))
                .orderBy(Sort.Order.desc("version"))
                .as(JdbcAuthenticationFlowContext.class).first())
                .map(this::toEntity);
    }

    @Override
    public Flowable<AuthenticationFlowContext> findByTransactionId(String transactionId) {
        LOGGER.debug("findByTransactionId({})", transactionId);
        LocalDateTime now = LocalDateTime.now(UTC);
        return fluxToFlowable(dbClient.select()
                .from(JdbcAuthenticationFlowContext.class)
                .matching(from(where("transaction_id").is(transactionId).and(where("expire_at").greaterThan(now))))
                .orderBy(Sort.Order.desc("version"))
                .as(JdbcAuthenticationFlowContext.class).all())
                .map(this::toEntity);
    }

    @Override
    public Single<AuthenticationFlowContext> create(AuthenticationFlowContext context) {
       String id = context.getTransactionId() + "-" + context.getVersion();
        LOGGER.debug("Create AuthenticationContext with id {}", id);

        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = dbClient.insert().into("auth_flow_ctx");

        // doesn't use the class introspection to handle json objects
        insertSpec = addQuotedField(insertSpec,"id", id, String.class);
        insertSpec = addQuotedField(insertSpec,"transaction_id", context.getTransactionId(), String.class);
        insertSpec = addQuotedField(insertSpec,"version", context.getVersion(), Integer.class);
        insertSpec = addQuotedField(insertSpec,"created_at", dateConverter.convertTo(context.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec,"expire_at", dateConverter.convertTo(context.getExpireAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec,"data", context.getData());

        Mono<Integer> insertAction = insertSpec.fetch().rowsUpdated();

        return monoToSingle(insertAction)
                .flatMap((i) -> this.findById(id).toSingle());
    }

    @Override
    public Completable delete(String transactionId) {
        LOGGER.debug("delete({})", transactionId);
        return monoToCompletable(dbClient.delete()
                .from(JdbcAuthenticationFlowContext.class)
                .matching(from(where("transaction_id").is(transactionId))).fetch().rowsUpdated());
    }

    @Override
    public Completable delete(String transactionId, int version) {
        LOGGER.debug("delete({}, {})", transactionId, version);
        return monoToCompletable(dbClient.delete()
                .from(JdbcAuthenticationFlowContext.class)
                .matching(from(where("transaction_id").is(transactionId).and(where("version").is(version)))).fetch().rowsUpdated());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(dbClient.delete().from(JdbcAuthenticationFlowContext.class).matching(where("expire_at").lessThan(now)).then()).doOnError(error -> LOGGER.error("Unable to purge authentication contexts", error));
    }
}

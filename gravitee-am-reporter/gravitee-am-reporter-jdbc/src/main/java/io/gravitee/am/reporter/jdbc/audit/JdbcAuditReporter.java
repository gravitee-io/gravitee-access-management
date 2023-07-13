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
package io.gravitee.am.reporter.jdbc.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.CaseFormat;
import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.reporter.jdbc.JdbcReporterConfiguration;
import io.gravitee.am.reporter.jdbc.audit.model.AuditAccessPointJdbc;
import io.gravitee.am.reporter.jdbc.audit.model.AuditEntityJdbc;
import io.gravitee.am.reporter.jdbc.audit.model.AuditJdbc;
import io.gravitee.am.reporter.jdbc.audit.model.AuditOutcomeJdbc;
import io.gravitee.am.reporter.jdbc.dialect.DialectHelper;
import io.gravitee.am.reporter.jdbc.dialect.SearchQuery;
import io.gravitee.am.reporter.jdbc.spring.JdbcReporterSpringConfiguration;
import io.gravitee.am.reporter.jdbc.utils.JSONMapper;
import io.gravitee.common.service.AbstractService;
import io.gravitee.reporter.api.Reportable;
import io.gravitee.reporter.api.Reporter;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.gravitee.am.reporter.jdbc.dialect.AbstractDialect.intervals;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(JdbcReporterSpringConfiguration.class)
public class JdbcAuditReporter extends AbstractService<Reporter> implements AuditReporter, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcAuditReporter.class);
    public static final String REPORTER_AUTO_PROVISIONING = "management.jdbc.reporter.provisioning";
    public static final String AUDIT_FIELD_ACTOR = "actor";
    public static final String AUDIT_FIELD_TARGET = "target";

    private final Pattern pattern = Pattern.compile("___");

    public static final String COL_ID = "id";
    public static final String COL_TRANSACTION_ID = "transaction_id";
    public static final String COL_TYPE = "type";
    public static final String COL_REFERENCE_TYPE = "reference_type";
    public static final String COL_REFERENCE_ID = "reference_id";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_AUDIT_ID = "audit_id";
    public static final String COL_AUDIT_FIELD = "audit_field";
    public static final String COL_ALTERNATIVE_ID = "alternative_id";
    public static final String COL_DISPLAY_NAME = "display_name";
    public static final String COL_ATTRIBUTES = "attributes";
    public static final String COL_STATUS = "status";
    public static final String COL_MESSAGE = "message";
    public static final String COL_IP_ADDRESS = "ip_address";
    public static final String COL_USER_AGENT = "user_agent";

    private static final List<String> auditColumns = List.of(
            COL_ID,
            COL_TRANSACTION_ID,
            COL_TYPE,
            COL_REFERENCE_TYPE,
            COL_REFERENCE_ID,
            COL_TIMESTAMP
    );

    private static final List<String> entityColumns = List.of(
            COL_AUDIT_ID,
            COL_AUDIT_FIELD,
            COL_ID,
            COL_ALTERNATIVE_ID,
            COL_TYPE,
            COL_DISPLAY_NAME,
            COL_REFERENCE_TYPE,
            COL_REFERENCE_ID,
            COL_ATTRIBUTES
    );

    private static final List<String> outcomesColumns = List.of(
            COL_AUDIT_ID,
            COL_STATUS,
            COL_MESSAGE
    );

    private static final List<String> accessPointColumns = List.of(
            COL_AUDIT_ID,
            COL_ID,
            COL_ALTERNATIVE_ID,
            COL_DISPLAY_NAME,
            COL_IP_ADDRESS,
            COL_USER_AGENT
    );

    private String auditsTable;
    private String auditAccessPointsTable;
    private String auditOutcomesTable;
    private String auditEntitiesTable;

    @Autowired
    private Environment environment;

    @Autowired
    private R2dbcEntityTemplate template;

    @Autowired
    private JdbcReporterConfiguration configuration;

    @Autowired
    private ReactiveTransactionManager tm;

    @Autowired
    private DialectHelper dialectHelper;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    protected MappingR2dbcConverter rowMapper;

    private final PublishProcessor<Audit> bulkProcessor = PublishProcessor.create();

    private Disposable disposable;

    private boolean ready = false;

    private String INSERT_AUDIT_STATEMENT;
    private String INSERT_ENTITY_STATEMENT;
    private String INSERT_OUTCOMES_STATEMENT;
    private String INSERT_ACCESSPOINT_STATEMENT;

    @Override
    public boolean canSearch() {
        return true;
    }

    protected String createInsertStatement(String table, List<String> columns) {
        return "INSERT INTO " + table + " (" +
                columns.stream().collect(Collectors.joining(","))
                + ") VALUES (:" + columns.stream().collect(Collectors.joining(",:")) + ")";
    }

    @Override
    public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, int page, int size) {
        LOGGER.debug("search on ({}, {})", referenceType, referenceType);
        if (!ready) {
            LOGGER.debug("Reporter not yet bootstrapped");
            return Single.just(new Page<>(Collections.emptyList(), page, size));
        }

        SearchQuery searchQuery = dialectHelper.buildSearchQuery(referenceType, referenceId, criteria);

        DatabaseClient.GenericExecuteSpec query = template.getDatabaseClient().sql(searchQuery.getQuery() + dialectHelper.buildPagingClause(page, size));
        DatabaseClient.GenericExecuteSpec count = template.getDatabaseClient().sql(searchQuery.getCount());
        for (Map.Entry<String, Object> bind : searchQuery.getBindings().entrySet()) {
            query = query.bind(bind.getKey(), bind.getValue());
            count = count.bind(bind.getKey(), bind.getValue());
        }

        Mono<Long> total = count.map((row, rowMetadata) -> row.get(0, Long.class)).first();

        return fluxToFlowable(query.map((row, rowMetadata) -> rowMapper.read(AuditJdbc.class, row)).all()
                .map(this::convert)
                .concatMap(this::fillWithActor)
                .concatMap(this::fillWithTarget)
                .concatMap(this::fillWithAccessPoint)
                .concatMap(this::fillWithOutcomes))
                .toList()
                .flatMap(content -> monoToSingle(total).map(value -> new Page<Audit>(content, page, value)))
                .doOnError(error -> LOGGER.error("Unable to retrieve reports for referenceType {} and referenceId {}",
                        referenceType, referenceId, error));
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, Type analyticsType) {
        LOGGER.debug("aggregate on ({}, {}) with type {}", referenceType, referenceType, analyticsType);

        switch (analyticsType) {
            case DATE_HISTO:
                return executeHistogramAggregation(referenceType, referenceId, criteria);
            case GROUP_BY:
                SearchQuery groupByQuery = dialectHelper.buildGroupByQuery(referenceType, referenceId, criteria);
                return executeGroupBy(groupByQuery, criteria);
            case COUNT:
                SearchQuery searchQuery = dialectHelper.buildSearchQuery(referenceType, referenceId, criteria);
                return executeCount(searchQuery);
            default:
                return Single.error(new IllegalArgumentException("Analytics [" + analyticsType + "] cannot be calculated"));
        }
    }

    protected Single<Map<Object, Object>> executeHistogramAggregation(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        Map<Long, Long> intervals = intervals(criteria);
        String fieldSuccess = (criteria.types().get(0) + "_" + Status.SUCCESS).toLowerCase();
        String fieldFailure = (criteria.types().get(0) + "_" + Status.FAILURE).toLowerCase();

        if (!ready) {
            LOGGER.debug("Reporter not yet bootstrapped");
            Map<Object, Object> result = new HashMap<>();
            result.put(fieldSuccess, intervals.values().stream().collect(Collectors.toList()));
            result.put(fieldFailure, intervals.values().stream().collect(Collectors.toList()));
            return Single.just(result);
        }

        return dialectHelper.buildAndProcessHistogram(template.getDatabaseClient(), referenceType, referenceId, criteria).map(stats -> {
            Map<Long, Long> successResult = new TreeMap<>();
            Map<Long, Long> failureResult = new TreeMap<>();
            stats.forEach(slotValue -> {
                Long timestamp = ((Number) slotValue.get("slot")).longValue();
                Long attempts = ((Number) slotValue.get("attempts")).longValue();
                if (((String)slotValue.get(COL_STATUS)).equalsIgnoreCase("success")) {
                    successResult.put(timestamp, attempts);
                } else {
                    failureResult.put(timestamp, attempts);
                }
            });
            // complete result with remaining intervals
            intervals.forEach((k, v) -> {
                successResult.putIfAbsent(k, v);
                failureResult.putIfAbsent(k, v);
            });
            List<Long> successData = successResult.entrySet().stream().map(e -> e.getValue()).collect(Collectors.toList());
            List<Long> failureData = failureResult.entrySet().stream().map(e -> e.getValue()).collect(Collectors.toList());
            Map<Object, Object> result = new HashMap<>();
            result.put(fieldSuccess, successData);
            result.put(fieldFailure, failureData);
            return result;
        });
    }

    /**
     * Execute the COUNT aggregate function
     *
     * @param searchQuery
     * @return
     */
    private Single<Map<Object, Object>> executeCount(SearchQuery searchQuery) {
        if (!ready) {
            LOGGER.debug("Reporter not yet bootstrapped");
            return Single.just(Collections.singletonMap("data", 0l));
        }
        DatabaseClient.GenericExecuteSpec count = template.getDatabaseClient().sql(searchQuery.getCount());
        for (Map.Entry<String, Object> bind : searchQuery.getBindings().entrySet()) {
            count = count.bind(bind.getKey(), bind.getValue());
        }
        return monoToSingle(count.map((row, rowMetadata) -> row.get(0, Long.class)).first().switchIfEmpty(Mono.just(0l)))
                .map(data -> Collections.singletonMap("data", data));
    }

    /**
     * Execute the GROUP_BY aggregate function
     *
     * @param searchQuery
     * @param criteria
     * @return
     */
    private Single<Map<Object, Object>> executeGroupBy(SearchQuery searchQuery, AuditReportableCriteria criteria) {
        if (!ready) {
            LOGGER.debug("Reporter not yet bootstrapped");
            return Single.just(Collections.emptyMap());
        }
        DatabaseClient.GenericExecuteSpec groupBy = template.getDatabaseClient().sql(searchQuery.getQuery());
        for (Map.Entry<String, Object> bind : searchQuery.getBindings().entrySet()) {
            groupBy = groupBy.bind(bind.getKey(), bind.getValue());
        }
        return monoToSingle(groupBy.map((row, rowMetadata) -> Map.of(row.get(convertFieldName(criteria)), row.get("counter"))).all().reduce(new HashMap<>(), (acc, value) -> {
            acc.putAll(value);
            return acc;
        }));
    }

    private String convertFieldName(AuditReportableCriteria criteria) {
        if (criteria.field().contains(".")) {
            return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, criteria.field().split("\\.")[1]);
        } else {
            return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, criteria.field());
        }
    }

    @Override
    public Maybe<Audit> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("findById({},{},{})", referenceType, referenceId, id);
        if (!ready) {
            LOGGER.debug("Reporter not yet bootstrapped");
            return Maybe.empty();
        }

        Mono<Audit> auditMono = template.select(AuditJdbc.class).from(this.auditsTable).matching(
                Query.query(where(COL_REFERENCE_ID).is(referenceId)
                        .and(where(COL_REFERENCE_TYPE).is(referenceType.name()))
                        .and(where(COL_ID).is(id))))
                .first()
                .map(this::convert)
                .flatMap(this::fillWithActor)
                .flatMap(this::fillWithTarget)
                .flatMap(this::fillWithAccessPoint)
                .flatMap(this::fillWithOutcomes);

        return monoToMaybe(auditMono)
                .doOnError(error -> LOGGER.error("Unable to retrieve the Report with referenceType {}, referenceId {} and id {}",
                        referenceType, referenceId, id, error));
    }

    private Mono<Audit> fillWithActor(Audit audit) {
        return template.select(AuditEntityJdbc.class).from(this.auditEntitiesTable).matching(
                Query.query(where(COL_AUDIT_ID).is(audit.getId())
                        .and(where(COL_AUDIT_FIELD).is(AUDIT_FIELD_ACTOR))))
                .first()
                .map(entity -> fillWith(audit, entity))
                .switchIfEmpty(Mono.just(audit));
    }

    private Mono<Audit> fillWithTarget(Audit audit) {
        return template.select(AuditEntityJdbc.class).from(this.auditEntitiesTable).matching(
                Query.query(where(COL_AUDIT_ID).is(audit.getId())
                        .and(where(COL_AUDIT_FIELD).is(AUDIT_FIELD_TARGET))))
                .first()
                .map(entity -> fillWith(audit, entity))
                .switchIfEmpty(Mono.just(audit));
    }

    private Mono<Audit> fillWithAccessPoint(Audit audit) {
        return template.select(AuditAccessPointJdbc.class).from(this.auditAccessPointsTable).matching(
                Query.query(where(COL_AUDIT_ID).is(audit.getId())))
                .first()
                .map(entity -> fillWith(audit, entity))
                .switchIfEmpty(Mono.just(audit));
    }

    private Mono<Audit> fillWithOutcomes(Audit audit) {
        return template.select(AuditOutcomeJdbc.class).from(auditOutcomesTable).matching(
                Query.query(where(COL_AUDIT_ID).is(audit.getId())))
                .first()
                .map(entity -> fillWith(audit, entity))
                .switchIfEmpty(Mono.just(audit));
    }

    private Audit convert(AuditJdbc entity) {
        Audit audit = new Audit();
        audit.setId(entity.getId());
        audit.setReferenceId(entity.getReferenceId());
        audit.setReferenceType(entity.getReferenceType() == null ? null : ReferenceType.valueOf(entity.getReferenceType()));
        audit.setTimestamp(entity.getTimestamp() == null ? null : Instant.ofEpochSecond(entity.getTimestamp().toEpochSecond(ZoneOffset.UTC)));
        audit.setTransactionId(entity.getTransactionId());
        audit.setType(entity.getType());
        return audit;
    }

    private Audit fillWith(Audit audit, AuditAccessPointJdbc entity) {
        AuditAccessPoint accessPoint = new AuditAccessPoint();
        accessPoint.setId(entity.getId());
        accessPoint.setAlternativeId(entity.getAlternativeId());
        accessPoint.setDisplayName(entity.getDisplayName());
        accessPoint.setUserAgent(entity.getUserAgent());
        accessPoint.setIpAddress(entity.getIpAddress());

        audit.setAccessPoint(accessPoint);
        return audit;
    }

    private Audit fillWith(Audit audit, AuditEntityJdbc entity) {
        AuditEntity auditEntity = new AuditEntity();
        auditEntity.setId(entity.getId());
        auditEntity.setAlternativeId(entity.getAlternativeId());
        auditEntity.setDisplayName(entity.getDisplayName());
        auditEntity.setType(entity.getType());
        auditEntity.setReferenceId(entity.getReferenceId());
        auditEntity.setReferenceType(entity.getReferenceType() == null ? null : ReferenceType.valueOf(entity.getReferenceType()));
        auditEntity.setAttributes(JSONMapper.toCollectionOfBean(entity.getAttributes(), new TypeReference<Map<String, Object>>() {}));
        if (AUDIT_FIELD_ACTOR.equalsIgnoreCase(entity.getAuditField())) {
            audit.setActor(auditEntity);
        } else {
            audit.setTarget(auditEntity);
        }

        return audit;
    }

    private Audit fillWith(Audit audit, AuditOutcomeJdbc entity) {
        AuditOutcome outcome = new AuditOutcome();
        outcome.setMessage(entity.getMessage());
        outcome.setStatus(entity.getStatus());

        audit.setOutcome(outcome);
        return audit;
    }

    @Override
    public void report(Reportable reportable) {
        LOGGER.debug("Push reportable {} in bulk processor", reportable);
        bulkProcessor.onNext((Audit) reportable);
    }

    private Flowable bulk(List<Audit> audits) {
        if (audits == null || audits.isEmpty()) {
            return Flowable.empty();
        }

        return Flowable.fromPublisher(Flux.just(audits).concatMap(this::bulkInsertReport))
                .doOnError(error -> LOGGER.error("Error during bulk loading", error));
    }



    private Mono<Long> bulkInsertReport(List<Audit> audits) {
        TransactionalOperator trx = TransactionalOperator.create(tm);

        Mono<Long> insertAction = null;
        for (Audit audit: audits) {
            DatabaseClient.GenericExecuteSpec insertSpec = template.getDatabaseClient().sql(INSERT_AUDIT_STATEMENT);
            insertSpec = addQuotedField(insertSpec, COL_ID, audit.getId(), String.class);
            insertSpec = addQuotedField(insertSpec, COL_TRANSACTION_ID, audit.getTransactionId(), String.class);
            insertSpec = addQuotedField(insertSpec, COL_TYPE, audit.getType(), String.class);
            insertSpec = addQuotedField(insertSpec, COL_REFERENCE_TYPE, audit.getReferenceType() == null ? null : audit.getReferenceType().name(), String.class);
            insertSpec = addQuotedField(insertSpec, COL_REFERENCE_ID, audit.getReferenceId(), String.class);
            insertSpec = addQuotedField(insertSpec, COL_TIMESTAMP, LocalDateTime.ofInstant(audit.timestamp(), ZoneId.of(ZoneOffset.UTC.getId())), LocalDateTime.class);

            if (insertAction == null)
            {
                insertAction = insertSpec.fetch().rowsUpdated();
            } else {
                insertAction = insertAction.then(insertSpec.fetch().rowsUpdated());
            }

            AuditEntity actor = audit.getActor();
            if (actor != null) {
                insertAction = insertAction.then(prepateInsertEntity(audit, actor, AUDIT_FIELD_ACTOR));
            }

            AuditEntity target = audit.getTarget();
            if (target != null) {
                insertAction = insertAction.then(prepateInsertEntity(audit, target, AUDIT_FIELD_TARGET));
            }

            AuditOutcome outcome = audit.getOutcome();
            if (outcome != null) {
                DatabaseClient.GenericExecuteSpec insertOutcomeSpec = template.getDatabaseClient().sql(INSERT_OUTCOMES_STATEMENT);

                insertOutcomeSpec = addQuotedField(insertOutcomeSpec, COL_AUDIT_ID, audit.getId(), String.class);
                insertOutcomeSpec = addQuotedField(insertOutcomeSpec, COL_STATUS, outcome.getStatus(), String.class);
                insertOutcomeSpec = addQuotedField(insertOutcomeSpec, COL_MESSAGE, outcome.getMessage(), String.class);

                insertAction = insertAction.then(insertOutcomeSpec.fetch().rowsUpdated());
            }

            AuditAccessPoint accessPoint = audit.getAccessPoint();
            if (accessPoint != null) {
                DatabaseClient.GenericExecuteSpec insertAccessPointSpec = template.getDatabaseClient().sql(INSERT_ACCESSPOINT_STATEMENT);

                insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_AUDIT_ID, audit.getId(), String.class);
                insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_ID, accessPoint.getId(), String.class);
                insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_ALTERNATIVE_ID, accessPoint.getAlternativeId(), String.class);
                insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_DISPLAY_NAME, accessPoint.getDisplayName(), String.class);
                insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_IP_ADDRESS, accessPoint.getIpAddress(), String.class);
                insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_USER_AGENT, accessPoint.getUserAgent(), String.class);

                insertAction = insertAction.then(insertAccessPointSpec.fetch().rowsUpdated());
            }
        }

        return insertAction.as(trx::transactional);
    }

    private Mono<Long> insertReport(Audit audit) {
        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec insertSpec = template.getDatabaseClient().sql(INSERT_AUDIT_STATEMENT);
        insertSpec = addQuotedField(insertSpec, COL_ID, audit.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_TRANSACTION_ID, audit.getTransactionId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_TYPE, audit.getType(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_TYPE, audit.getReferenceType() == null ? null : audit.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_ID, audit.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_TIMESTAMP, LocalDateTime.ofInstant(audit.timestamp(), ZoneId.of(ZoneOffset.UTC.getId())), LocalDateTime.class);

        Mono<Long> insertAction = insertSpec.fetch().rowsUpdated();

        AuditEntity actor = audit.getActor();
        if (actor != null) {
            insertAction = insertAction.then(prepateInsertEntity(audit, actor, AUDIT_FIELD_ACTOR));
        }

        AuditEntity target = audit.getTarget();
        if (target != null) {
            insertAction = insertAction.then(prepateInsertEntity(audit, target, AUDIT_FIELD_TARGET));
        }

        AuditOutcome outcome = audit.getOutcome();
        if (outcome != null) {
            DatabaseClient.GenericExecuteSpec insertOutcomeSpec = template.getDatabaseClient().sql(INSERT_OUTCOMES_STATEMENT);

            insertOutcomeSpec = addQuotedField(insertOutcomeSpec, COL_AUDIT_ID, audit.getId(), String.class);
            insertOutcomeSpec = addQuotedField(insertOutcomeSpec, COL_STATUS, outcome.getStatus(), String.class);
            insertOutcomeSpec = addQuotedField(insertOutcomeSpec, COL_MESSAGE, outcome.getMessage(), String.class);

            insertAction = insertAction.then(insertOutcomeSpec.fetch().rowsUpdated());
        }

        AuditAccessPoint accessPoint = audit.getAccessPoint();
        if (accessPoint != null) {
            DatabaseClient.GenericExecuteSpec insertAccessPointSpec = template.getDatabaseClient().sql(INSERT_ACCESSPOINT_STATEMENT);

            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_AUDIT_ID, audit.getId(), String.class);
            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_ID, accessPoint.getId(), String.class);
            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_ALTERNATIVE_ID, accessPoint.getAlternativeId(), String.class);
            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_DISPLAY_NAME, accessPoint.getDisplayName(), String.class);
            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_IP_ADDRESS, accessPoint.getIpAddress(), String.class);
            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, COL_USER_AGENT, accessPoint.getUserAgent(), String.class);

            insertAction = insertAction.then(insertAccessPointSpec.fetch().rowsUpdated());
        }

        return insertAction.as(trx::transactional);
    }

    private Mono<Long> prepateInsertEntity(Audit audit, AuditEntity entity, String field) {
        DatabaseClient.GenericExecuteSpec insertEntitySpec = template.getDatabaseClient().sql(INSERT_ENTITY_STATEMENT);
        insertEntitySpec = addQuotedField(insertEntitySpec, COL_AUDIT_ID, audit.getId(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, COL_AUDIT_FIELD, field, String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, COL_ID, entity.getId(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, COL_ALTERNATIVE_ID, entity.getAlternativeId(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, COL_TYPE, entity.getType(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, COL_DISPLAY_NAME, entity.getDisplayName(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, COL_REFERENCE_TYPE, entity.getReferenceType() == null ? null : entity.getReferenceType().name(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, COL_REFERENCE_ID, entity.getReferenceId(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, COL_ATTRIBUTES, JSONMapper.toJson(entity.getAttributes()), String.class);
        return insertEntitySpec.fetch().rowsUpdated();
    }

    protected <T> DatabaseClient.GenericExecuteSpec addQuotedField(DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        final String tableSuffix = (configuration.getTableSuffix() == null || configuration.getTableSuffix().isEmpty()) ? "" : "_" + configuration.getTableSuffix();

        auditsTable = pattern.matcher("reporter_audits___").replaceAll(tableSuffix);
        auditEntitiesTable = pattern.matcher("reporter_audits_entities___").replaceAll(tableSuffix);
        auditOutcomesTable = pattern.matcher("reporter_audits_outcomes___").replaceAll(tableSuffix);
        auditAccessPointsTable = pattern.matcher("reporter_audits_access_points___").replaceAll(tableSuffix);

        this.dialectHelper.setAuditsTable(auditsTable);
        this.dialectHelper.setAuditEntitiesTable(auditEntitiesTable);
        this.dialectHelper.setAuditOutcomesTable(auditOutcomesTable);
        this.dialectHelper.setAuditAccessPointsTable(auditAccessPointsTable);

        this.INSERT_AUDIT_STATEMENT = createInsertStatement(this.auditsTable, auditColumns);
        this.INSERT_ENTITY_STATEMENT = createInsertStatement(this.auditEntitiesTable, entityColumns);
        this.INSERT_OUTCOMES_STATEMENT = createInsertStatement(this.auditOutcomesTable, outcomesColumns);
        this.INSERT_ACCESSPOINT_STATEMENT = createInsertStatement(this.auditAccessPointsTable, accessPointColumns);

        if (environment.getProperty(REPORTER_AUTO_PROVISIONING, Boolean.class, true)) {
            // for now simply get the file named <driver>.schema, more complex stuffs will be done if schema updates have to be done in the future
            final String sqlScript = "database/" + configuration.getDriver() + ".schema";

            Function<Connection, Mono<Integer>> resultFunction = connection -> {
                Statement doesTableExist = connection.createStatement(dialectHelper.tableExists(auditsTable));
                return flowableToFlux(Flowable.fromPublisher(doesTableExist.execute())
                        .flatMap(Result::getRowsUpdated)
                        .first(0)
                        .flatMapPublisher(total -> {
                            if (total == 0) {
                                LOGGER.debug("SQL datatable {} doest not exists, initialize all audit tables for the reporter.", auditsTable);
                                try (InputStream input = this.getClass().getClassLoader().getResourceAsStream(sqlScript);
                                     BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {

                                    List<String> sqlStatements = reader.lines()
                                            // remove empty line and comment
                                            .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("--"))
                                            .map(line -> {
                                                // update table & index names
                                                String finalLine = pattern.matcher(line).replaceAll(tableSuffix);
                                                LOGGER.debug("Statement to execute: {}", finalLine);
                                                return finalLine;
                                            })
                                            .distinct()
                                            .collect(Collectors.toList());

                                    LOGGER.debug("Found {} statements to execute", sqlStatements.size());
                                    return Flowable.fromIterable(sqlStatements)
                                            .flatMap(statement -> Flowable.fromPublisher(connection.createStatement(statement).execute()))
                                            .flatMap(Result::getRowsUpdated);

                                } catch (Exception e) {
                                    LOGGER.error("Unable to initialize the reporter schema", e);
                                    return Flowable.error(e);
                                }
                            } else {
                                return Flowable.empty();
                            }
                        })).reduce(Integer::sum);
            };

            // init bulk processor
            template.getDatabaseClient().inConnection(resultFunction)
                    .doOnError(error -> LOGGER.error("Unable to initialize Database", error))
                    .doOnTerminate(this::initializeBulkProcessor)
                    .subscribe();

        } else {
            initializeBulkProcessor();
        }
    }

    protected void initializeBulkProcessor() {
        if (!lifecycle.stopped()) {
            disposable = bulkProcessor.buffer(
                    configuration.getFlushInterval(),
                    TimeUnit.SECONDS,
                    configuration.getBulkActions())
                    .flatMap(JdbcAuditReporter.this::bulk)
                    .doOnError(error -> LOGGER.error("An error occurs while indexing data into report_audits_{} table of {} database",
                            configuration.getTableSuffix(), configuration.getDatabase(), error))
                    .onErrorResumeNext(err -> bulkProcessor)
                    .subscribe();

            ready = true;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        try {
            ready = false;
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }

            // we wait until the bulk processor has stopped
            if (bulkProcessor != null) {
                while (bulkProcessor.hasSubscribers()) {
                    LOGGER.debug("The bulk processor is processing data, wait.");
                }
            }

            if (this.connectionFactory != null && this.connectionFactory instanceof ConnectionPool) {
                ConnectionPool connectionFactory = (ConnectionPool) this.connectionFactory;
                if (!connectionFactory.isDisposed()) {
                    // dispose is a blocking call, use the non blocking one to avoid error
                    connectionFactory.disposeLater().subscribe();
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to close JDBC client", ex);
        }
    }
}

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
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ConnectionFactory;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.PublishProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.relational.core.sql.SqlIdentifier;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.gravitee.am.reporter.jdbc.dialect.AbstractDialect.intervals;
import static org.springframework.data.relational.core.query.Criteria.from;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(JdbcReporterSpringConfiguration.class)
public class JdbcAuditReporter extends AbstractService implements AuditReporter, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcAuditReporter.class);
    public static final String REPORTER_AUTO_PROVISIONING = "management.jdbc.reporter.provisioning";

    private final Pattern pattern = Pattern.compile("___");

    private String auditsTable;
    private String auditAccessPointsTable;
    private String auditOutcomesTable;
    private String auditEntitiesTable;

    @Autowired
    private Environment environment;

    @Autowired
    private DatabaseClient dbClient;

    @Autowired
    private JdbcReporterConfiguration configuration;

    @Autowired
    private ReactiveTransactionManager tm;

    @Autowired
    private DialectHelper dialectHelper;

    @Autowired
    private ConnectionFactory connectionFactory;

    private final PublishProcessor<Audit> bulkProcessor = PublishProcessor.create();

    private Disposable disposable;

    private boolean ready = false;

    @Override
    public boolean canSearch() {
        return true;
    }

    @Override
    public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, int page, int size) {
        LOGGER.debug("search on ({}, {})", referenceType, referenceType);
        if (!ready) {
            LOGGER.debug("Reporter not yet bootstrapped");
            return Single.just(new Page<>(Collections.emptyList(), page, size));
        }

        SearchQuery searchQuery = dialectHelper.buildSearchQuery(referenceType, referenceId, criteria);

        DatabaseClient.GenericExecuteSpec query = dbClient.execute(searchQuery.getQuery() + dialectHelper.buildPagingClause(page, size));
        DatabaseClient.GenericExecuteSpec count = dbClient.execute(searchQuery.getCount());
        for (Map.Entry<String, Object> bind : searchQuery.getBindings().entrySet()) {
            query = query.bind(bind.getKey(), bind.getValue());
            count = count.bind(bind.getKey(), bind.getValue());
        }

        Mono<Long> total = count.as(Long.class).fetch().first();

        return fluxToFlowable(query.as(AuditJdbc.class).fetch().all()
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

        return dialectHelper.buildAndProcessHistogram(dbClient, referenceType, referenceId, criteria).map(stats -> {
            Map<Long, Long> successResult = new TreeMap<>();
            Map<Long, Long> failureResult = new TreeMap<>();
            stats.forEach(slotValue -> {
                Long timestamp = ((Number) slotValue.get("slot")).longValue();
                Long attempts = ((Number) slotValue.get("attempts")).longValue();
                if (((String)slotValue.get("status")).equalsIgnoreCase("success")) {
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

        DatabaseClient.GenericExecuteSpec count = dbClient.execute(searchQuery.getCount());
        for (Map.Entry<String, Object> bind : searchQuery.getBindings().entrySet()) {
            count = count.bind(bind.getKey(), bind.getValue());
        }
        return monoToSingle(count.as(Long.class).fetch().first().switchIfEmpty(Mono.just(0l)))
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
        DatabaseClient.GenericExecuteSpec groupBy = dbClient.execute(searchQuery.getQuery());
        for (Map.Entry<String, Object> bind : searchQuery.getBindings().entrySet()) {
            groupBy = groupBy.bind(bind.getKey(), bind.getValue());
        }
        return monoToSingle(groupBy.fetch().all().collectMap((f) -> f.get(convertFieldName(criteria)), (f) -> f.get("counter")));
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

        Mono<Audit> auditMono = dbClient.select().from(auditsTable).matching(
                from(where("reference_id").is(referenceId)
                        .and(where("reference_type").is(referenceType.name()))
                        .and(where("id").is(id))))
                .as(AuditJdbc.class)
                .fetch()
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
        return dbClient.select().from(auditEntitiesTable).matching(
                from(where("audit_id").is(audit.getId())
                        .and(where("audit_field").is("actor"))))
                .as(AuditEntityJdbc.class)
                .fetch()
                .first()
                .map(entity -> fillWith(audit, entity))
                .switchIfEmpty(Mono.just(audit));
    }

    private Mono<Audit> fillWithTarget(Audit audit) {
        return dbClient.select().from(auditEntitiesTable).matching(
                from(where("audit_id").is(audit.getId())
                        .and(where("audit_field").is("target"))))
                .as(AuditEntityJdbc.class)
                .fetch()
                .first()
                .map(entity -> fillWith(audit, entity))
                .switchIfEmpty(Mono.just(audit));
    }

    private Mono<Audit> fillWithAccessPoint(Audit audit) {
        return dbClient.select().from(auditAccessPointsTable).matching(
                from(where("audit_id").is(audit.getId())))
                .as(AuditAccessPointJdbc.class)
                .fetch()
                .first()
                .map(entity -> fillWith(audit, entity))
                .switchIfEmpty(Mono.just(audit));
    }

    private Mono<Audit> fillWithOutcomes(Audit audit) {
        return dbClient.select().from(auditOutcomesTable).matching(
                from(where("audit_id").is(audit.getId())))
                .as(AuditOutcomeJdbc.class)
                .fetch()
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
        if ("actor".equalsIgnoreCase(entity.getAuditField())) {
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

        return Flowable.fromPublisher(Flux.fromIterable(audits).flatMap(this::insertReport, configuration.getMaxSize()))
                .doOnError(error -> LOGGER.error("Error during bulk loading", error));
    }

    private Mono insertReport(Audit audit) {
        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = dbClient.insert().into(auditsTable);
        insertSpec = addQuotedField(insertSpec,"id", audit.getId(), String.class);
        insertSpec = addQuotedField(insertSpec,"transaction_id", audit.getTransactionId(), String.class);
        insertSpec = addQuotedField(insertSpec,"type", audit.getType(), String.class);
        insertSpec = addQuotedField(insertSpec,"reference_type", audit.getReferenceType() == null ? null : audit.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec,"reference_id", audit.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec,"timestamp", LocalDateTime.ofInstant(audit.timestamp(), ZoneId.of(ZoneOffset.UTC.getId())), LocalDateTime.class);

        Mono<Integer> insertAction = insertSpec.fetch().rowsUpdated();

        AuditEntity actor = audit.getActor();
        if (actor != null) {
            insertAction = insertAction.then(prepateInsertEntity(audit, actor, "actor"));
        }

        AuditEntity target = audit.getTarget();
        if (target != null) {
            insertAction = insertAction.then(prepateInsertEntity(audit, target, "target"));
        }

        AuditOutcome outcome = audit.getOutcome();
        if (outcome != null) {
            DatabaseClient.GenericInsertSpec<Map<String, Object>> insertOutcomeSpec = dbClient.insert().into(auditOutcomesTable);

            insertOutcomeSpec = addQuotedField(insertOutcomeSpec, "audit_id", audit.getId(), String.class);
            insertOutcomeSpec = addQuotedField(insertOutcomeSpec, "status", outcome.getStatus(), String.class);
            insertOutcomeSpec = addQuotedField(insertOutcomeSpec, "message", outcome.getMessage(), String.class);

            insertAction = insertAction.then(insertOutcomeSpec.fetch().rowsUpdated());
        }

        AuditAccessPoint accessPoint = audit.getAccessPoint();
        if (accessPoint != null) {
            DatabaseClient.GenericInsertSpec<Map<String, Object>> insertAccessPointSpec = dbClient.insert().into(auditAccessPointsTable);

            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, "audit_id", audit.getId(), String.class);
            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, "id", accessPoint.getId(), String.class);
            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, "alternative_id", accessPoint.getAlternativeId(), String.class);
            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, "display_name", accessPoint.getDisplayName(), String.class);
            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, "ip_address", accessPoint.getIpAddress(), String.class);
            insertAccessPointSpec = addQuotedField(insertAccessPointSpec, "user_agent", accessPoint.getUserAgent(), String.class);

            insertAction = insertAction.then(insertAccessPointSpec.fetch().rowsUpdated());
        }

        return insertAction.as(trx::transactional);
    }

    private Mono<Integer> prepateInsertEntity(Audit audit, AuditEntity entity, String field) {
        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertEntitySpec = dbClient.insert().into(auditEntitiesTable);
        insertEntitySpec = addQuotedField(insertEntitySpec, "audit_id", audit.getId(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, "audit_field", field, String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, "id", entity.getId(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, "alternative_id", entity.getAlternativeId(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, "type", entity.getType(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, "display_name", entity.getDisplayName(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, "reference_type", entity.getReferenceType() == null ? null : entity.getReferenceType().name(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, "reference_id", entity.getReferenceId(), String.class);
        insertEntitySpec = addQuotedField(insertEntitySpec, "attributes", JSONMapper.toJson(entity.getAttributes()), String.class);
        return insertEntitySpec.fetch().rowsUpdated();
    }

    protected <T> DatabaseClient.GenericInsertSpec<Map<String, Object>> addQuotedField(DatabaseClient.GenericInsertSpec<Map<String, Object>> spec, String name, Object value, Class<T> type) {
        return value == null ? spec.nullValue(SqlIdentifier.quoted(name), type) : spec.value(SqlIdentifier.quoted(name), value);
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

        if (environment.getProperty(REPORTER_AUTO_PROVISIONING, Boolean.class, true)) {
            // for now simply get the file named <driver>.schema, more complex stuffs will be done if schema updates have to be done in the future
            final String sqlScript = "database/" + configuration.getDriver() + ".schema";
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

                dbClient.execute(dialectHelper.tableExists(auditsTable))
                        .as(Integer.class)
                        .fetch()
                        .first()
                        .switchIfEmpty(Mono.just(0))
                        .flatMap(found -> {
                            if (found == 0) {
                                return Flux.fromIterable(sqlStatements)
                                        .concatMap(statement -> dbClient.execute(statement).then())
                                        .then();
                            } else {
                                return Mono.empty();
                            }
                        })
                        .doOnError(error -> LOGGER.error("Unable to initialize Database", error))
                        .doOnTerminate(() -> {
                            // init bulk processor
                            initializeBulkProcessor();
                        }).subscribe();

            } catch (Exception e) {
                LOGGER.error("Unable to initialize the report schema", e);
            }
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

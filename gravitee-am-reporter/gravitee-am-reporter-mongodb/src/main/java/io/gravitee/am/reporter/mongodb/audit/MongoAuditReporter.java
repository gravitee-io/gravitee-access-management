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
package io.gravitee.am.reporter.mongodb.audit;

import com.mongodb.BasicDBObject;
import com.mongodb.ReadPreference;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
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
import io.gravitee.am.reporter.mongodb.MongoReporterConfiguration;
import io.gravitee.am.reporter.mongodb.audit.model.AuditAccessPointMongo;
import io.gravitee.am.reporter.mongodb.audit.model.AuditEntityMongo;
import io.gravitee.am.reporter.mongodb.audit.model.AuditMongo;
import io.gravitee.am.reporter.mongodb.audit.model.AuditOutcomeMongo;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.gravitee.common.service.AbstractService;
import io.gravitee.reporter.api.Reportable;
import io.gravitee.reporter.api.Reporter;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_ACCESS_POINT_ID;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_ACTOR;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_ACTOR_ID;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_ID;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_REFERENCE_ID;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_REFERENCE_TYPE;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_STATUS;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_TARGET;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_TARGET_ID;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_TIMESTAMP;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_TYPE;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_ACTOR_ID_TARGET_ID_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_TARGET_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_TYPE_STATUS_SUCCESS_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_TYPE_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.MIN_READ_PREFERENCE_STALENESS;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.OLD_INDICES;
import static java.util.stream.Collectors.toMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoAuditReporter extends AbstractService<Reporter> implements AuditReporter, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(MongoAuditReporter.class);
    @Autowired
    private ConnectionProvider connectionProvider;

    @Autowired
    private MongoReporterConfiguration configuration;

    @Value("${management.mongodb.ensureIndexOnStart:true}")
    private boolean ensureIndexOnStart;

    @Value("${management.mongodb.cursorMaxTime:60000}")
    private int cursorMaxTimeInMs;

    @Value("${reporters.mongodb.readPreference:PRIMARY}")
    private String readPreference = "PRIMARY";

    @Value("${reporters.mongodb.readPreferenceMaxStaleness:90000}")
    private Long readPreferenceMaxStalenessMs = MIN_READ_PREFERENCE_STALENESS;

    private ClientWrapper<MongoClient> clientWrapper;

    private MongoCollection<AuditMongo> reportableCollection;

    private final PublishProcessor<Audit> bulkProcessor = PublishProcessor.create();

    private final static ReplaceOptions UPSERT_OPTIONS = new ReplaceOptions().upsert(true);

    private Disposable disposable;

    protected final <TResult> FindPublisher<TResult> withMaxTimeout(FindPublisher<TResult> query) {
        return query.maxTime(this.cursorMaxTimeInMs, TimeUnit.MILLISECONDS);
    }

    protected final <TResult> AggregatePublisher<TResult> withMaxTimeout(AggregatePublisher<TResult> query) {
        return query.maxTime(this.cursorMaxTimeInMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean canSearch() {
        return true;
    }

    @Override
    public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, int page, int size) {
        // build query
        Bson query = query(referenceType, referenceId, criteria);

        // run search query
        Single<Long> countOperation = Observable.fromPublisher(withReadPreferenceCollection().countDocuments(query, new CountOptions().maxTime(cursorMaxTimeInMs, TimeUnit.MILLISECONDS))).first(0l);
        Single<List<Audit>> auditsOperation = Observable.fromPublisher(withMaxTimeout(withReadPreferenceCollection().find(query))
                        .sort(new BasicDBObject(FIELD_TIMESTAMP, -1))
                        .skip(size * page).limit(size))
                .map(this::convert).collect(LinkedList::new, List::add);
        return Single.zip(countOperation, auditsOperation, (count, audits) -> new Page<>(audits, page, count))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, Type analyticsType) {
        // build query
        Bson query = query(referenceType, referenceId, criteria);
        switch (analyticsType) {
            case DATE_HISTO:
                return executeHistogram(criteria, query)
                        .observeOn(Schedulers.computation());
            case GROUP_BY:
                return executeGroupBy(criteria, query)
                        .observeOn(Schedulers.computation());
            case COUNT:
                return executeCount(query)
                        .observeOn(Schedulers.computation());
            default:
                return Single.error(new IllegalArgumentException("Analytics [" + analyticsType + "] cannot be calculated"));
        }
    }

    @Override
    public Maybe<Audit> findById(ReferenceType referenceType, String referenceId, String id) {
        return Observable.fromPublisher(withReadPreferenceCollection().find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_ID, id)))
                .first()).firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public void report(Reportable reportable) {
        bulkProcessor
                .onNext((Audit) reportable);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.clientWrapper = this.connectionProvider.getClientWrapper();

        // init reportable collection
        reportableCollection = this.clientWrapper.getClient().getDatabase(this.configuration.getDatabase()).getCollection(this.configuration.getReportableCollection(), AuditMongo.class);

        // init indexes
        initIndexes();

        // init bulk processor
        disposable = bulkProcessor.buffer(
                configuration.getFlushInterval(),
                TimeUnit.SECONDS,
                configuration.getBulkActions())
                .flatMap(list -> bulk(list)
                        .doOnError(throwable -> logger.error("An error occurred while inserting into the audit log.", throwable))
                        .retry())
                .subscribe();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        try {
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }

            // we wait until the bulk processor has stopped
            while (bulkProcessor.hasSubscribers()) {
                logger.debug("The bulk processor is processing data, wait.");
            }

            this.clientWrapper.releaseClient();
        } catch (Exception ex) {
            logger.error("Failed to close mongoDB client", ex);
        }
    }

    private void initIndexes() {
        if (ensureIndexOnStart) {
            // drop old indexes
            // see : https://github.com/gravitee-io/issues/issues/7136
            Completable deleteOldIndexes = Observable.fromPublisher(reportableCollection.listIndexes())
                    .map(document -> document.getString("name"))
                    .flatMapCompletable(indexName -> {
                        if (OLD_INDICES.contains(indexName)) {
                            return Completable.fromPublisher(reportableCollection.dropIndex(indexName));
                        }
                        return Completable.complete();
                    });

            // create new indexes
            List<IndexModel> indexes = new ArrayList<>();
            indexes.add(new IndexModel(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_TIMESTAMP_NAME).background(true)));
            indexes.add(new IndexModel(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_TYPE, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_TYPE_TIMESTAMP_NAME).background(true)));
            indexes.add(new IndexModel(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_TYPE, 1).append(FIELD_STATUS, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_TYPE_STATUS_SUCCESS_TIMESTAMP_NAME).partialFilterExpression(new Document(FIELD_STATUS, new Document("$eq", "SUCCESS"))).background(true)));
            indexes.add(new IndexModel(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_ACTOR, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME).background(true)));
            indexes.add(new IndexModel(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_TARGET, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_TARGET_TIMESTAMP_NAME).background(true)));
            indexes.add(new IndexModel(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_ACTOR, 1).append(FIELD_TARGET, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME).background(true)));
            indexes.add(new IndexModel(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_ACTOR_ID, 1).append(FIELD_TARGET_ID, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_ACTOR_ID_TARGET_ID_TIMESTAMP_NAME).background(true)));
            Completable createNewIndexes = Completable.fromPublisher(reportableCollection.createIndexes(indexes))
                    .doOnComplete(() -> logger.debug("{} Reporter indexes created", indexes.size()))
                    .doOnError(throwable -> logger.error("An error has occurred during creation of indexes", throwable));

            // process indexes
            deleteOldIndexes
                    .andThen(createNewIndexes)
                    .subscribe();

        }
    }

    private Single<Map<Object, Object>> executeHistogram(AuditReportableCriteria criteria, Bson query) {
        // NOTE : MongoDB does not return count : 0 if there is no matching document in the given time range, we need to add it by hand
        Map<Long, Long> intervals = intervals(criteria);
        Map<String, String> types = new HashMap<>();
        for (String type : criteria.types()) {
            types.put((type + "_" + Status.SUCCESS).toLowerCase(), type);
            types.put((type + "_" + Status.FAILURE).toLowerCase(), type);
        }
        BasicDBObject subTractTimestamp = new BasicDBObject("$subtract", Arrays.asList("$timestamp", new Date(0)));
        List<BsonField> condition = types.entrySet().stream().map(es -> Accumulators.sum(es.getKey(),
                        new BasicDBObject("$cond",
                                Arrays.asList(
                                        new BasicDBObject("$and", Arrays.asList(
                                                new BasicDBObject("$eq", Arrays.asList("$outcome.status", es.getKey().contains(Status.SUCCESS.name().toLowerCase()) ? Status.SUCCESS.name() : Status.FAILURE.name())),
                                                new BasicDBObject("$eq", Arrays.asList("$type", es.getValue()))
                                        )),
                                        1,
                                        0
                                )
                        )
                )
        ).toList();

        return Observable.fromPublisher(withMaxTimeout(withReadPreferenceCollection().aggregate(Arrays.asList(
                        Aggregates.match(query),
                        Aggregates.group(
                                new BasicDBObject("_id",
                                        new BasicDBObject("$subtract",
                                                Arrays.asList(subTractTimestamp, new BasicDBObject("$mod", Arrays.asList(subTractTimestamp, criteria.interval()))))),
                                condition
                        )), Document.class))
                ).toList()
                .map(docs -> {
                    Map<String, Map<Long, Long>> results = new HashMap<>();
                    types.forEach((key, value) -> results.put(key, new HashMap<>()));
                    docs.forEach(document -> {
                        Long timestamp = ((Number) ((Document) document.get("_id")).get("_id")).longValue();
                        results.forEach((k, v) -> v.put(timestamp, ((Number) document.get(k)).longValue()));
                    });
                    // complete result with remaining intervals
                    intervals.forEach((k, v) -> results.forEach((k1, v1) -> v1.putIfAbsent(k, v)));
                    return results.entrySet().stream().collect(toMap(Map.Entry::getKey, entry -> entry.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).collect(Collectors.toList())));
                });
    }

    private Single<Map<Object, Object>> executeGroupBy(AuditReportableCriteria criteria, Bson query) {
        List<Bson> aggregates = new ArrayList<>(Arrays.asList(
                Aggregates.match(query),
                Aggregates.group(new BasicDBObject("_id", "$" + criteria.field()), Accumulators.sum("count", 1))));
        if (criteria.size() != null && criteria.size() != 0) {
            aggregates.add(Aggregates.limit(criteria.size()));
        }
        return Observable.fromPublisher(withMaxTimeout(withReadPreferenceCollection().aggregate(aggregates, Document.class
                )))
                .toList()
                .map(docs -> docs.stream().collect(toMap(d -> ((Document) d.get("_id")).get("_id"), d -> d.get("count"))));
    }

    private Single<Map<Object, Object>> executeCount(Bson query) {
        return Observable.fromPublisher(withReadPreferenceCollection().countDocuments(query, new CountOptions().maxTime(cursorMaxTimeInMs, TimeUnit.MILLISECONDS))).first(0l).map(data -> Collections.singletonMap("data", data));
    }

    private Flowable<BulkWriteResult> bulk(List<Audit> audits) {
        if (audits == null || audits.isEmpty()) {
            return Flowable.empty();
        }

        return Flowable.fromPublisher(reportableCollection.bulkWrite(this.convertToBulkList(audits)));
    }

    private Bson query(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        List<Bson> filters = new ArrayList<>();

        filters.add(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)));

        // event types
        if (criteria.types() != null && !criteria.types().isEmpty()) {
            filters.add(in(FIELD_TYPE, criteria.types()));
        }

        // event status
        if (criteria.status() != null && !criteria.status().isEmpty()) {
            filters.add(eq(FIELD_STATUS, criteria.status()));
        }

        // event user
        if (criteria.user() != null && !criteria.user().isEmpty()) {
            filters.add(or(eq(FIELD_ACTOR, criteria.user()), eq(FIELD_TARGET, criteria.user())));
        }

        // event user technical ID
        if (criteria.userId() != null && !criteria.userId().isEmpty()) {
            filters.add(or(eq(FIELD_ACTOR_ID, criteria.userId()), eq(FIELD_TARGET_ID, criteria.userId())));
        }

        // time range
        if (criteria.from() != 0 && criteria.to() != 0) {
            filters.add(new Document(FIELD_TIMESTAMP, new Document("$gte", new Date(criteria.from())).append("$lte", new Date(criteria.to()))));
        } else {
            if (criteria.from() != 0) {
                filters.add(gte(FIELD_TIMESTAMP, new Date(criteria.from())));
            }
            if (criteria.to() != 0) {
                filters.add(lte(FIELD_TIMESTAMP, new Date(criteria.to())));
            }
        }

        if (criteria.accessPointId() != null && !criteria.accessPointId().isEmpty()) {
            filters.add(eq(FIELD_ACCESS_POINT_ID, criteria.accessPointId()));
        }

        // build query
        return and(filters);
    }

    private List<ReplaceOneModel<AuditMongo>> convertToBulkList(List<Audit> audits) {
        return audits.stream()
                .map(audit -> new ReplaceOneModel<>(new BasicDBObject("_id", audit.getId()), convert(audit), UPSERT_OPTIONS))
                .collect(Collectors.toList());
    }

    private AuditMongo convert(Audit audit) {
        AuditMongo auditMongo = new AuditMongo();
        auditMongo.setId(audit.getId());
        auditMongo.setTransactionId(audit.getTransactionId());
        auditMongo.setReferenceType(audit.getReferenceType());
        auditMongo.setReferenceId(audit.getReferenceId());
        auditMongo.setType(audit.getType());
        auditMongo.setTimestamp(audit.timestamp());

        // actor
        if (audit.getActor() != null) {
            AuditEntity actor = audit.getActor();
            AuditEntityMongo actorMongo = new AuditEntityMongo();
            actorMongo.setId(actor.getId());
            actorMongo.setAlternativeId(actor.getAlternativeId());
            actorMongo.setType(actor.getType());
            actorMongo.setDisplayName(actor.getDisplayName());
            actorMongo.setReferenceType(actor.getReferenceType() != null ? actor.getReferenceType().name() : null);
            actorMongo.setReferenceId(actor.getReferenceId());
            actorMongo.setAttributes(new Document(actor.getAttributes()));
            auditMongo.setActor(actorMongo);
        }

        // access point
        if (audit.getAccessPoint() != null) {
            AuditAccessPoint accessPoint = audit.getAccessPoint();
            AuditAccessPointMongo accessPointMongo = new AuditAccessPointMongo();
            accessPointMongo.setId(accessPoint.getId());
            accessPointMongo.setAlternativeId(accessPoint.getAlternativeId());
            accessPointMongo.setDisplayName(accessPoint.getDisplayName());
            accessPointMongo.setIpAddress(accessPoint.getIpAddress());
            accessPointMongo.setUserAgent(accessPoint.getUserAgent());
            auditMongo.setAccessPoint(accessPointMongo);
        }

        // target
        if (audit.getTarget() != null) {
            AuditEntity target = audit.getTarget();
            AuditEntityMongo targetMongo = new AuditEntityMongo();
            targetMongo.setId(target.getId());
            targetMongo.setType(target.getType());
            targetMongo.setAlternativeId(target.getAlternativeId());
            targetMongo.setDisplayName(target.getDisplayName());
            targetMongo.setReferenceType(target.getReferenceType() != null ? target.getReferenceType().name() : null);
            targetMongo.setReferenceId(target.getReferenceId());
            targetMongo.setAttributes(new Document(target.getAttributes()));
            auditMongo.setTarget(targetMongo);
        }

        // outcome
        if (audit.getOutcome() != null) {
            AuditOutcome result = audit.getOutcome();
            AuditOutcomeMongo resultMongo = new AuditOutcomeMongo();
            resultMongo.setStatus(result.getStatus().name());
            resultMongo.setMessage(result.getMessage());
            auditMongo.setOutcome(resultMongo);
        }
        return auditMongo;
    }

    private Audit convert(AuditMongo auditMongo) {
        Audit audit = new Audit();
        audit.setId(auditMongo.getId());
        audit.setTransactionId(auditMongo.getTransactionId());
        audit.setReferenceType(auditMongo.getReferenceType());
        audit.setReferenceId(auditMongo.getReferenceId());
        audit.setType(auditMongo.getType());
        audit.setTimestamp(auditMongo.getTimestamp());

        // actor
        if (auditMongo.getActor() != null) {
            AuditEntityMongo actorMongo = auditMongo.getActor();
            AuditEntity actor = new AuditEntity();
            actor.setId(actorMongo.getId());
            actor.setAlternativeId(actorMongo.getAlternativeId());
            actor.setType(actorMongo.getType());
            actor.setDisplayName(actorMongo.getDisplayName());
            actor.setReferenceType(actorMongo.getReferenceType() != null ? ReferenceType.valueOf(actorMongo.getReferenceType()) : null);
            actor.setReferenceId(actorMongo.getReferenceId());
            actor.setAttributes(actorMongo.getAttributes());
            audit.setActor(actor);
        }

        // access point
        if (auditMongo.getAccessPoint() != null) {
            AuditAccessPointMongo accessPointMongo = auditMongo.getAccessPoint();
            AuditAccessPoint accessPoint = new AuditAccessPoint();
            accessPoint.setId(accessPointMongo.getId());
            accessPoint.setAlternativeId(accessPointMongo.getAlternativeId());
            accessPoint.setDisplayName(accessPointMongo.getDisplayName());
            accessPoint.setIpAddress(accessPointMongo.getIpAddress());
            accessPoint.setUserAgent(accessPointMongo.getUserAgent());
            audit.setAccessPoint(accessPoint);
        }

        // target
        if (auditMongo.getTarget() != null) {
            AuditEntityMongo targetMongo = auditMongo.getTarget();
            AuditEntity target = new AuditEntity();
            target.setId(targetMongo.getId());
            target.setType(targetMongo.getType());
            target.setAlternativeId(targetMongo.getAlternativeId());
            target.setDisplayName(targetMongo.getDisplayName());
            target.setReferenceType(targetMongo.getReferenceType() != null ? ReferenceType.valueOf(targetMongo.getReferenceType()) : null);
            target.setReferenceId(targetMongo.getReferenceId());
            target.setAttributes(targetMongo.getAttributes());
            audit.setTarget(target);
        }

        // outcome
        if (auditMongo.getOutcome() != null) {
            AuditOutcomeMongo resultMongo = auditMongo.getOutcome();
            AuditOutcome result = new AuditOutcome();
            result.setStatus(Status.valueOf(resultMongo.getStatus()));
            result.setMessage(resultMongo.getMessage());
            audit.setOutcome(result);
        }

        return audit;
    }

    private Map<Long, Long> intervals(AuditReportableCriteria criteria) {
        ChronoUnit unit = convert(criteria.interval());
        Instant startDate = Instant.ofEpochMilli(criteria.from()).truncatedTo(unit);
        Instant endDate = Instant.ofEpochMilli(criteria.to()).truncatedTo(unit);

        Map<Long, Long> intervals = new HashMap<>();
        intervals.put(startDate.toEpochMilli(), 0L);
        while (startDate.isBefore(endDate)) {
            startDate = startDate.plus(criteria.interval(), ChronoUnit.MILLIS);
            intervals.put(startDate.toEpochMilli(), 0L);
        }
        return intervals;
    }

    private ChronoUnit convert(long millisecondsInterval) {
        if (millisecondsInterval >= 0 && millisecondsInterval < ChronoUnit.MINUTES.getDuration().toMillis()) {
            return ChronoUnit.SECONDS;
        } else if (millisecondsInterval >= ChronoUnit.MINUTES.getDuration().toMillis() && millisecondsInterval < ChronoUnit.HOURS.getDuration().toMillis()) {
            return ChronoUnit.MINUTES;
        } else if (millisecondsInterval >= ChronoUnit.HOURS.getDuration().toMillis() && millisecondsInterval < ChronoUnit.DAYS.getDuration().toMillis()) {
            return ChronoUnit.HOURS;
        } else {
            return ChronoUnit.DAYS;
        }
    }

    private MongoCollection<AuditMongo> withReadPreferenceCollection() {
        if (this.readPreference == null) {
            return this.reportableCollection;
        }

        ReadPreference readPreferenceValue;
        try {
            readPreferenceValue = ReadPreference.valueOf(this.readPreference);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid read preference value: {}", this.readPreference, ex);
            return this.reportableCollection;
        }

        // Max staleness is only compatible with NON-PRIMARY read preference
        if (readPreferenceValue != ReadPreference.primary()) {
            if (this.readPreferenceMaxStalenessMs < MIN_READ_PREFERENCE_STALENESS) {
                this.readPreferenceMaxStalenessMs = MIN_READ_PREFERENCE_STALENESS;
            }
            readPreferenceValue = readPreferenceValue.withMaxStalenessMS(this.readPreferenceMaxStalenessMs, TimeUnit.MILLISECONDS);
        }

        return this.reportableCollection.withReadPreference(readPreferenceValue);
    }
}

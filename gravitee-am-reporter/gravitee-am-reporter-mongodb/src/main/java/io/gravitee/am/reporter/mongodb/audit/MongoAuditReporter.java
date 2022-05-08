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
import com.mongodb.client.model.*;
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
import io.gravitee.common.service.AbstractService;
import io.gravitee.reporter.api.Reportable;
import io.reactivex.*;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.PublishProcessor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import({io.gravitee.am.reporter.mongodb.spring.MongoReporterConfiguration.class})
public class MongoAuditReporter extends AbstractService implements AuditReporter, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(MongoAuditReporter.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_REFERENCE_TYPE = "referenceType";
    private static final String FIELD_REFERENCE_ID = "referenceId";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_STATUS = "outcome.status";
    private static final String FIELD_TARGET = "target.alternativeId";
    private static final String FIELD_TARGET_ID = "target.id";
    private static final String FIELD_ACTOR = "actor.alternativeId";
    private static final String FIELD_ACTOR_ID = "actor.id";
    private static final String FIELD_ACCESS_POINT_ID = "accessPoint.id";
    private static final String INDEX_REFERENCE_TIMESTAMP_NAME = "ref_1_time_-1";
    private static final String INDEX_REFERENCE_TYPE_TIMESTAMP_NAME = "ref_1_type_1_time_-1";
    private static final String INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME = "ref_1_actor_1_time_-1";
    private static final String INDEX_REFERENCE_TARGET_TIMESTAMP_NAME = "ref_1_target_1_time_-1";
    private static final String INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME = "ref_1_actor_1_target_1_time_-1";
    private static final String INDEX_REFERENCE_ACTOR_ID_TARGET_ID_TIMESTAMP_NAME = "ref_1_actorId_1_targetId_1_time_-1";
    private static final String OLD_INDEX_REFERENCE_TIMESTAMP_NAME = "referenceType_1_referenceId_1_timestamp_-1";
    private static final String OLD_INDEX_REFERENCE_TYPE_TIMESTAMP_NAME = "referenceType_1_referenceId_1_type_1_timestamp_-1";
    private static final String OLD_INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME = "referenceType_1_referenceId_1_actor.alternativeId_1_timestamp_-1";
    private static final String OLD_INDEX_REFERENCE_TARGET_TIMESTAMP_NAME = "referenceType_1_referenceId_1_target.alternativeId_1_timestamp_-1";
    private static final String OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME = "referenceType_1_referenceId_1_actor.alternativeId_1_target.alternativeId_1_timestamp_-1";

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private MongoReporterConfiguration configuration;

    @Value("${management.mongodb.ensureIndexOnStart:true}")
    private boolean ensureIndexOnStart;

    private MongoCollection<AuditMongo> reportableCollection;

    private final PublishProcessor<Audit> bulkProcessor = PublishProcessor.create();

    private Disposable disposable;

    @Override
    public boolean canSearch() {
        return true;
    }
    @Override
    public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, int page, int size) {
        // build query
        Bson query = query(referenceType, referenceId, criteria);

        // run search query
        Single<Long> countOperation = Observable.fromPublisher(reportableCollection.countDocuments(query)).first(0l);
        Single<List<Audit>> auditsOperation = Observable.fromPublisher(reportableCollection.find(query).sort(new BasicDBObject(FIELD_TIMESTAMP, -1)).skip(size * page).limit(size)).map(this::convert).collect(LinkedList::new, List::add);
        return Single.zip(countOperation, auditsOperation, (count, audits) -> new Page<>(audits, page, count));
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, Type analyticsType) {
        // build query
        Bson query = query(referenceType, referenceId, criteria);
        switch (analyticsType) {
            case DATE_HISTO:
                return executeHistogram(criteria, query);
            case GROUP_BY:
                return executeGroupBy(criteria, query);
            case COUNT:
                return executeCount(query);
            default:
                return Single.error(new IllegalArgumentException("Analytics [" + analyticsType + "] cannot be calculated"));
        }
    }

    @Override
    public Maybe<Audit> findById(ReferenceType referenceType, String referenceId, String id) {
        return Observable.fromPublisher(reportableCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_ID, id))).first()).firstElement().map(this::convert);
    }

    @Override
    public void report(Reportable reportable) {
        bulkProcessor
                .onNext((Audit) reportable);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // init reportable collection
        reportableCollection = this.mongoClient.getDatabase(this.configuration.getDatabase()).getCollection(this.configuration.getReportableCollection(), AuditMongo.class);

        // init indexes
        initIndexes();

        // init bulk processor
        disposable = bulkProcessor.buffer(
                configuration.getFlushInterval(),
                TimeUnit.SECONDS,
                configuration.getBulkActions())
                .flatMap(this::bulk)
                .doOnError(throwable -> logger.error("An error occurs while indexing data into MongoDB", throwable))
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

            mongoClient.close();
        } catch (Exception ex) {
            logger.error("Failed to close mongoDB client", ex);
        }
    }

    private void initIndexes() {
        if (ensureIndexOnStart) {
            List<String> oldIndexNames = Arrays.asList(
                    OLD_INDEX_REFERENCE_TIMESTAMP_NAME,
                    OLD_INDEX_REFERENCE_TYPE_TIMESTAMP_NAME,
                    OLD_INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME,
                    OLD_INDEX_REFERENCE_TARGET_TIMESTAMP_NAME,
                    OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME
            );
            // drop old indexes
            // see : https://github.com/gravitee-io/issues/issues/7136
            Completable deleteOldIndexes = Observable.fromPublisher(reportableCollection.listIndexes())
                    .map(document -> document.getString("name"))
                    .flatMapCompletable(indexName -> {
                        if (oldIndexNames.contains(indexName)) {
                            return Completable.fromPublisher(reportableCollection.dropIndex(indexName));
                        } else {
                            return Completable.complete();
                        }
                    });

            // create new indexes
            Map<Document, IndexOptions> indexes = new HashMap<>();
            indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_TIMESTAMP_NAME));
            indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_TYPE, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_TYPE_TIMESTAMP_NAME));
            indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_ACTOR, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME));
            indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_TARGET, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_TARGET_TIMESTAMP_NAME));
            indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_ACTOR, 1).append(FIELD_TARGET, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME));
            indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_ACTOR_ID, 1).append(FIELD_TARGET_ID, 1).append(FIELD_TIMESTAMP, -1), new IndexOptions().name(INDEX_REFERENCE_ACTOR_ID_TARGET_ID_TIMESTAMP_NAME));
            Completable createNewIndexes = Observable.fromIterable(indexes.entrySet())
                    .flatMapCompletable(index -> Completable.fromPublisher(reportableCollection.createIndex(index.getKey(), index.getValue()))
                            .doOnComplete(() -> logger.debug("Created an index named: {}", index.getValue().getName()))
                            .doOnError(throwable -> logger.error("An error has occurred during creation of index {}", index.getValue().getName(), throwable)));

            // process indexes
            deleteOldIndexes
                    .andThen(createNewIndexes)
                    .subscribe();

        }
    }

    private Single<Map<Object, Object>> executeHistogram(AuditReportableCriteria criteria, Bson query) {
        // NOTE : MongoDB does not return count : 0 if there is no matching document in the given time range, we need to add it by hand
        Map<Long, Long> intervals = intervals(criteria);
        String fieldSuccess = (criteria.types().get(0) + "_" + Status.SUCCESS).toLowerCase();
        String fieldFailure = (criteria.types().get(0) + "_" + Status.FAILURE).toLowerCase();
        return Observable.fromPublisher(reportableCollection.aggregate(Arrays.asList(
                Aggregates.match(query),
                Aggregates.group(
                        new BasicDBObject("_id",
                                new BasicDBObject("$subtract",
                                        Arrays.asList(
                                                new BasicDBObject("$subtract", Arrays.asList("$timestamp", new Date(0))),
                                                new BasicDBObject("$mod", Arrays.asList(new BasicDBObject("$subtract", Arrays.asList("$timestamp", new Date(0))), criteria.interval()))
                                        ))),
                        Accumulators.sum(fieldSuccess, new BasicDBObject("$cond", Arrays.asList(new BasicDBObject("$eq", Arrays.asList("$outcome.status", Status.SUCCESS)), 1, 0))),
                        Accumulators.sum(fieldFailure, new BasicDBObject("$cond", Arrays.asList(new BasicDBObject("$eq", Arrays.asList("$outcome.status", Status.FAILURE)), 1, 0))))), Document.class))
                .toList()
                .map(docs -> {
                    Map<Long, Long> successResult = new HashMap<>();
                    Map<Long, Long> failureResult = new HashMap<>();
                    docs.forEach(document -> {
                        Long timestamp = ((Number) ((Document) document.get("_id")).get("_id")).longValue();
                        Long successAttempts = ((Number) document.get(fieldSuccess)).longValue();
                        Long failureAttempts = ((Number) document.get(fieldFailure)).longValue();
                        successResult.put(timestamp, successAttempts);
                        failureResult.put(timestamp, failureAttempts);
                    });
                    // complete result with remaining intervals
                    intervals.forEach((k, v) -> {
                        successResult.putIfAbsent(k, v);
                        failureResult.putIfAbsent(k, v);
                    });
                    List<Long> successData = successResult.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getValue()).collect(Collectors.toList());
                    List<Long> failureData = failureResult.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getValue()).collect(Collectors.toList());
                    Map<Object, Object> result = new HashMap<>();
                    result.put(fieldSuccess, successData);
                    result.put(fieldFailure, failureData);
                    return result;
                });
    }

    private Single<Map<Object, Object>> executeGroupBy(AuditReportableCriteria criteria, Bson query) {
        return Observable.fromPublisher(reportableCollection.aggregate(
                Arrays.asList(
                        Aggregates.match(query),
                        Aggregates.group(new BasicDBObject("_id", "$" + criteria.field()), Accumulators.sum("count", 1)),
                        Aggregates.limit(criteria.size() != null ? criteria.size() : 50)), Document.class
        ))
                .toList()
                .map(docs -> docs.stream().collect(Collectors.toMap(d -> ((Document) d.get("_id")).get("_id"), d -> d.get("count"))));
    }

    private Single<Map<Object, Object>> executeCount(Bson query) {
        return Observable.fromPublisher(reportableCollection.countDocuments(query)).first(0l).map(data -> Collections.singletonMap("data", data));
    }

    private Flowable bulk(List<Audit> audits) {
        if (audits == null || audits.isEmpty()) {
            return Flowable.empty();
        }

        return Flowable.fromPublisher(reportableCollection.bulkWrite(this.convert(audits)));
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
        Bson query = (filters.isEmpty()) ? new BasicDBObject() : and(filters);
        return query;
    }

    private List<WriteModel<AuditMongo>> convert(List<Audit> audits) {
        return audits.stream().map(audit -> new InsertOneModel<>(convert(audit))).collect(Collectors.toList());
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
            auditMongo.setTarget(targetMongo);
        }

        // outcome
        if (audit.getOutcome() != null) {
            AuditOutcome result = audit.getOutcome();
            AuditOutcomeMongo resultMongo = new AuditOutcomeMongo();
            resultMongo.setStatus(result.getStatus());
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
            audit.setTarget(target);
        }

        // outcome
        if (auditMongo.getOutcome() != null) {
            AuditOutcomeMongo resultMongo = auditMongo.getOutcome();
            AuditOutcome result = new AuditOutcome();
            result.setStatus(resultMongo.getStatus());
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
        intervals.put(startDate.toEpochMilli(), 0l);
        while (startDate.isBefore(endDate)) {
            startDate = startDate.plus(criteria.interval(), ChronoUnit.MILLIS);
            intervals.put(startDate.toEpochMilli(), 0l);
        }
        return intervals;
    }

    private ChronoUnit convert(long millisecondsInterval) {
        if (millisecondsInterval >= 0 && millisecondsInterval < 60 * 1000) {
            return ChronoUnit.SECONDS;
        } else if (millisecondsInterval >= 60 * 1000 && millisecondsInterval < 60 * 60 * 1000) {
            return ChronoUnit.MINUTES;
        } else if (millisecondsInterval >= 60 * 60 * 1000 && millisecondsInterval < 24 * 60 * 60 * 1000) {
            return ChronoUnit.HOURS;
        } else {
            return ChronoUnit.DAYS;
        }
    }
}

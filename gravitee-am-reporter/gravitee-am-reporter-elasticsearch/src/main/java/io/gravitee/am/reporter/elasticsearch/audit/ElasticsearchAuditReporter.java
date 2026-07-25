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
package io.gravitee.am.reporter.elasticsearch.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchReporterConfiguration;
import io.gravitee.am.reporter.elasticsearch.LoggableEndpoints;
import io.gravitee.am.reporter.elasticsearch.audit.model.AuditDocument;
import io.gravitee.am.reporter.elasticsearch.spring.ElasticsearchClientConfiguration;
import io.gravitee.common.service.AbstractService;
import io.gravitee.elasticsearch.client.Client;
import io.gravitee.elasticsearch.exception.ElasticsearchException;
import io.gravitee.elasticsearch.model.Aggregation;
import io.gravitee.elasticsearch.model.SearchHit;
import io.gravitee.elasticsearch.model.SearchResponse;
import io.gravitee.reporter.api.Reportable;
import io.gravitee.reporter.api.Reporter;
import io.vertx.core.Context;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.CustomLog;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Audit reporter that writes audits to (and searches them from) an Elasticsearch / OpenSearch cluster,
 * reusing the shared {@code gravitee-common-elasticsearch} HTTP client for transport.
 *
 * @author GraviteeSource Team
 */
@CustomLog
@Import(ElasticsearchClientConfiguration.class)
public class ElasticsearchAuditReporter extends AbstractService<Reporter> implements AuditReporter, InitializingBean {

    @Autowired
    private Client client;

    @Autowired
    private ElasticsearchReporterConfiguration configuration;

    private final ObjectMapper mapper = new ObjectMapper();

    private ElasticsearchQueryBuilder queryBuilder;

    private AuditDropCounter drops;

    private final PublishProcessor<Audit> bulkProcessor = PublishProcessor.create();

    /** Completes once the index template is in place. Nothing is written before it does. */
    private Completable indexReady;

    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicLong pendingAudits = new AtomicLong();
    private final CountDownLatch drained = new CountDownLatch(1);

    private Disposable disposable;

    /** The in-flight index preparation, which retries indefinitely while the cluster is unreachable. */
    private Disposable preparation;

    @Override
    public void afterPropertiesSet() {
        this.queryBuilder = new ElasticsearchQueryBuilder(mapper);
        this.drops = new AuditDropCounter();

        this.indexReady = prepareIndex()
                .retryWhen(errors -> errors.flatMap(this::retryUnlessMisconfigured))
                .cache();
        // kicked off now rather than on the first audit, so a misconfiguration shows up immediately.
        // Never awaited here: afterPropertiesSet can run on a Vert.x event loop, and the Elasticsearch
        // client needs that same event loop to answer, so blocking would deadlock the node.
        // Held so stopping cancels it: an unreachable cluster is retried indefinitely, and a reporter
        // is stopped and restarted on every update event, so an uncancelled retry would outlive it.
        this.preparation = this.indexReady
                .subscribeOn(Schedulers.io())
                .subscribe(() -> { },
                        error -> log.error("The Elasticsearch audit reporter cannot write audits", error));

        this.disposable = bulkProcessor
                .buffer(configuration.getFlushInterval(), TimeUnit.SECONDS, configuration.getBulkActions())
                .filter(audits -> !audits.isEmpty())
                .map(audits -> BulkBatch.of(configuration.getIndex(), mapper, audits))
                .doOnNext(this::accountForUnserializable)
                .filter(batch -> !batch.isEmpty())
                // bounded backlog: when Elasticsearch cannot keep up the oldest pending batch is
                // evicted rather than letting the node grow until it runs out of memory
                .onBackpressureBuffer(configuration.getMaxPendingBatches(),
                        () -> { },
                        BackpressureOverflowStrategy.DROP_OLDEST,
                        this::onBatchEvicted)
                .flatMapCompletable(this::deliver, true, configuration.getMaxConcurrentRequests())
                .subscribe(drained::countDown, error -> {
                    log.error("The Elasticsearch audit pipeline stopped unexpectedly, audits will no longer be written", error);
                    drained.countDown();
                });
    }

    /**
     * An unreachable cluster is transient, so it is retried indefinitely and the reporter keeps
     * buffering (and, if the outage lasts, dropping and counting) meanwhile. A server that answers and
     * refuses is a misconfiguration: retrying will never fix it, so preparation fails for good and
     * every batch from then on is dropped with that reason rather than written to an index whose
     * mapping would silently reject audits.
     */
    private Flowable<?> retryUnlessMisconfigured(Throwable error) {
        if (error instanceof ElasticsearchException) {
            return Flowable.error(new IllegalStateException(templateFailureMessage(), error));
        }
        if (error instanceof IllegalStateException) {
            // an unsupported or undetectable server version, which already carries its own message
            return Flowable.error(error);
        }
        if (!accepting.get()) {
            // the reporter is stopping. This is what actually ends the loop: indexReady is cached, and
            // a cached Completable keeps its upstream alive no matter who unsubscribes, so disposing
            // the subscription alone would leave this retrying for the lifetime of the node.
            return Flowable.error(error);
        }
        log.warn("Elasticsearch at {} is not reachable yet. No audit will be written until its index " +
                "template has been applied.", LoggableEndpoints.redact(configuration.getEndpoints()), error);
        return Flowable.timer(configuration.getRetryMaxInterval(), TimeUnit.SECONDS, Schedulers.computation());
    }

    private Completable prepareIndex() {
        return Completable.defer(() -> {
            AuditIndexNames.validate(configuration.getIndex());
            return doPrepareIndex();
        });
    }

    private Completable doPrepareIndex() {
        return client.getInfo()
                .map(ElasticsearchServerVersion::detect)
                .flatMapCompletable(version -> {
                    if (!version.isSupported()) {
                        return Completable.error(new IllegalStateException(version.unsupportedMessage()));
                    }
                    log.info("Elasticsearch audit reporter connected to {}, writing to {}",
                            version, AuditIndexNames.readPattern(configuration.getIndex()));
                    return client.putIndexTemplate(
                            AuditIndexTemplate.name(configuration.getIndex()),
                            AuditIndexTemplate.bodyFor(version, configuration.getIndex()));
                });
    }

    private String templateFailureMessage() {
        String index = configuration.getIndex();
        return ("Unable to apply the Elasticsearch index template '%s' for pattern '%s' at priority %d, so the reporter " +
                "cannot start. Elasticsearch refuses a composable index template whose patterns overlap an existing one " +
                "at the same priority; its response, naming the conflicting template, is logged immediately above this " +
                "error by the Elasticsearch client. Remove or re-prioritise the conflicting template, or change this " +
                "reporter's index name. The reporter will not run without its template, because audit attributes would " +
                "then be dynamically mapped and audits could be silently rejected.")
                .formatted(AuditIndexTemplate.name(index), AuditIndexNames.readPattern(index), AuditIndexTemplate.priority(index));
    }

    @Override
    public boolean canHandle(Reportable reportable) {
        return reportable instanceof Audit;
    }

    @Override
    public void report(Reportable reportable) {
        if (reportable instanceof Audit audit) {
            if (!accepting.get()) {
                drops.notAccepted(audit.getId());
                return;
            }
            pendingAudits.incrementAndGet();
            bulkProcessor.onNext(audit);
        }
    }

    @Override
    public boolean canSearch() {
        return true;
    }

    @Override
    public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, int page, int size) {
        long from = (long) size * page;
        if (from + size > ElasticsearchQueryBuilder.MAX_RESULT_WINDOW) {
            return Single.error(new IllegalArgumentException(
                    ("Elasticsearch cannot page past the first %d audits (asked for %d to %d). Narrow the search with a " +
                            "date range, or raise index.max_result_window on the audit indices.")
                            .formatted(ElasticsearchQueryBuilder.MAX_RESULT_WINDOW, from, from + size)));
        }
        String query = queryBuilder.buildSearchQuery(referenceType, referenceId, criteria, page, size);
        return client.search(readPattern(), null, query)
                .map(response -> {
                    List<Audit> audits = new ArrayList<>();
                    long total = 0;
                    if (response.getSearchHits() != null) {
                        if (response.getSearchHits().getTotal() != null) {
                            total = response.getSearchHits().getTotal().getValue();
                        }
                        if (response.getSearchHits().getHits() != null) {
                            for (SearchHit hit : response.getSearchHits().getHits()) {
                                audits.add(toAudit(hit));
                            }
                        }
                    }
                    return new Page<>(audits, page, total);
                })
                .doOnError(error -> log.error("Unable to search audits for {} {}", referenceType, referenceId, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Audit> findById(ReferenceType referenceType, String referenceId, String id) {
        String query = queryBuilder.buildFindByIdQuery(referenceType, referenceId, id);
        return client.search(readPattern(), null, query)
                .flatMapMaybe(response -> {
                    if (response.getSearchHits() != null
                            && response.getSearchHits().getHits() != null
                            && !response.getSearchHits().getHits().isEmpty()) {
                        return Maybe.just(toAudit(response.getSearchHits().getHits().get(0)));
                    }
                    return Maybe.empty();
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, Type analyticsType) {
        switch (analyticsType) {
            case COUNT:
                return client.count(readPattern(), null, queryBuilder.buildCountQuery(referenceType, referenceId, criteria))
                        .map(response -> {
                            Map<Object, Object> result = new HashMap<>();
                            result.put("data", response.getCount() != null ? response.getCount() : 0L);
                            return result;
                        })
                        .observeOn(Schedulers.computation());
            case GROUP_BY:
                return Single.defer(() -> client.search(readPattern(), null, queryBuilder.buildGroupByQuery(referenceType, referenceId, criteria)))
                        .map(this::toGroupByResult)
                        .observeOn(Schedulers.computation());
            case DATE_HISTO:
                return client.search(readPattern(), null, queryBuilder.buildDateHistogramQuery(referenceType, referenceId, criteria))
                        .map(response -> toHistogramResult(response, criteria))
                        .observeOn(Schedulers.computation());
            default:
                return Single.error(new IllegalArgumentException("Analytics [" + analyticsType + "] cannot be calculated"));
        }
    }

    /**
     * Stops accepting audits, flushes what is buffered and waits for Elasticsearch to acknowledge it,
     * bounded so a sick cluster cannot hang a rolling restart. Whatever could not be flushed in time
     * is logged rather than lost silently.
     */
    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (disposable == null || disposable.isDisposed()) {
            return;
        }

        accepting.set(false);
        if (preparation != null) {
            preparation.dispose();
        }
        bulkProcessor.onComplete();

        long timeout = configuration.getShutdownFlushTimeout();
        if (Context.isOnEventLoopThread()) {
            // awaiting here would block the very event loop the flush needs to complete on, so the
            // drain is left to finish in the background rather than deadlocked into losing everything
            log.warn("The Elasticsearch reporter is stopping on an event loop thread, so its flush of {} audits " +
                    "cannot be awaited and will finish in the background.", pendingAudits.get());
            return;
        }
        if (!drained.await(timeout, TimeUnit.SECONDS)) {
            log.error("The Elasticsearch reporter could not flush within {}s of stopping. {} audits were not written.",
                    timeout, pendingAudits.get());
        }
        disposable.dispose();
    }

    private String readPattern() {
        return AuditIndexNames.readPattern(configuration.getIndex());
    }

    private void onBatchEvicted(BulkBatch batch) {
        pendingAudits.addAndGet(-batch.size());
        drops.overflowed(batch);
    }

    /**
     * An audit that could not be serialized never reaches a batch, so without this it would stay
     * counted as pending forever and the shutdown messages would overstate what was lost.
     */
    private void accountForUnserializable(BulkBatch batch) {
        int lost = batch.unserializable().size();
        if (lost > 0) {
            pendingAudits.addAndGet(-lost);
            drops.unserializable(lost);
        }
    }

    private Completable deliver(BulkBatch batch) {
        return indexReady
                .andThen(Completable.defer(() -> send(batch, 0)))
                // send() absorbs its own failures, so anything arriving here means the reporter never
                // became writable at all — a misconfiguration rather than a delivery problem
                .onErrorResumeNext(error -> {
                    drops.notWritable(batch, error);
                    return Completable.complete();
                })
                .doFinally(() -> pendingAudits.addAndGet(-batch.size()));
    }

    private Completable send(BulkBatch batch, int attempt) {
        return client.bulk(batch.payload(), false)
                .flatMapCompletable(response -> {
                    BulkBatch remaining = batch.retryable(response, drops::rejected);
                    accountForUnserializable(remaining);
                    if (remaining.isEmpty()) {
                        return Completable.complete();
                    }
                    return retryOrDrop(remaining, attempt, new ElasticsearchException(
                            "Elasticsearch acknowledged only %d of %d audits".formatted(batch.size() - remaining.size(), batch.size())));
                })
                .onErrorResumeNext(error -> retryOrDrop(batch, attempt, error));
    }

    private Completable retryOrDrop(BulkBatch batch, int attempt, Throwable cause) {
        if (attempt >= configuration.getRetryAttempts()) {
            drops.retriesExhausted(batch, cause);
            return Completable.complete();
        }
        long delay = backoffSeconds(attempt);
        log.warn("Retrying {} audits to Elasticsearch in {}s (attempt {} of {}): {}",
                batch.size(), delay, attempt + 1, configuration.getRetryAttempts(), cause.getMessage());
        return Completable.timer(delay, TimeUnit.SECONDS, Schedulers.computation())
                .andThen(Completable.defer(() -> send(batch, attempt + 1)));
    }

    private long backoffSeconds(int attempt) {
        long delay = configuration.getRetryInitialInterval() * (1L << Math.min(attempt, 16));
        return Math.min(delay, configuration.getRetryMaxInterval());
    }

    private Audit toAudit(SearchHit hit) throws Exception {
        AuditDocument document = mapper.treeToValue(hit.getSource(), AuditDocument.class);
        return AuditConverter.toAudit(document);
    }

    private Map<Object, Object> toGroupByResult(SearchResponse response) {
        Map<Object, Object> result = new LinkedHashMap<>();
        Aggregation aggregation = aggregation(response, ElasticsearchQueryBuilder.AGG_BY_FIELD);
        if (aggregation != null && aggregation.getBuckets() != null) {
            for (JsonNode bucket : aggregation.getBuckets()) {
                result.put(bucket.get("key").asText(), bucket.get("doc_count").asLong());
            }
        }
        return result;
    }

    private Map<Object, Object> toHistogramResult(SearchResponse response, AuditReportableCriteria criteria) {
        Map<Object, Object> result = new HashMap<>();
        List<String> keys = new ArrayList<>();
        if (criteria.types() != null) {
            for (String type : criteria.types()) {
                for (Status status : new Status[]{Status.SUCCESS, Status.FAILURE}) {
                    String key = ElasticsearchQueryBuilder.histogramKey(type, status);
                    keys.add(key);
                    result.put(key, new ArrayList<Long>());
                }
            }
        }

        Aggregation aggregation = aggregation(response, ElasticsearchQueryBuilder.AGG_BY_DATE);
        if (aggregation != null && aggregation.getBuckets() != null) {
            for (JsonNode bucket : aggregation.getBuckets()) {
                for (String key : keys) {
                    JsonNode subAgg = bucket.get(key);
                    long count = (subAgg != null && subAgg.get("doc_count") != null) ? subAgg.get("doc_count").asLong() : 0L;
                    @SuppressWarnings("unchecked")
                    List<Long> series = (List<Long>) result.get(key);
                    series.add(count);
                }
            }
        }
        return result;
    }

    private Aggregation aggregation(SearchResponse response, String name) {
        if (response.getAggregations() == null) {
            return null;
        }
        return response.getAggregations().get(name);
    }
}

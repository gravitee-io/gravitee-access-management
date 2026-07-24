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
import io.gravitee.am.reporter.elasticsearch.audit.model.AuditDocument;
import io.gravitee.am.reporter.elasticsearch.spring.ElasticsearchClientConfiguration;
import io.gravitee.elasticsearch.client.Client;
import io.gravitee.elasticsearch.model.Aggregation;
import io.gravitee.elasticsearch.model.SearchHit;
import io.gravitee.elasticsearch.model.SearchResponse;
import io.gravitee.common.service.AbstractService;
import io.gravitee.reporter.api.Reportable;
import io.gravitee.reporter.api.Reporter;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.buffer.Buffer;
import lombok.CustomLog;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    private final PublishProcessor<Audit> bulkProcessor = PublishProcessor.create();

    private Disposable disposable;

    @Override
    public void afterPropertiesSet() {
        this.queryBuilder = new ElasticsearchQueryBuilder(mapper);

        // best-effort index template so key fields are keyword and the timestamp is a date
        client.putIndexTemplate(configuration.getIndex() + "-template", indexTemplate())
                .doOnError(error -> log.warn("Unable to create Elasticsearch index template for {}", configuration.getIndex(), error))
                .onErrorComplete()
                .subscribe();

        this.disposable = bulkProcessor
                .buffer(configuration.getFlushInterval(), TimeUnit.SECONDS, configuration.getBulkActions())
                .flatMap(audits -> bulk(audits)
                        .doOnError(throwable -> log.error("An error occurred while indexing audits into Elasticsearch", throwable))
                        .retry())
                .subscribe();
    }

    @Override
    public boolean canHandle(Reportable reportable) {
        return reportable instanceof Audit;
    }

    @Override
    public void report(Reportable reportable) {
        if (reportable instanceof Audit audit) {
            bulkProcessor.onNext(audit);
        }
    }

    @Override
    public boolean canSearch() {
        return true;
    }

    @Override
    public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, int page, int size) {
        String query = queryBuilder.buildSearchQuery(referenceType, referenceId, criteria, page, size);
        return client.search(configuration.getIndex(), null, query)
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
        return client.search(configuration.getIndex(), null, query)
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
                return client.count(configuration.getIndex(), null, queryBuilder.buildCountQuery(referenceType, referenceId, criteria))
                        .map(response -> {
                            Map<Object, Object> result = new HashMap<>();
                            result.put("data", response.getCount() != null ? response.getCount() : 0L);
                            return result;
                        })
                        .observeOn(Schedulers.computation());
            case GROUP_BY:
                return client.search(configuration.getIndex(), null, queryBuilder.buildGroupByQuery(referenceType, referenceId, criteria))
                        .map(this::toGroupByResult)
                        .observeOn(Schedulers.computation());
            case DATE_HISTO:
                return client.search(configuration.getIndex(), null, queryBuilder.buildDateHistogramQuery(referenceType, referenceId, criteria))
                        .map(response -> toHistogramResult(response, criteria))
                        .observeOn(Schedulers.computation());
            default:
                return Single.error(new IllegalArgumentException("Analytics [" + analyticsType + "] cannot be calculated"));
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    private Flowable<?> bulk(List<Audit> audits) {
        if (audits == null || audits.isEmpty()) {
            return Flowable.empty();
        }
        Buffer payload = Buffer.buffer();
        for (Audit audit : audits) {
            try {
                String action = mapper.writeValueAsString(Map.of("index", Map.of("_index", configuration.getIndex(), "_id", audit.getId())));
                String source = mapper.writeValueAsString(AuditConverter.toDocument(audit));
                payload.appendString(action).appendString("\n").appendString(source).appendString("\n");
            } catch (Exception e) {
                log.error("Unable to serialize audit {} for Elasticsearch bulk", audit.getId(), e);
            }
        }
        return client.bulk(payload, false).toFlowable();
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

    private String indexTemplate() {
        // Explicitly map the fields we filter / aggregate on. actor.attributes and target.attributes are
        // free-form maps whose value types vary across audit events, so they are stored but NOT indexed
        // (enabled:false) to avoid dynamic-mapping conflicts that would otherwise reject documents. The
        // strings_as_keyword dynamic template is a safety net for any unforeseen top-level string field.
        return """
                {
                  "index_patterns": ["%s*"],
                  "template": {
                    "mappings": {
                      "dynamic_templates": [
                        {
                          "strings_as_keyword": {
                            "match_mapping_type": "string",
                            "mapping": { "type": "keyword", "ignore_above": 2048 }
                          }
                        }
                      ],
                      "properties": {
                        "id": { "type": "keyword" },
                        "transactionId": { "type": "keyword" },
                        "referenceType": { "type": "keyword" },
                        "referenceId": { "type": "keyword" },
                        "type": { "type": "keyword" },
                        "timestamp": { "type": "date", "format": "epoch_millis" },
                        "actor": {
                          "properties": {
                            "id": { "type": "keyword" },
                            "alternativeId": { "type": "keyword" },
                            "type": { "type": "keyword" },
                            "displayName": { "type": "keyword", "ignore_above": 2048 },
                            "referenceType": { "type": "keyword" },
                            "referenceId": { "type": "keyword" },
                            "attributes": { "type": "object", "enabled": false }
                          }
                        },
                        "target": {
                          "properties": {
                            "id": { "type": "keyword" },
                            "alternativeId": { "type": "keyword" },
                            "type": { "type": "keyword" },
                            "displayName": { "type": "keyword", "ignore_above": 2048 },
                            "referenceType": { "type": "keyword" },
                            "referenceId": { "type": "keyword" },
                            "attributes": { "type": "object", "enabled": false }
                          }
                        },
                        "accessPoint": {
                          "properties": {
                            "id": { "type": "keyword" },
                            "alternativeId": { "type": "keyword" },
                            "displayName": { "type": "keyword", "ignore_above": 2048 },
                            "ipAddress": { "type": "keyword" },
                            "userAgent": { "type": "keyword", "ignore_above": 2048 }
                          }
                        },
                        "outcome": {
                          "properties": {
                            "status": { "type": "keyword" },
                            "message": { "type": "text" }
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(configuration.getIndex());
    }
}

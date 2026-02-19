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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.common.analytics.Field;
import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.management.service.AnalyticsService;
import io.gravitee.am.management.service.AuditService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.analytics.AnalyticsCountResponse;
import io.gravitee.am.model.analytics.AnalyticsGroupByResponse;
import io.gravitee.am.model.analytics.AnalyticsHistogramResponse;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.analytics.AnalyticsResponse;
import io.gravitee.am.model.analytics.Bucket;
import io.gravitee.am.model.analytics.Timestamp;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.gravitee.am.common.audit.EventType.USER_LOGIN;
import static io.gravitee.am.common.audit.EventType.USER_MAGIC_LINK_LOGIN;
import static io.gravitee.am.common.audit.EventType.USER_WEBAUTHN_LOGIN;
import static io.gravitee.am.common.audit.EventType.USER_CBA_LOGIN;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AnalyticsServiceImpl implements AnalyticsService {

    @Autowired
    private AuditService auditService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Override
    public Single<AnalyticsResponse> execute(Domain domain, AnalyticsQuery query) {
        return switch (query.getType()) {
            case DATE_HISTO -> executeDateHistogram(query);
            case GROUP_BY -> executeGroupBy(domain, query);
            case COUNT -> executeCount(domain, query);
        };
    }

    private Single<AnalyticsResponse> executeDateHistogram(AnalyticsQuery query) {
        AuditReportableCriteria.Builder queryBuilder = new AuditReportableCriteria.Builder();
        if (query.getField().equalsIgnoreCase(USER_LOGIN)) {
            queryBuilder.types(EventType.loginTypes());
        } else {
            queryBuilder.types(Collections.singletonList(query.getField().toUpperCase()));
        }
        queryBuilder.from(query.getFrom());
        queryBuilder.to(query.getTo());
        queryBuilder.interval(query.getInterval());
        return auditService.aggregate(query.getDomain(), queryBuilder.build(), query.getType())
                .map(values -> {
                    Timestamp timestamp = new Timestamp(query.getFrom(), query.getTo(), query.getInterval());
                    List<Bucket> buckets = values
                            .entrySet()
                            .stream()
                            .map(entry -> {
                                Bucket bucket = new Bucket();
                                bucket.setName((String) entry.getKey());
                                bucket.setField(query.getField());
                                bucket.setData((List<Long>) entry.getValue());
                                return bucket;
                            })
                            .collect(Collectors.toList());
                    AnalyticsHistogramResponse analyticsHistogramResponse = new AnalyticsHistogramResponse();
                    analyticsHistogramResponse.setTimestamp(timestamp);
                    analyticsHistogramResponse.setValues(buckets);
                    return analyticsHistogramResponse;
                });
    }

    private Single<AnalyticsResponse> executeGroupBy(Domain domain, AnalyticsQuery query) {
        AuditReportableCriteria.Builder queryBuilder = new AuditReportableCriteria.Builder()
                .types(Collections.singletonList(query.getField().toUpperCase()));
        queryBuilder.from(query.getFrom());
        queryBuilder.to(query.getTo());
        queryBuilder.size(query.getSize());

        switch (query.getField()) {
            case Field.APPLICATION:
                // applications are group by login attempts
                queryBuilder.types(List.of(USER_LOGIN, USER_WEBAUTHN_LOGIN, USER_CBA_LOGIN, USER_MAGIC_LINK_LOGIN));
                queryBuilder.status(Status.SUCCESS.name());
                queryBuilder.field("accessPoint.id");
                return executeGroupBy(query.getDomain(), queryBuilder.build(), query.getType())
                        .flatMap(analyticsResponse -> fetchMetadata((AnalyticsGroupByResponse) analyticsResponse));
            case Field.USER_STATUS, Field.USER_REGISTRATION:
                return dataPlaneRegistry.getUserRepository(domain).statistics(query).map(AnalyticsGroupByResponse::new);
            case Field.USER_LOGIN:
                queryBuilder.types(List.of(USER_LOGIN, USER_WEBAUTHN_LOGIN, USER_CBA_LOGIN, USER_MAGIC_LINK_LOGIN));
                queryBuilder.field("type");
                return executeGroupBy(query.getDomain(), queryBuilder.build(), query.getType())
                        .flatMap(response -> Single.just(transformKeys((AnalyticsGroupByResponse) response)));
            case Field.WEBAUTHN:
                queryBuilder.types(List.of(EventType.USER_WEBAUTHN_LOGIN));
                queryBuilder.field("outcome.status");
                return executeGroupBy(query.getDomain(), queryBuilder.build(), query.getType())
                        .flatMap(response -> Single.just(transformKeys((AnalyticsGroupByResponse) response)));
            case Field.CBA:
                queryBuilder.types(List.of(USER_CBA_LOGIN));
                queryBuilder.field("outcome.status");
                return executeGroupBy(query.getDomain(), queryBuilder.build(), query.getType())
                        .flatMap(response -> Single.just(transformKeys((AnalyticsGroupByResponse) response)));
            case Field.MAGIC_LINK:
                queryBuilder.types(List.of(USER_MAGIC_LINK_LOGIN));
                queryBuilder.field("outcome.status");
                return executeGroupBy(query.getDomain(), queryBuilder.build(), query.getType())
                        .flatMap(response -> Single.just(transformKeys((AnalyticsGroupByResponse) response)));
            default:
                return executeGroupBy(query.getDomain(), queryBuilder.build(), query.getType());
        }
    }

    private Single<AnalyticsResponse> fetchMetadata(AnalyticsGroupByResponse analyticsGroupByResponse) {
        Map<Object, Object> values = analyticsGroupByResponse.getValues();
        if (values == null || values.isEmpty()) {
            return Single.just(analyticsGroupByResponse);
        }
        return Observable.fromIterable(values.keySet())
                .flatMapMaybe(appId -> applicationService.findById((String) appId)
                        .map(application -> {
                            Map<String, Object> data = new HashMap<>();
                            data.put("name", application.getName());
                            data.put("domain", application.getDomain());
                            return Collections.singletonMap((String) appId, data);
                        })
                        .defaultIfEmpty(Collections.singletonMap((String) appId, getGenericMetadata("Deleted application", true)))
                        .toMaybe())
                .toList()
                .map(result -> {
                    Map<String, Map<String, Object>> metadata = result.stream()
                            .flatMap(m -> m.entrySet().stream())
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    analyticsGroupByResponse.setMetadata(metadata);
                    return analyticsGroupByResponse;
                });

    }

    private Single<AnalyticsResponse> executeCount(Domain domain, AnalyticsQuery query) {
        AuditReportableCriteria.Builder queryBuilder = new AuditReportableCriteria.Builder();
        if (query.getField().equalsIgnoreCase(USER_LOGIN)) {
            queryBuilder.types(EventType.loginTypes());
        } else {
            queryBuilder.types(Collections.singletonList(query.getField().toUpperCase()));
        }
        queryBuilder.from(query.getFrom());
        queryBuilder.to(query.getTo());
        queryBuilder.status(Status.SUCCESS.name());

        return switch (query.getField()) {
            case Field.APPLICATION ->
                    applicationService.countByDomain(query.getDomain()).map(AnalyticsCountResponse::new);
            case Field.USER -> dataPlaneRegistry.getUserRepository(domain).countByReference(domain.asReference()).map(AnalyticsCountResponse::new);
            default -> auditService.aggregate(query.getDomain(), queryBuilder.build(), query.getType())
                    .map(values -> values.values().isEmpty() ? new AnalyticsCountResponse(0L) : new AnalyticsCountResponse((Long) values.values().iterator().next()));
        };
    }

    private Single<AnalyticsResponse> executeGroupBy(String domain, AuditReportableCriteria criteria, Type type) {
        return auditService.aggregate(domain, criteria, type).map(AnalyticsGroupByResponse::new);
    }

    private Map<String, Object> getGenericMetadata(String value, boolean deleted) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", value);
        if (deleted) {
            metadata.put("deleted", true);
        }
        return metadata;
    }

    private static AnalyticsGroupByResponse transformKeys(AnalyticsGroupByResponse response) {
        Map<Object, Object> transformedValues = response.getValues().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> transformKey(entry.getKey()),
                        Map.Entry::getValue
                ));
        response.setValues(transformedValues);
        return response;
    }

    private static Object transformKey(Object key) {
        if (key instanceof String) {
            return switch ((String) key) {
                case USER_LOGIN -> "username login";
                case USER_WEBAUTHN_LOGIN -> "webauthn login";
                case USER_CBA_LOGIN -> "cba login";
                case USER_MAGIC_LINK_LOGIN -> "magic link login";
                default -> ((String) key).toLowerCase();
            };
        }
        return key;
    }
}

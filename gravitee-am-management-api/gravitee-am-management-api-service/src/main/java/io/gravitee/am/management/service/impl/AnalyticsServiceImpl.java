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
import io.gravitee.am.model.analytics.*;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.UserService;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private UserService userService;

    @Override
    public Single<AnalyticsResponse> execute(AnalyticsQuery query) {
        switch (query.getType()) {
            case DATE_HISTO:
                return executeDateHistogram(query);
            case GROUP_BY:
                return executeGroupBy(query);
            case COUNT:
                return executeCount(query);
        }
        return Single.just(new AnalyticsResponse() {});
    }

    private Single<AnalyticsResponse> executeDateHistogram(AnalyticsQuery query) {
        AuditReportableCriteria.Builder queryBuilder = new AuditReportableCriteria.Builder()
                .types(Collections.singletonList(query.getField().toUpperCase()));
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

    private Single<AnalyticsResponse> executeGroupBy(AnalyticsQuery query) {
        AuditReportableCriteria.Builder queryBuilder = new AuditReportableCriteria.Builder()
                .types(Collections.singletonList(query.getField().toUpperCase()));
        queryBuilder.from(query.getFrom());
        queryBuilder.to(query.getTo());
        queryBuilder.size(query.getSize());

        switch (query.getField()) {
            case Field.APPLICATION:
                // applications are group by login attempts
                queryBuilder.types(Collections.singletonList(EventType.USER_LOGIN));
                queryBuilder.status(Status.SUCCESS);
                queryBuilder.field("accessPoint.id");
                return executeGroupBy(query.getDomain(), queryBuilder.build(), query.getType())
                        .flatMap(analyticsResponse -> fetchMetadata((AnalyticsGroupByResponse) analyticsResponse));
            case Field.USER_STATUS:
            case Field.USER_REGISTRATION:
                return userService.statistics(query).map(value -> new AnalyticsGroupByResponse(value));
            default :
                return executeGroupBy(query.getDomain(), queryBuilder.build(), query.getType());
        }
    }

    private Single<AnalyticsResponse> fetchMetadata(AnalyticsGroupByResponse analyticsGroupByResponse) {
        Map<Object, Object> values = analyticsGroupByResponse.getValues();
        if (values == null && values.isEmpty()) {
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
                        .defaultIfEmpty(Collections.singletonMap((String) appId, getGenericMetadata("Deleted application", true))))
                .toList()
                .map(result -> {
                    Map<String, Map<String, Object>> metadata = result.stream()
                            .flatMap(m -> m.entrySet().stream())
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    analyticsGroupByResponse.setMetadata(metadata);
                    return analyticsGroupByResponse;
                });

    }

    private Single<AnalyticsResponse> executeCount(AnalyticsQuery query) {
        AuditReportableCriteria.Builder queryBuilder = new AuditReportableCriteria.Builder()
                .types(Collections.singletonList(query.getField().toUpperCase()));
        queryBuilder.from(query.getFrom());
        queryBuilder.to(query.getTo());
        queryBuilder.status(Status.SUCCESS);

        switch (query.getField()) {
            case Field.APPLICATION:
                return applicationService.countByDomain(query.getDomain()).map(value -> new AnalyticsCountResponse(value));
            case Field.USER:
                return userService.countByDomain(query.getDomain()).map(value -> new AnalyticsCountResponse(value));
            default :
                return auditService.aggregate(query.getDomain(), queryBuilder.build(), query.getType())
                        .map(values -> new AnalyticsCountResponse((Long) values.values().iterator().next()));
        }
    }

    private Single<AnalyticsResponse> executeGroupBy(String domain, AuditReportableCriteria criteria, Type type) {
        return auditService.aggregate(domain, criteria, type).map(values -> new AnalyticsGroupByResponse(values));
    }

    private Map<String, Object> getGenericMetadata(String value, boolean deleted) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", value);
        if (deleted) {
            metadata.put("deleted", true);
        }
        return metadata;
    }
}

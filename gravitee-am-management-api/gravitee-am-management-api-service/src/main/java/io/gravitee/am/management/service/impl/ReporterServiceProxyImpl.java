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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import io.gravitee.am.management.service.ReporterPluginService;
import io.gravitee.am.management.service.ReporterServiceProxy;
import io.gravitee.am.management.service.exception.ReporterPluginSchemaNotFoundException;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.ReporterNotFoundException;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.model.UpdateReporter;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ReporterAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ReporterServiceProxyImpl extends AbstractSensitiveProxy implements ReporterServiceProxy {

    @Autowired
    private ReporterPluginService reporterPluginService;

    @Autowired
    private io.gravitee.am.service.ReporterService reporterService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Flowable<Reporter> findAll() {
        return reporterService.findAll().flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Flowable<Reporter> findByDomain(String domain) {
        return reporterService.findByDomain(domain).flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Maybe<Reporter> findById(String id) {
        return reporterService.findById(id).flatMap(reporter -> filterSensitiveData(reporter).toMaybe());
    }

    @Override
    public Single<Reporter> createDefault(String domain) {
        return reporterService.createDefault(domain);
    }

    @Override
    public NewReporter createInternal(String domain) {
        return reporterService.createInternal(domain);
    }

    @Override
    public Single<Reporter> create(String domain, NewReporter newReporter, User principal) {
        return reporterService.create(domain, newReporter, principal)
                .flatMap(this::filterSensitiveData)
                .doOnSuccess(reporter1 -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_CREATED).reporter(reporter1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_CREATED).throwable(throwable)));
    }

    @Override
    public Single<Reporter> update(String domain, String id, UpdateReporter updateReporter, User principal) {
        return reporterService.findById(id)
                .switchIfEmpty(Single.error(new ReporterNotFoundException(id)))
                .flatMap(oldReporter -> filterSensitiveData(oldReporter)
                        .flatMap(safeOldReporter -> updateSensitiveData(updateReporter, oldReporter)
                                .flatMap(reporterToUpdate -> reporterService.update(domain, id, reporterToUpdate, principal))
                                .flatMap(this::filterSensitiveData)
                                .doOnSuccess(reporter1 -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_UPDATED).oldValue(safeOldReporter).reporter(reporter1)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_UPDATED).throwable(throwable))))
                );
    }

    @Override
    public Completable delete(String reporterId, User principal) {
        return reporterService.delete(reporterId, principal);
    }

    private Single<Reporter> filterSensitiveData(Reporter reporter) {
        return reporterPluginService.getSchema(reporter.getType())
                .switchIfEmpty(Single.error(new ReporterPluginSchemaNotFoundException(reporter.getType())))
                .map(schema -> {
                    // Duplicate the object to avoid side effect
                    var filteredEntity = new Reporter(reporter);
                    var schemaNode = objectMapper.readTree(schema);
                    var configurationNode = objectMapper.readTree(filteredEntity.getConfiguration());
                    super.filterSensitiveData(schemaNode, configurationNode, filteredEntity::setConfiguration);
                    return filteredEntity;
                });
    }

    private Single<UpdateReporter> updateSensitiveData(UpdateReporter updateReporter, Reporter oldReporter) {
        return reporterPluginService.getSchema(oldReporter.getType())
                .switchIfEmpty(Single.error(new ReporterPluginSchemaNotFoundException(oldReporter.getType())))
                .map(schema -> {
                    var updateConfig = objectMapper.readTree(updateReporter.getConfiguration());
                    var oldConfig = objectMapper.readTree(oldReporter.getConfiguration());
                    var schemaConfig = objectMapper.readTree(schema);
                    super.updateSensitiveData(updateConfig, oldConfig, schemaConfig, updateReporter::setConfiguration);
                    return updateReporter;
                });
    }
}

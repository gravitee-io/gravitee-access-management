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
import io.gravitee.am.common.event.Action;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import io.gravitee.am.management.service.ReporterPluginService;
import io.gravitee.am.management.service.ReporterServiceProxy;
import io.gravitee.am.management.service.exception.ReporterPluginSchemaNotFoundException;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.ReporterNotFoundException;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.model.UpdateReporter;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ReporterAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
    public Flowable<Reporter> findByReference(Reference reference) {
        return reporterService.findByReference(reference).flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Maybe<Reporter> findById(String id) {
        return reporterService.findById(id).flatMap(reporter -> filterSensitiveData(reporter).toMaybe());
    }

    @Override
    public Single<Reporter> createDefault(Reference reference) {
        return reporterService.createDefault(reference);
    }

    @Override
    public NewReporter createInternal(Reference reference) {
        return reporterService.createInternal(reference);
    }

    @Override
    public Single<Reporter> create(Reference reference, NewReporter newReporter, User principal, boolean system) {
        return reporterService.create(reference, newReporter, principal, system)
                .flatMap(this::filterSensitiveData)
                .doOnSuccess(reporter1 -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_CREATED).reporter(reporter1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_CREATED).reference(reference).throwable(throwable)));
    }

    @Override
    public Single<Reporter> update(Reference domain, String id, UpdateReporter updateReporter, User principal, boolean isUpgrader) {

        Supplier<ReporterAuditBuilder> baseAudit = () ->
                AuditBuilder.builder(ReporterAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.REPORTER_UPDATED)
                        .reference(domain);

        return reporterService.findById(id)
                .switchIfEmpty(Single.error(new ReporterNotFoundException(id)))
                .doOnError(throwable -> auditService.report(baseAudit.get().throwable(throwable)))
                .flatMap(oldReporter ->
                        filterSensitiveData(oldReporter)
                                .doOnError(throwable -> auditService.report(baseAudit.get().throwable(throwable)))
                                .flatMap(safeOldReporter ->
                                        updateSensitiveData(updateReporter, oldReporter)
                                                .flatMap(reporterToUpdate -> reporterService.update(domain, id, reporterToUpdate, principal, isUpgrader))
                                                .flatMap(this::filterSensitiveData)
                                                .doOnSuccess(updatedReporter -> auditService.report(baseAudit.get().oldValue(safeOldReporter).reporter(updatedReporter)))
                                                .doOnError(throwable -> auditService.report(baseAudit.get().oldValue(safeOldReporter).throwable(throwable)))
                                )
                );
    }

    @Override
    public Completable delete(String reporterId, User principal, boolean removeSystemReporter) {
        return reporterService.delete(reporterId, principal, removeSystemReporter);
    }

    @Override
    public String createReporterConfig(Reference reference) {
        return reporterService.createReporterConfig(reference);
    }

    @Override
    public Completable notifyInheritedReporters(Reference parentReference, Reference affectedReference, Action action) {
        return reporterService.notifyInheritedReporters(parentReference, affectedReference, action);
    }

    private Single<Reporter> filterSensitiveData(Reporter reporter) {
        return reporterPluginService.getSchema(reporter.getType())
                .map(Optional::ofNullable)
                .switchIfEmpty(Maybe.just(Optional.empty()))
                .toSingle()
                .map(schema -> {
                    // Duplicate the object to avoid side effect
                    var filteredEntity = new Reporter(reporter);
                    if (schema.isPresent()) {
                        var schemaNode = objectMapper.readTree(schema.get());
                        var configurationNode = objectMapper.readTree(filteredEntity.getConfiguration());
                        super.filterSensitiveData(schemaNode, configurationNode, filteredEntity::setConfiguration);
                    } else {
                        // not schema , remove all the configuration to avoid sensitive data leak
                        // this case may happen when the plugin zip file has been removed from the plugins directory
                        // (set empty object to avoid NullPointer on the UI)
                        filteredEntity.setConfiguration(DEFAULT_SCHEMA_CONFIG);
                    }
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

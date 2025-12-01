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
import io.gravitee.am.management.service.BotDetectionPluginService;
import io.gravitee.am.management.service.BotDetectionServiceProxy;
import io.gravitee.am.management.service.exception.BotDetectionPluginSchemaNotFoundException;
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.Reference;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.BotDetectionService;
import io.gravitee.am.service.exception.BotDetectionNotFoundException;
import io.gravitee.am.service.model.NewBotDetection;
import io.gravitee.am.service.model.UpdateBotDetection;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.BotDetectionAuditBuilder;
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
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class BotDetectionServiceProxyImpl extends AbstractSensitiveProxy implements BotDetectionServiceProxy {

    @Autowired
    private BotDetectionService botDetectionService;

    @Autowired
    private BotDetectionPluginService botDetectionPluginService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Maybe<BotDetection> findById(String id) {
        return botDetectionService.findById(id).flatMap(reporter -> filterSensitiveData(reporter).toMaybe());
    }

    @Override
    public Flowable<BotDetection> findByDomain(String domain) {
        return botDetectionService.findByDomain(domain).flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Single<BotDetection> create(String domain, NewBotDetection botDetection, User principal) {
        return botDetectionService.create(domain, botDetection, principal)
                .flatMap(this::filterSensitiveData)
                .doOnSuccess(detection -> auditService.report(AuditBuilder.builder(BotDetectionAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.BOT_DETECTION_CREATED)
                        .botDetection(detection)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(BotDetectionAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.BOT_DETECTION_CREATED)
                        .reference(Reference.domain(domain))
                        .throwable(throwable)));
    }

    @Override
    public Single<BotDetection> update(String domain, String id, UpdateBotDetection updateBotDetection, User principal) {

        Supplier<BotDetectionAuditBuilder> audit = () ->
                AuditBuilder.builder(BotDetectionAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.BOT_DETECTION_UPDATED)
                        .reference(Reference.domain(domain));

        return botDetectionService.findById(id)
                .switchIfEmpty(Single.error(new BotDetectionNotFoundException(id)))
                .doOnError(err -> auditService.report(audit.get().throwable(err)))
                .flatMap(oldBotDetection -> filterSensitiveData(oldBotDetection)
                        .doOnError(err -> auditService.report(audit.get().throwable(err)))
                        .flatMap(safeOldBotDetection -> updateSensitiveData(updateBotDetection, oldBotDetection)
                                .flatMap(botDetectionToUpdate -> botDetectionService.update(domain, id, botDetectionToUpdate, principal))
                                .flatMap(this::filterSensitiveData)
                                .doOnSuccess(updated -> auditService.report(audit.get().oldValue(safeOldBotDetection).botDetection(updated)))
                                .doOnError(err -> auditService.report(audit.get().oldValue(safeOldBotDetection).throwable(err)))
                        )
                );
    }


    @Override
    public Completable delete(String domain, String botDetectionId, User principal) {
        return botDetectionService.delete(domain, botDetectionId, principal);
    }

    private Single<BotDetection> filterSensitiveData(BotDetection botDetection) {
        return botDetectionPluginService.getSchema(botDetection.getType())
                .map(Optional::ofNullable)
                .switchIfEmpty(Maybe.just(Optional.empty()))
                .toSingle()
                .map(schema -> {
                    // Duplicate the object to avoid side effect
                    var filteredEntity = new BotDetection(botDetection);
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

    private Single<UpdateBotDetection> updateSensitiveData(UpdateBotDetection updateBotDetection, BotDetection oldBotDetection) {
        return botDetectionPluginService.getSchema(oldBotDetection.getType())
                .switchIfEmpty(Single.error(new BotDetectionPluginSchemaNotFoundException(oldBotDetection.getType())))
                .map(schema -> {
                    var updateConfig = objectMapper.readTree(updateBotDetection.getConfiguration());
                    var oldConfig = objectMapper.readTree(oldBotDetection.getConfiguration());
                    var schemaConfig = objectMapper.readTree(schema);
                    super.updateSensitiveData(updateConfig, oldConfig, schemaConfig, updateBotDetection::setConfiguration);
                    return updateBotDetection;
                });
    }
}

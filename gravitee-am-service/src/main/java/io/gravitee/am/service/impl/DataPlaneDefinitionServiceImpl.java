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
package io.gravitee.am.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Organization;
import io.gravitee.am.plugins.dataplane.core.DataPlanePluginManager;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.am.repository.management.api.DataPlaneDefinitionRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.dataplane.config.DataPlaneConfigHandler;
import io.gravitee.am.service.dataplane.config.DataPlaneConnectionSummary;
import io.gravitee.am.service.exception.DataPlaneDefinitionAlreadyExistsException;
import io.gravitee.am.service.exception.DataPlaneDefinitionNotFoundException;
import io.gravitee.am.service.exception.DataPlaneInUseByDomainsException;
import io.gravitee.am.service.exception.EnvironmentAlreadyBoundToDataPlaneException;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.gravitee.am.service.model.NewDataPlaneDefinition;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.DataPlaneDefinitionAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.plugins.dataplane.core.DataPlanePluginManager.PLUGIN_ID_PREFIX;
import static org.springframework.util.StringUtils.hasText;

/**
 * Persists a data plane definition. Persisting is all it does: registering the definition so that
 * domains can be served from it is the loader's job, and nothing here emits a sync event.
 *
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class DataPlaneDefinitionServiceImpl implements DataPlaneDefinitionService {

    private final DataPlaneDefinitionRepository dataPlaneDefinitionRepository;
    private final DomainRepository domainRepository;
    private final OrganizationService organizationService;
    private final EnvironmentService environmentService;
    private final DataPlanePluginManager dataPlanePluginManager;
    private final PluginConfigurationValidatorsRegistry pluginValidatorsRegistry;
    private final List<DataPlaneConfigHandler> configHandlers;
    private final AuditService auditService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataPlaneDefinitionServiceImpl(@Lazy DataPlaneDefinitionRepository dataPlaneDefinitionRepository,
                                          @Lazy DomainRepository domainRepository,
                                          OrganizationService organizationService,
                                          EnvironmentService environmentService,
                                          DataPlanePluginManager dataPlanePluginManager,
                                          PluginConfigurationValidatorsRegistry pluginValidatorsRegistry,
                                          List<DataPlaneConfigHandler> configHandlers,
                                          AuditService auditService) {
        this.dataPlaneDefinitionRepository = dataPlaneDefinitionRepository;
        this.domainRepository = domainRepository;
        this.organizationService = organizationService;
        this.environmentService = environmentService;
        this.dataPlanePluginManager = dataPlanePluginManager;
        this.pluginValidatorsRegistry = pluginValidatorsRegistry;
        this.configHandlers = configHandlers;
        this.auditService = auditService;
    }

    @Override
    public Single<DataPlaneDefinitionSummary> create(NewDataPlaneDefinition newDataPlaneDefinition) {
        log.debug("Create data plane definition {}", newDataPlaneDefinition);

        return Single.fromCallable(() -> validate(newDataPlaneDefinition))
                .flatMap(definition -> checkReferences(definition)
                        .andThen(checkIdIsFree(definition))
                        .andThen(checkEnvironmentIsFree(definition))
                        .andThen(Single.defer(() -> {
                            var now = new Date();
                            definition.setCreatedAt(now);
                            definition.setUpdatedAt(now);
                            return dataPlaneDefinitionRepository.create(definition)
                                    .onErrorResumeNext(throwable -> conflictThatLostTheRace(definition, throwable));
                        }))
                        .map(this::toSummary)
                        .doOnError(throwable -> reportCreated(toSummary(definition), throwable)))
                .doOnSuccess(summary -> reportCreated(summary, null));
    }

    private void reportCreated(DataPlaneDefinitionSummary summary, Throwable throwable) {
        auditService.report(AuditBuilder.builder(DataPlaneDefinitionAuditBuilder.class)
                .type(EventType.DATA_PLANE_CREATED)
                .dataPlane(summary)
                .throwable(throwable));
    }

    @Override
    public Flowable<DataPlaneDefinitionSummary> findAll() {
        log.debug("Find all data plane definitions");
        return dataPlaneDefinitionRepository.findAll().map(this::toSummary);
    }

    @Override
    public Single<DataPlaneDefinitionSummary> findById(String id) {
        log.debug("Find data plane definition by id: {}", id);
        return dataPlaneDefinitionRepository.findById(id)
                .map(this::toSummary)
                .switchIfEmpty(Single.error(() -> new DataPlaneDefinitionNotFoundException(id)));
    }

    @Override
    public Completable delete(String id) {
        log.debug("Delete data plane definition {}", id);
        return dataPlaneDefinitionRepository.findById(id)
                .map(this::toSummary)
                .switchIfEmpty(Single.error(() -> new DataPlaneDefinitionNotFoundException(id)))
                .flatMapCompletable(summary -> checkNoDomainUsesIt(id)
                        .andThen(Completable.defer(() -> dataPlaneDefinitionRepository.delete(id)))
                        .doOnComplete(() -> reportDeleted(summary, null))
                        .doOnError(throwable -> reportDeleted(summary, throwable)));
    }

    private Completable checkNoDomainUsesIt(String dataPlaneId) {
        return domainRepository.existsByDataPlaneId(dataPlaneId)
                .flatMapCompletable(inUse -> inUse
                        ? Completable.error(new DataPlaneInUseByDomainsException(dataPlaneId))
                        : Completable.complete());
    }

    private void reportDeleted(DataPlaneDefinitionSummary summary, Throwable throwable) {
        auditService.report(AuditBuilder.builder(DataPlaneDefinitionAuditBuilder.class)
                .type(EventType.DATA_PLANE_DELETED)
                .dataPlane(summary)
                .throwable(throwable));
    }

    /**
     * Drops the stored connection settings in favour of the credential-free summary.
     */
    private DataPlaneDefinitionSummary toSummary(DataPlaneDefinition definition) {
        return DataPlaneDefinitionSummary.of(definition, connectionSummary(definition));
    }

    private DataPlaneConnectionSummary connectionSummary(DataPlaneDefinition definition) {
        Optional<DataPlaneConfigHandler> handler = handlerFor(definition.getType());
        if (handler.isEmpty()) {
            // the plugin this row was created for is no longer deployed
            return DataPlaneConnectionSummary.UNKNOWN;
        }
        try {
            return handler.get().summarize(objectMapper.readTree(definition.getConfiguration()));
        } catch (Exception e) {
            log.warn("Data plane [{}] holds a configuration that can no longer be read", definition.getId());
            return DataPlaneConnectionSummary.UNKNOWN;
        }
    }

    private Completable checkReferences(DataPlaneDefinition definition) {
        return organizationService.findById(definition.getOrganizationId())
                .onErrorResumeNext(throwable -> Single.error(throwable instanceof OrganizationNotFoundException
                        ? new InvalidParameterException("Unknown organization [" + definition.getOrganizationId() + "]")
                        : throwable))
                .flatMap(organization -> environmentService.findById(definition.getEnvironmentId(), definition.getOrganizationId())
                        .onErrorResumeNext(throwable -> Single.error(throwable instanceof EnvironmentNotFoundException
                                ? new InvalidParameterException("Unknown environment [" + definition.getEnvironmentId() + "] for organization [" + definition.getOrganizationId() + "]")
                                : throwable)))
                .ignoreElement();
    }

    private Single<DataPlaneDefinition> conflictThatLostTheRace(DataPlaneDefinition definition, Throwable throwable) {
        return checkIdIsFree(definition)
                .andThen(checkEnvironmentIsFree(definition))
                .andThen(Single.<DataPlaneDefinition>error(throwable));
    }

    private Completable checkIdIsFree(DataPlaneDefinition definition) {
        return dataPlaneDefinitionRepository.findById(definition.getId())
                .flatMapCompletable(existing -> Completable.error(new DataPlaneDefinitionAlreadyExistsException(definition.getId())));
    }

    private Completable checkEnvironmentIsFree(DataPlaneDefinition definition) {
        return dataPlaneDefinitionRepository.findByEnvironmentId(definition.getEnvironmentId())
                .flatMapCompletable(bound -> Completable.error(new EnvironmentAlreadyBoundToDataPlaneException(definition.getEnvironmentId(), bound.getId())));
    }

    private DataPlaneDefinition validate(NewDataPlaneDefinition payload) {
        if (!hasText(payload.getId())) {
            throw new InvalidParameterException("'id' is required");
        }
        if (DataPlaneDescription.DEFAULT_DATA_PLANE_ID.equals(payload.getId())) {
            throw new InvalidParameterException("'" + DataPlaneDescription.DEFAULT_DATA_PLANE_ID + "' is reserved for the data plane declared in the gravitee.yml");
        }
        if (!hasText(payload.getName())) {
            throw new InvalidParameterException("'name' is required");
        }
        if (!hasText(payload.getType())) {
            throw new InvalidParameterException("'type' is required");
        }
        if (dataPlanePluginManager.get(PLUGIN_ID_PREFIX + payload.getType()) == null) {
            throw new InvalidParameterException("No data plane plugin is deployed for type [" + payload.getType() + "]");
        }
        validateConfiguration(payload.getType(), payload.getConfiguration());

        DataPlaneDefinition definition = new DataPlaneDefinition();
        definition.setId(payload.getId());
        definition.setName(payload.getName());
        definition.setType(payload.getType());
        definition.setGatewayUrl(payload.getGatewayUrl());
        definition.setOrganizationId(hasText(payload.getOrganizationId()) ? payload.getOrganizationId() : Organization.DEFAULT);
        definition.setEnvironmentId(hasText(payload.getEnvironmentId()) ? payload.getEnvironmentId() : Environment.DEFAULT);
        definition.setConfiguration(payload.getConfiguration().toString());
        return definition;
    }

    private void validateConfiguration(String type, JsonNode configuration) {
        if (configuration == null || !configuration.isObject()) {
            throw new InvalidParameterException("'configuration' is required and must be a JSON object");
        }
        var handler = handlerFor(type)
                .orElseThrow(() -> new InvalidParameterException("No configuration validator is available for type [" + type + "]"));

        Set<String> foreignBlocks = configHandlers.stream()
                .map(DataPlaneConfigHandler::blockName)
                .filter(block -> !block.equals(handler.blockName()))
                .filter(configuration::has)
                .collect(Collectors.toSet());
        if (!foreignBlocks.isEmpty()) {
            throw new InvalidParameterException("configuration must only declare the '" + handler.blockName() + "' block, found also: " + String.join(", ", foreignBlocks));
        }

        validateAgainstSchema(type, handler.blockName(), configuration.get(handler.blockName()));
        handler.validate(configuration);
    }

    /**
     * The plugin's {@code schema-form.json} owns the shape of the block, the {@link DataPlaneConfigHandler}
     * owns what a schema cannot express. A plugin packaged without a schema has no validator, leaving
     * the handler as the only check.
     */
    private void validateAgainstSchema(String type, String blockName, JsonNode block) {
        if (block == null) {
            // the type-specific pass reports this in its own words
            return;
        }
        pluginValidatorsRegistry.get(PLUGIN_ID_PREFIX + type)
                .map(validator -> validator.validate(block.toString()))
                .filter(result -> !result.isValid())
                .ifPresent(result -> {
                    throw new InvalidParameterException("configuration." + blockName + " is not valid: " + result.getMsg());
                });
    }

    private Optional<DataPlaneConfigHandler> handlerFor(String type) {
        return configHandlers.stream().filter(candidate -> candidate.supports(type)).findFirst();
    }
}

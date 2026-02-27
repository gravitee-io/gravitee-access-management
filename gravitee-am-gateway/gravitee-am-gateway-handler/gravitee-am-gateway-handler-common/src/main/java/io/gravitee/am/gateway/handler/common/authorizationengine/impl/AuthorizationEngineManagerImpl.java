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
package io.gravitee.am.gateway.handler.common.authorizationengine.impl;

import io.gravitee.am.authorizationengine.api.AuthorizationEngineProvider;
import io.gravitee.am.authorizationengine.api.audit.AuthorizationAuditEvent;
import io.gravitee.am.authorizationengine.api.model.AuthorizationEngineRequest;
import io.gravitee.am.authorizationengine.api.model.AuthorizationEngineResponse;
import io.gravitee.am.common.event.AuthorizationBundleEvent;
import io.gravitee.am.common.event.AuthorizationEngineEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.handler.common.authorizationengine.AuthorizationEngineManager;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.plugins.authorizationengine.core.AuthorizationEnginePluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.model.AuthorizationBundle;
import io.gravitee.am.repository.management.api.AuthorizationBundleRepository;
import io.gravitee.am.repository.management.api.AuthorizationEngineRepository;
import io.gravitee.am.repository.management.api.AuthorizationSchemaRepository;
import io.gravitee.am.repository.management.api.AuthorizationSchemaVersionRepository;
import io.gravitee.am.repository.management.api.EntityStoreRepository;
import io.gravitee.am.repository.management.api.EntityStoreVersionRepository;
import io.gravitee.am.repository.management.api.PolicySetRepository;
import io.gravitee.am.repository.management.api.PolicySetVersionRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.gateway.PermissionEvaluatedAuditBuilder;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author GraviteeSource Team
 */
public class AuthorizationEngineManagerImpl extends AbstractService implements AuthorizationEngineManager, InitializingBean, EventListener<AuthorizationEngineEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationEngineManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private AuthorizationEnginePluginManager authorizationEnginePluginManager;

    @Autowired
    private AuthorizationEngineRepository authorizationEngineRepository;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private AuthorizationBundleRepository authorizationBundleRepository;

    @Autowired
    private PolicySetRepository policySetRepository;

    @Autowired
    private PolicySetVersionRepository policySetVersionRepository;

    @Autowired
    private AuthorizationSchemaRepository authorizationSchemaRepository;

    @Autowired
    private AuthorizationSchemaVersionRepository authorizationSchemaVersionRepository;

    @Autowired
    private EntityStoreRepository entityStoreRepository;

    @Autowired
    private EntityStoreVersionRepository entityStoreVersionRepository;

    @Autowired
    private DomainReadinessService domainReadinessService;

    @Autowired
    private AuditService auditService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentMap<String, AuthorizationEngineProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> engineBundleBindings = new ConcurrentHashMap<>();

    private final EventListener<AuthorizationBundleEvent, Payload> bundleEventListener =
            event -> handleBundleEvent(event);

    @Override
    public Maybe<AuthorizationEngineProvider> get(String id) {
        AuthorizationEngineProvider provider = providers.get(id);
        return (provider != null) ? Maybe.just(provider) : Maybe.empty();
    }

    @Override
    public Maybe<AuthorizationEngineProvider> getDefault() {
        final int size = providers.size();
        if (size == 0) {
            return Maybe.empty();
        }
        if (size > 1) {
            return Maybe.error(new IllegalStateException("Multiple authorization engine providers found. Only one provider is allowed per domain."));
        }
        return Maybe.just(providers.values().iterator().next());
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing authorization engines for domain {}", domain.getName());

        authorizationEngineRepository.findByDomain(domain.getId())
                .flatMapSingle(authorizationEngine ->
                        loadAuthorizationEngine(authorizationEngine)
                                .doOnSuccess(ae ->
                                        logger.info("Authorization engine {} loaded for domain {}", ae.getName(), domain.getName()))
                                .doOnError(error ->
                                        logger.warn("Failed to load authorization engine {} for domain {}",
                                                authorizationEngine.getName(), domain.getName(), error))
                )
                .ignoreElements()
                .doOnComplete(() ->
                        logger.info("All authorization engines initialized for domain {}", domain.getName()))
                .subscribe(
                        () -> {}, // complete handled above
                        error -> {
                            logger.error("Unexpected error while initializing authorization engines for domain {}", domain.getName(), error);
                            domainReadinessService.pluginInitFailed(domain.getId(), Type.AUTHORIZATION_ENGINE.name(), error.getMessage());
                        }
                );
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for authorization engine events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, AuthorizationEngineEvent.class, domain.getId());

        logger.info("Register event listener for authorization bundle events for domain {}", domain.getName());
        eventManager.subscribeForEvents(bundleEventListener, AuthorizationBundleEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for authorization engine events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, AuthorizationEngineEvent.class, domain.getId());
        eventManager.unsubscribeForEvents(bundleEventListener, AuthorizationBundleEvent.class, domain.getId());
        clearProviders();
    }

    @Override
    public void onEvent(Event<AuthorizationEngineEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY, UPDATE -> updateAuthorizationEngine(event.content().getId(), event.type());
                case UNDEPLOY -> removeAuthorizationEngine(event.content().getId());
                default -> {
                    // No action needed for default case
                }
            }
        }
    }

    private Single<AuthorizationEngine> loadAuthorizationEngine(AuthorizationEngine authorizationEngine) {
        logger.info("Loading authorization engine: {} [{}]", authorizationEngine.getName(), authorizationEngine.getType());
        return deployProvider(authorizationEngine);
    }

    private void updateAuthorizationEngine(String authorizationEngineId, AuthorizationEngineEvent authorizationEngineEvent) {
        final String eventType = authorizationEngineEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} authorization engine event for {}",
                domain.getName(), eventType, authorizationEngineId);
        authorizationEngineRepository.findById(authorizationEngineId)
                .flatMapSingle(authorizationEngine ->
                        loadAuthorizationEngine(authorizationEngine)
                                .doOnSuccess(ae -> logger.info("Authorization engine {} {} for domain {}", authorizationEngineId, eventType, domain.getName()))
                                .doOnError(error -> logger.error("Unable to {} authorization engine for domain {}", eventType, domain.getName(), error)))
                .switchIfEmpty(Maybe.fromRunnable(() -> logger.error("No authorization engine found with id {}", authorizationEngineId)))
                .onErrorComplete(error -> {
                    logger.error("An error has occurred when {} authorization engine {} for domain {}",
                            eventType, authorizationEngineId, domain.getName(), error);
                    return true;
                })
                .ignoreElement()
                .subscribe();
    }

    private void removeAuthorizationEngine(String authorizationEngineId) {
        logger.info("Domain {} has received authorization engine event, delete authorization engine {}", domain.getName(), authorizationEngineId);
        clearProvider(authorizationEngineId);
    }

    private Single<AuthorizationEngine> deployProvider(AuthorizationEngine authorizationEngine) {
        try {
            ProviderConfiguration config = new ProviderConfiguration(authorizationEngine.getType(), authorizationEngine.getConfiguration());

            domainReadinessService.initPluginSync(domain.getId(), authorizationEngine.getId(), Type.AUTHORIZATION_ENGINE.name());
            AuthorizationEngineProvider provider = authorizationEnginePluginManager.create(config);
            clearProvider(authorizationEngine.getId());
            if (provider != null) {
                providers.put(authorizationEngine.getId(), provider);
                provider.setAuditCallback(this::reportAuditEvent);

                String bundleId = extractBundleId(authorizationEngine.getConfiguration());
                if (bundleId != null && !bundleId.isBlank()) {
                    engineBundleBindings.put(authorizationEngine.getId(), bundleId);
                } else {
                    engineBundleBindings.remove(authorizationEngine.getId());
                }

                logger.info("Authorization engine {} deployed for domain {}", authorizationEngine.getId(), domain.getName());
                domainReadinessService.pluginLoaded(domain.getId(), authorizationEngine.getId());

                pushBundleToProvider(authorizationEngine, provider);

                return Single.just(authorizationEngine);
            } else {
                String errorMsg = "Failed to create authorization engine provider";
                domainReadinessService.pluginFailed(domain.getId(), authorizationEngine.getId(), errorMsg);
                return Single.error(new IllegalStateException(errorMsg));
            }
        } catch (Exception ex) {
            logger.error("An error has occurred while loading authorization engine: {} [{}]",
                    authorizationEngine.getName(), authorizationEngine.getType(), ex);
            clearProvider(authorizationEngine.getId());
            domainReadinessService.pluginFailed(domain.getId(), authorizationEngine.getId(), ex.getMessage());
            return Single.error(ex);
        }
    }

    private void clearProviders() {
        providers.keySet().forEach(this::clearProvider);
    }

    private void clearProvider(String authorizationEngineId) {
        AuthorizationEngineProvider provider = providers.remove(authorizationEngineId);
        engineBundleBindings.remove(authorizationEngineId);

        if (provider != null) {
            try {
                domainReadinessService.pluginUnloaded(domain.getId(), authorizationEngineId);
                logger.info("Stopping authorization engine provider: {}", authorizationEngineId);
                provider.stop();
            } catch (Exception e) {
                logger.error("An error has occurred while stopping the authorization engine provider: {}", authorizationEngineId, e);
            }
        }
    }

    /**
     * Extract bundleId from the engine's plugin configuration JSON and push
     * the bundle's content to the provider. Fire-and-forget — errors are logged.
     */
    private void pushBundleToProvider(AuthorizationEngine authorizationEngine, AuthorizationEngineProvider provider) {
        String bundleId = extractBundleId(authorizationEngine.getConfiguration());
        if (bundleId == null || bundleId.isBlank()) {
            logger.debug("No bundleId configured for engine {}, skipping initial bundle push", authorizationEngine.getId());
            return;
        }

        logger.info("Pushing bundle {} to engine {} for domain {}", bundleId, authorizationEngine.getId(), domain.getName());
        authorizationBundleRepository.findById(bundleId)
                .flatMap(this::resolveBundleContent)
                .subscribe(
                        resolved -> {
                            try {
                                provider.updateConfig(resolved[0], resolved[1], resolved[2]);
                                logger.info("Bundle {} pushed to engine {} for domain {}",
                                        bundleId, authorizationEngine.getId(), domain.getName());
                            } catch (Exception e) {
                                logger.error("Failed to push bundle {} to engine {} for domain {}",
                                        bundleId, authorizationEngine.getId(), domain.getName(), e);
                            }
                        },
                        error -> logger.error("Error fetching bundle {} for engine {} in domain {}",
                                bundleId, authorizationEngine.getId(), domain.getName(), error),
                        () -> logger.warn("Bundle {} not found for engine {} in domain {}",
                                bundleId, authorizationEngine.getId(), domain.getName())
                );
    }

    private String extractBundleId(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(configuration);
            JsonNode bundleIdNode = node.get("bundleId");
            return (bundleIdNode != null && !bundleIdNode.isNull()) ? bundleIdNode.asText() : null;
        } catch (Exception e) {
            logger.warn("Failed to parse engine configuration for bundleId", e);
            return null;
        }
    }

    /**
     * Handle authorization bundle events (create/update/delete).
     * Fetches the bundle by ID and pushes its content atomically to all providers.
     */
    private void handleBundleEvent(Event<AuthorizationBundleEvent, Payload> event) {
        Payload payload = event.content();
        if (payload.getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(payload.getReferenceId())) {
            switch (event.type()) {
                case DEPLOY, UPDATE -> hotReloadFromBundle(payload.getId());
                case UNDEPLOY -> {
                    String removedBundleId = payload.getId();
                    logger.info("Authorization bundle {} undeployed for domain {}", removedBundleId, domain.getName());
                    engineBundleBindings.forEach((engineId, bId) -> {
                        if (removedBundleId.equals(bId)) {
                            AuthorizationEngineProvider provider = providers.get(engineId);
                            if (provider != null) {
                                try {
                                    provider.updateConfig(null, null, null);
                                } catch (Exception e) {
                                    logger.error("Failed to clear config for engine {} in domain {}", engineId, domain.getName(), e);
                                }
                            }
                        }
                    });
                }
                default -> {
                    // No action needed
                }
            }
        }
    }

    /**
     * Report an authorization evaluation event received from a provider (e.g., sidecar audit callback).
     * Converts the provider-agnostic {@link AuthorizationAuditEvent} into a
     * {@link PermissionEvaluatedAuditBuilder} and delegates to the gateway's audit pipeline.
     */
    private void reportAuditEvent(AuthorizationAuditEvent event) {
        try {
            AuthorizationEngineRequest request = AuthorizationEngineRequest.builder()
                    .subject(AuthorizationEngineRequest.Subject.builder()
                            .type(event.principalType())
                            .id(event.principalId())
                            .build())
                    .action(AuthorizationEngineRequest.Action.builder()
                            .name(event.action())
                            .build())
                    .resource(AuthorizationEngineRequest.Resource.builder()
                            .type(event.resourceType())
                            .id(event.resourceId())
                            .build())
                    .build();

            AuthorizationEngineResponse response = new AuthorizationEngineResponse(event.decision(), null);

            auditService.report(AuditBuilder.builder(PermissionEvaluatedAuditBuilder.class)
                    .domain(domain)
                    .decisionId(event.decisionId())
                    .request(request)
                    .response(response));

            logger.debug("Reported authorization audit event: decisionId={}, decision={}, engine={}",
                    event.decisionId(), event.decision(), event.engine());
        } catch (Exception e) {
            logger.error("Failed to report authorization audit event: decisionId={}", event.decisionId(), e);
        }
    }

    private void hotReloadFromBundle(String bundleId) {
        if (providers.isEmpty()) {
            logger.debug("No running providers to hot-reload for domain {}", domain.getName());
            return;
        }

        authorizationBundleRepository.findById(bundleId)
                .flatMap(this::resolveBundleContent)
                .subscribe(
                        resolved -> {
                            logger.info("Hot-reloading bundle {} for domain {}", bundleId, domain.getName());
                            engineBundleBindings.forEach((engineId, bId) -> {
                                if (bundleId.equals(bId)) {
                                    AuthorizationEngineProvider provider = providers.get(engineId);
                                    if (provider != null) {
                                        try {
                                            provider.updateConfig(resolved[0], resolved[1], resolved[2]);
                                        } catch (Exception e) {
                                            logger.error("Failed to hot-reload bundle for engine {} in domain {}", engineId, domain.getName(), e);
                                        }
                                    }
                                }
                            });
                        },
                        error -> logger.error("Error fetching bundle {} for hot-reload in domain {}",
                                bundleId, domain.getName(), error),
                        () -> logger.warn("Bundle {} not found for hot-reload in domain {}",
                                bundleId, domain.getName())
                );
    }

    /**
     * Resolves a bundle's component references into actual content.
     * Returns a Maybe of String[3]: [policies, entities, schema].
     * Supports pinToLatest: if true, resolves the latest version; otherwise uses the specific version from the bundle.
     */
    private Maybe<String[]> resolveBundleContent(AuthorizationBundle bundle) {
        Single<String> policiesSingle = bundle.getPolicySetId() != null
                ? resolvePolicySetContent(bundle)
                : Single.just("");

        Single<String> entitiesSingle = bundle.getEntityStoreId() != null
                ? resolveEntityStoreContent(bundle)
                : Single.just("");

        Single<String> schemaSingle = bundle.getSchemaId() != null
                ? resolveSchemaContent(bundle)
                : Single.just("");

        return Single.zip(policiesSingle, entitiesSingle, schemaSingle,
                (policies, entities, schema) -> new String[]{policies, entities, schema})
                .toMaybe();
    }

    private Single<String> resolvePolicySetContent(AuthorizationBundle bundle) {
        if (bundle.isPolicySetPinToLatest()) {
            return policySetVersionRepository.findLatestByPolicySetId(bundle.getPolicySetId())
                    .map(v -> v.getContent())
                    .defaultIfEmpty("");
        }
        return policySetVersionRepository.findByPolicySetIdAndVersion(bundle.getPolicySetId(), bundle.getPolicySetVersion())
                .map(v -> v.getContent())
                .defaultIfEmpty("");
    }

    private Single<String> resolveEntityStoreContent(AuthorizationBundle bundle) {
        if (bundle.isEntityStorePinToLatest()) {
            return entityStoreVersionRepository.findLatestByEntityStoreId(bundle.getEntityStoreId())
                    .map(v -> v.getContent())
                    .defaultIfEmpty("");
        }
        return entityStoreVersionRepository.findByEntityStoreIdAndVersion(bundle.getEntityStoreId(), bundle.getEntityStoreVersion())
                .map(v -> v.getContent())
                .defaultIfEmpty("");
    }

    private Single<String> resolveSchemaContent(AuthorizationBundle bundle) {
        if (bundle.isSchemaPinToLatest()) {
            return authorizationSchemaVersionRepository.findLatestBySchemaId(bundle.getSchemaId())
                    .map(v -> v.getContent())
                    .defaultIfEmpty("");
        }
        return authorizationSchemaVersionRepository.findBySchemaIdAndVersion(bundle.getSchemaId(), bundle.getSchemaVersion())
                .map(v -> v.getContent())
                .defaultIfEmpty("");
    }
}

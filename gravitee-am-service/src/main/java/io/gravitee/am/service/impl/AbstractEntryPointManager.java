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

import io.gravitee.am.common.event.EntrypointEvent;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.EntryPointManager;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Shared logic for the entrypoint cache: loads entrypoints on startup (scoped to the
 * organizations/environments this instance is configured to serve), reacts to entrypoint events, and
 * serves lookups from memory. The actual data access is delegated to plane-specific subclasses,
 * because the management API and the gateway expose different beans (services vs repositories).
 * Started as a lifecycle component via each node's {@code components()}; the cache is loaded in
 * {@link #doStart()}, once the data layer is ready.
 *
 * @author GraviteeSource Team
 */
public abstract class AbstractEntryPointManager extends AbstractService<EntryPointManager> implements EntryPointManager, EventListener<EntrypointEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEntryPointManager.class);

    private final ConcurrentMap<String, Entrypoint> entrypoints = new ConcurrentHashMap<>();

    private final EventManager eventManager;
    private final Node node;

    protected AbstractEntryPointManager(EventManager eventManager, Node node) {
        this.eventManager = eventManager;
        this.node = node;
    }

    /** Load all entrypoints owned by the given organization. */
    protected abstract Flowable<Entrypoint> loadOrganizationEntrypoints(String organizationId);

    /** Load all entrypoints owned by the given environment. */
    protected abstract Flowable<Entrypoint> loadEnvironmentEntrypoints(String environmentId);

    /** Enumerate every organization id (used when no scope is configured). */
    protected abstract Flowable<String> allOrganizationIds();

    /** Load a single entrypoint by id; the payload reference identifies its owning scope. */
    protected abstract Maybe<Entrypoint> findEntrypointById(String entrypointId, ReferenceType referenceType, String referenceId);

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for entrypoint events");
        eventManager.subscribeForEvents(this, EntrypointEvent.class);

        logger.info("Initializing entrypoints cache");
        loadEntrypoints().blockingAwait();
        logger.debug("Entrypoints cache initialized with {} entrypoint(s)", entrypoints.size());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        eventManager.unsubscribeForEvents(this, EntrypointEvent.class);
    }

    @Override
    public List<Entrypoint> findByOrganizationId(String organizationId) {
        return entrypoints.values().stream()
                .filter(entrypoint -> Objects.equals(entrypoint.getOrganizationId(), organizationId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Entrypoint> findByEnvironmentId(String environmentId) {
        return entrypoints.values().stream()
                .filter(entrypoint -> environmentId != null && environmentId.equals(entrypoint.getEnvironmentId()))
                .collect(Collectors.toList());
    }

    @Override
    public void onEvent(Event<EntrypointEvent, Payload> event) {
        Payload payload = event.content();
        String entrypointId = payload.getId();
        switch (event.type()) {
            case UNDEPLOY -> {
                entrypoints.remove(entrypointId);
                logger.debug("Entrypoint {} undeployed - cache now holds {} entrypoint(s)", entrypointId, entrypoints.size());
            }
            case DEPLOY, UPDATE -> reload(entrypointId, payload.getReferenceType(), payload.getReferenceId());
        }
    }

    private void reload(String entrypointId, ReferenceType referenceType, String referenceId) {
        findEntrypointById(entrypointId, referenceType, referenceId).subscribe(
                entrypoint -> {
                    if (isInScope(entrypoint)) {
                        entrypoints.put(entrypointId, entrypoint);
                        logger.debug("Entrypoint {} cached - cache now holds {} entrypoint(s)", entrypointId, entrypoints.size());
                    } else {
                        entrypoints.remove(entrypointId);
                        logger.debug("Entrypoint {} is out of scope for this instance", entrypointId);
                    }
                },
                error -> logger.error("Unable to reload entrypoint {}", entrypointId, error),
                () -> entrypoints.remove(entrypointId));
    }

    private Completable loadEntrypoints() {
        Set<String> organizationIds = configured(Node.META_ORGANIZATIONS);
        Set<String> environmentIds = configured(Node.META_ENVIRONMENTS);

        if (organizationIds.isEmpty() && environmentIds.isEmpty()) {
            return allOrganizationIds().flatMapCompletable(organizationId -> cache(loadOrganizationEntrypoints(organizationId)), false, 10);
        }

        Completable byEnvironment = Flowable.fromIterable(environmentIds).flatMapCompletable(environmentId -> cache(loadEnvironmentEntrypoints(environmentId)));
        Completable byOrganization = Flowable.fromIterable(organizationIds).flatMapCompletable(organizationId -> cache(loadOrganizationEntrypoints(organizationId)));
        return Completable.mergeArray(byEnvironment, byOrganization);
    }

    private Completable cache(Flowable<Entrypoint> source) {
        return source.doOnNext(entrypoint -> entrypoints.put(entrypoint.getId(), entrypoint)).ignoreElements();
    }

    private boolean isInScope(Entrypoint entrypoint) {
        Set<String> organizationIds = configured(Node.META_ORGANIZATIONS);
        Set<String> environmentIds = configured(Node.META_ENVIRONMENTS);

        if (organizationIds.isEmpty() && environmentIds.isEmpty()) {
            return true;
        }
        if (entrypoint.getEnvironmentId() != null && environmentIds.contains(entrypoint.getEnvironmentId())) {
            return true;
        }
        return organizationIds.contains(entrypoint.getOrganizationId());
    }

    @SuppressWarnings("unchecked")
    private Set<String> configured(String metadataKey) {
        Object value = node.metadata().get(metadataKey);
        return value instanceof Set ? (Set<String>) value : Set.of();
    }
}

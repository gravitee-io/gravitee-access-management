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
package io.gravitee.am.service;

import io.gravitee.am.model.Entrypoint;
import io.gravitee.common.service.Service;

import java.util.List;
import java.util.Optional;

/**
 * In-memory cache of entrypoints, loaded on startup and kept current through entrypoint events, so
 * lookups by organization or environment do not require a database query. Wired in both the
 * management API and the gateway.
 * <p>
 * The cache only holds entrypoints within the node's configured organization/environment scope
 * (node metadata; unscoped nodes cache everything), and {@link #findAllByEnvironmentId(String)} only
 * returns environment-scoped entrypoints — organization-level ones are not matched by it.
 *
 * @author GraviteeSource Team
 */
public interface EntryPointManager extends Service<EntryPointManager> {

    List<Entrypoint> findByOrganizationId(String organizationId);

    List<Entrypoint> findAllByEnvironmentId(String environmentId);

    /**
     * The entrypoints user-facing URLs should be built from for the given environment.
     * <p>
     * Cockpit's own access point is flagged {@code defaultEntrypoint} and the customer's overriding one
     * is not, so the override wins whenever both are present. Falls back to the full list when every
     * entrypoint is flagged default: callers dereference a URL, so an empty result would break them.
     */
    default List<Entrypoint> findByEnvironmentId(String environmentId) {
        List<Entrypoint> all = findAllByEnvironmentId(environmentId);
        List<Entrypoint> overriding = all.stream().filter(entrypoint -> !entrypoint.isDefaultEntrypoint()).toList();
        return overriding.isEmpty() ? all : overriding;
    }

    /**
     * The single entrypoint a user-facing URL should be built from for the given environment.
     * <p>
     * The lowest URL wins rather than the first in the list. Cache iteration order is unspecified and the
     * management API and gateway hold separate caches, so "the first" could differ between planes and two
     * emails for one environment could carry different hosts.
     */
    Optional<Entrypoint> findPrimaryByEnvironmentId(String environmentId);
}

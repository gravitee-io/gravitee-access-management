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

import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.model.Entrypoint;
import io.reactivex.rxjava3.core.Completable;
import io.gravitee.common.service.Service;
import io.gravitee.node.logging.NodeLoggerFactory;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;

import java.net.URI;
import java.util.Comparator;
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
     * Loads an environment's entrypoints when the cache holds none for it, completing once they are
     * cached. Read once per environment, and concurrent callers share the one read.
     */
    Completable ensureEnvironmentLoaded(String environmentId);

    /**
     * The single entrypoint a user-facing URL should be built from for the given environment.
     * <p>
     * The lowest URL wins rather than the first in the list. Cache iteration order is unspecified and the
     * management API and gateway hold separate caches, so "the first" could differ between planes and two
     * emails for one environment could carry different hosts.
     */
    Optional<Entrypoint> findPrimaryByEnvironmentId(String environmentId);

    /**
     * The entrypoint an environment should be addressed by for a caller arriving on {@code requestOrigin},
     * so an environment with several hosts answers on the one in the address bar rather than an arbitrary
     * pick. Falls back to {@link #findPrimaryByEnvironmentId(String)} when there is no request to go on or
     * nothing matches.
     * <p>
     * Only ever returns a stored entrypoint, never the caller's string: an unrecognised origin is a forged
     * {@code Host} header away from trusting somebody else's domain.
     */
    default Optional<Entrypoint> resolveForRequest(String environmentId, @Nullable String requestOrigin) {
        return matchingEntrypoint(environmentId, requestOrigin).or(() -> findPrimaryByEnvironmentId(environmentId));
    }

    private Optional<Entrypoint> matchingEntrypoint(String environmentId, @Nullable String requestOrigin) {
        if (requestOrigin == null || requestOrigin.isBlank()) {
            return Optional.empty();
        }
        // The lowest url wins rather than the first, for the same reason findPrimaryByEnvironmentId picks
        // that way: cache iteration order is unspecified, so two entrypoints sharing an origin would
        // otherwise resolve differently between planes.
        Optional<Entrypoint> matched = findAllByEnvironmentId(environmentId).stream()
                .filter(entrypoint -> sameOrigin(entrypoint.getUrl(), requestOrigin))
                .min(Comparator.comparing(Entrypoint::getUrl));
        if (matched.isEmpty()) {
            // Either a forged host or an entrypoint the environment never synced, and the two look the same
            // from here. Debug rather than warn: this also runs per webauthn request, so an environment
            // whose entrypoints never synced would warn at request rate.
            Logger logger = NodeLoggerFactory.getLogger(this.getClass());
            logger.debug("Environment {} has no entrypoint matching the request origin, falling back to its primary entrypoint", environmentId);
        }
        return matched;
    }

    private static boolean sameOrigin(String entrypointUrl, String requestOrigin) {
        String entrypointOrigin = UriBuilder.toOrigin(entrypointUrl);
        String callerOrigin = UriBuilder.toOrigin(requestOrigin);
        if (entrypointOrigin == null || callerOrigin == null) {
            return false;
        }
        return UriBuilder.sameOriginAuthority(URI.create(entrypointOrigin), URI.create(callerOrigin));
    }
}

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
package io.gravitee.am.repository.gateway.api;

import io.gravitee.am.model.ActionLease;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

import java.time.Duration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ActionLeaseRepository {

    /**
     * Acquire a lease for a given action and nodeId.
     * The lease is acquired if:
     * - The current lease for the action has expired, OR
     * - The nodeId is already the owner of the lease, OR
     * - No entry exists yet for the action
     *
     * @param action the action to acquire the lease for
     * @param nodeId the node ID requesting the lease
     * @param duration the duration of the lease
     * @return Maybe containing the ActionLease if acquired, empty otherwise
     */
    Maybe<ActionLease> acquireLease(String action, String nodeId, Duration duration);

    /**
     * Release the lease for a given action and nodeId.
     *
     * @param action the action to release the lease for
     * @param nodeId the node ID releasing the lease
     * @return Completable
     */
    Completable releaseLease(String action, String nodeId);
}

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
package io.gravitee.am.plugins.dataplane.core;

import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.reactivex.rxjava3.core.Completable;

/**
 * Decides whether a data plane's store answers with the settings it was provisioned with. Building a
 * provider proves nothing on its own: both implementations hand back a client that connects on its
 * first operation, so a wrong password or an unreachable host is only found by asking the store.
 *
 * @author GraviteeSource Team
 */
public interface DataPlaneVerifier {

    /**
     * Puts this data plane under verification and starts the check without waiting for it. Only the
     * provisioned data planes are put under it: the ones the gravitee.yml declares are the node's own
     * configuration and keep serving domains without a check.
     */
    void require(String dataPlaneId, DataPlaneProvider provider);

    /**
     * Completes once the store has answered. A data plane that was never put under verification
     * completes immediately. A check already running is joined rather than repeated, and a check that
     * already succeeded completes without another round trip.
     */
    Completable verified(String dataPlaneId);

    /** Drops what is known about an id, so that a re-provisioned one is checked again from scratch. */
    void forget(String dataPlaneId);
}

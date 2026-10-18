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
package io.gravitee.am.gateway.handler.oauth2.service.device;

import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.reactivex.rxjava3.core.Single;

import java.util.Set;

/**
 * Device authorization requests of RFC 8628, from their creation at the device authorization
 * endpoint to their redemption at the token endpoint.
 *
 * @author GraviteeSource Team
 */
public interface DeviceAuthorizationRequestService {

    /**
     * Issue a device code and a user code for the given client and requested scopes.
     */
    Single<DeviceAuthorizationRequest> register(Client client, Set<String> scopes);

    /**
     * Validity, in seconds, advertised to the device for the codes it has just been issued.
     * The application's own override when it has one, the domain setting otherwise.
     */
    int getDeviceCodeExpiryInSec(Client client);

    /**
     * Minimum delay, in seconds, the device must wait between two polls.
     * The application's own override when it has one, the domain setting otherwise.
     */
    int getPollingIntervalInSec(Client client);
}

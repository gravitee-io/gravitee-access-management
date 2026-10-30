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
import io.reactivex.rxjava3.core.Maybe;
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
     * Read a request on behalf of a polling device, applying the polling state machine.
     * The request is deleted once it reaches a terminal state, so a code can be redeemed only once.
     */
    Single<DeviceAuthorizationRequest> retrieve(String deviceCode, Client client);

    /**
     * @param userCode the code as typed by the end user, in any case and with any separator
     */
    Maybe<DeviceAuthorizationRequest> findByUserCode(String userCode);

    /**
     * Record the end user's approval so the next poll of the device returns tokens.
     *
     * Rejects a request that is no longer pending, that has expired, or that belongs to another
     * client, so that an approval cannot land on a request that moved on since the code was typed.
     */
    Single<DeviceAuthorizationRequest> approve(String deviceCode, String clientId, String subject, Set<String> scopes);

    /**
     * Record the end user's rejection so the next poll of the device returns access_denied.
     *
     * Applies the same guards as the approval, so that a request which has expired or already
     * reached a terminal state cannot be rejected either.
     */
    Single<DeviceAuthorizationRequest> deny(String deviceCode, String clientId);

    /**
     * Whether the codes of this request have lapsed. A request past expiry is still readable until
     * its retention window closes, so it can report as expired rather than as missing.
     */
    boolean isExpired(DeviceAuthorizationRequest request);

    /**
     * Whether this request is still waiting for its end user, and its codes are still valid.
     */
    boolean isPending(DeviceAuthorizationRequest request);

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

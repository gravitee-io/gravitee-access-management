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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device;

import io.gravitee.am.gateway.handler.oauth2.exception.ExpiredTokenException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;

import java.util.Optional;

/**
 * Terminal states of a device authorization run, as told to the end user by the completion page.
 * One page serves them all, so a customer keeps a single template to style and translate.
 *
 * @author GraviteeSource Team
 */
public enum DeviceFlowOutcome {

    APPROVED,
    DENIED,
    EXPIRED,
    INVALID;

    public String value() {
        return name().toLowerCase();
    }

    /**
     * The outcome a failed approval or rejection reports, empty when the failure is not one the
     * end user can be told about and the request should fail instead.
     */
    public static Optional<DeviceFlowOutcome> of(Throwable failure) {
        if (failure instanceof ExpiredTokenException) {
            return Optional.of(EXPIRED);
        }
        if (failure instanceof InvalidGrantException) {
            return Optional.of(INVALID);
        }
        return Optional.empty();
    }
}

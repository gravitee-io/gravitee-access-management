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
package io.gravitee.am.authdevice.notifier.api.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code externalInformation} carries the {@code extraData} that {@code notify()} returned, merged with the
 * gateway's own entries for the request. {@code connection} is null for a notifier that does not federate.
 */
public record ADStatusRequest(String transactionId, Map<String, Object> externalInformation, FederatedConnection connection) {

    public ADStatusRequest {
        // Not Map.copyOf: persisted JSON may hold null values.
        externalInformation = externalInformation == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(externalInformation));
    }
}

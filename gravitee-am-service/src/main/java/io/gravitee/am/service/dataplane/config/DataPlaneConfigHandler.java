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
package io.gravitee.am.service.dataplane.config;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Knows the shape of one data plane type's connection settings: what makes them valid on write, and
 * what is safe to hand back on read.
 *
 * A new data plane type ships its own handler alongside its plugin.
 *
 * @author GraviteeSource Team
 */
public interface DataPlaneConfigHandler {

    /**
     * Name of the top-level block this handler owns, e.g. {@code mongodb}.
     */
    String blockName();

    boolean supports(String type);

    /**
     * The provider plugins resolve their settings from the Spring environment and silently fall back
     * to defaults when a key is missing (a mongodb data plane with no {@code dbname} ends up on
     * {@code localhost:27017/gravitee-am}), so write time is the only place a bad definition can be
     * rejected with a caller to return the error to.
     *
     * @param configuration the {@code dataPlanes[i]} body
     * @throws io.gravitee.am.service.exception.InvalidParameterException if a required key is missing
     */
    void validate(JsonNode configuration);

    /**
     * Extracts the handful of fields that are safe to expose on a read.
     *
     * This is an allowlist on purpose. The stored blob can carry credentials in a lot of places —
     * {@code username}/{@code password}, {@code keystore.password}, {@code keystorePassword},
     * {@code keyPassword}, {@code truststore.password}, and the userinfo of a connection {@code uri}
     * — and the key surface keeps growing, so nothing is emitted unless it is named here.
     */
    DataPlaneConnectionSummary summarise(JsonNode configuration);
}

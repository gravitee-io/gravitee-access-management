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
 * @author GraviteeSource Team
 */
public interface DataPlaneConfigHandler {

    /** Name of the top-level block this handler owns, e.g. {@code mongodb}. */
    String blockName();

    boolean supports(String type);

    /**
     * Providers fall back to driver defaults for missing keys (no {@code dbname} lands on
     * {@code localhost:27017/gravitee-am}), so write time is the only place to reject a bad definition.
     */
    void validate(JsonNode configuration);

    /** Allowlist: the blob carries credentials in too many places to filter by exclusion. */
    DataPlaneConnectionSummary summarize(JsonNode configuration);
}

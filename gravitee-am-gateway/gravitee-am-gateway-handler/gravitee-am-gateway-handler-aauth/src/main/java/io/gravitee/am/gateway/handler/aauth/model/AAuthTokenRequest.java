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
package io.gravitee.am.gateway.handler.aauth.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PS token endpoint request body per AAUTH spec Section 7.1.3.
 *
 * @param resourceToken  the resource token JWT (REQUIRED)
 * @param upstreamToken  auth token from upstream authorization, for call chaining (OPTIONAL, Phase 11)
 * @param justification  Markdown string explaining why access is needed (OPTIONAL)
 * @param loginHint      hint about who to authorize (OPTIONAL)
 * @param tenant         tenant identifier (OPTIONAL)
 * @param domainHint     domain hint (OPTIONAL)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AAuthTokenRequest(
        @JsonProperty("resource_token") String resourceToken,
        @JsonProperty("upstream_token") String upstreamToken,
        @JsonProperty("justification") String justification,
        @JsonProperty("login_hint") String loginHint,
        @JsonProperty("tenant") String tenant,
        @JsonProperty("domain_hint") String domainHint
) {
}

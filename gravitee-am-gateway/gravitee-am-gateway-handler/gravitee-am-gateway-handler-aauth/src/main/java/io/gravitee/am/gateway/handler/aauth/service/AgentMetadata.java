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
package io.gravitee.am.gateway.handler.aauth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Agent Server metadata document, published at {@code /.well-known/aauth-agent.json}.
 * <p>
 * Per the AAUTH protocol specification (Agent Server Metadata section):
 * <ul>
 *   <li><code>issuer</code> — the agent server's HTTPS URL (REQUIRED)</li>
 *   <li><code>jwks_uri</code> — URL to the agent server's JSON Web Key Set (REQUIRED)</li>
 *   <li><code>client_name</code> — human-readable agent name (OPTIONAL)</li>
 *   <li><code>logo_uri</code> — agent logo URL (OPTIONAL)</li>
 *   <li><code>logo_dark_uri</code> — logo for dark backgrounds (OPTIONAL)</li>
 *   <li><code>login_endpoint</code> — third-party login URL (OPTIONAL)</li>
 *   <li><code>callback_endpoint</code> — agent's HTTPS callback endpoint (OPTIONAL)</li>
 *   <li><code>localhost_callback_allowed</code> — whether localhost callbacks are allowed (OPTIONAL, default false)</li>
 *   <li><code>tos_uri</code> — terms of service URL (OPTIONAL)</li>
 *   <li><code>policy_uri</code> — privacy policy URL (OPTIONAL)</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentMetadata(
        @JsonProperty("issuer") String issuer,
        @JsonProperty("jwks_uri") String jwksUri,
        @JsonProperty("client_name") String clientName,
        @JsonProperty("logo_uri") String logoUri,
        @JsonProperty("logo_dark_uri") String logoDarkUri,
        @JsonProperty("login_endpoint") String loginEndpoint,
        @JsonProperty("callback_endpoint") String callbackEndpoint,
        @JsonProperty("localhost_callback_allowed") boolean localhostCallbackAllowed,
        @JsonProperty("tos_uri") String tosUri,
        @JsonProperty("policy_uri") String policyUri
) {
}

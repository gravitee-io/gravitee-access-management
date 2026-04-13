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
package io.gravitee.am.gateway.handler.aauth.service.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AAUTH Person Server (PS) metadata document.
 * Served at {@code /.well-known/aauth-person.json} per the AAUTH protocol specification.
 *
 * @param issuer        the PS's HTTPS URL (placed in the {@code iss} claim of issued JWTs)
 * @param tokenEndpoint URL where agents send token requests
 * @param jwksUri       URL to the PS's JSON Web Key Set
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AAuthPSMetadata(
        @JsonProperty("issuer") String issuer,
        @JsonProperty("token_endpoint") String tokenEndpoint,
        @JsonProperty("jwks_uri") String jwksUri
) {
}

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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PS token endpoint response body per AAUTH spec Section 7.1.4.
 *
 * @param authToken the signed {@code aa-auth+jwt}
 * @param expiresIn token lifetime in seconds
 */
public record AAuthTokenResponse(
        @JsonProperty("auth_token") String authToken,
        @JsonProperty("expires_in") long expiresIn
) {
}

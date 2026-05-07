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
package io.gravitee.am.service.cimd;

import io.gravitee.am.common.oidc.ClientAuthenticationMethod;

import java.util.Set;

/**
 * Shared CIMD validation constants used by both the management-API fetcher
 * ({@link CimdMetadataFetcher}) and the gateway runtime synthesizer
 * (in {@code gravitee-am-gateway-handler-common}). Centralised here so the
 * rule set cannot drift between the two paths.
 */
public final class CimdValidationRules {

    /**
     * token_endpoint_auth_method values that are not permitted for CIMD clients,
     * because CIMD documents must not carry or imply a {@code client_secret}.
     */
    public static final Set<String> FORBIDDEN_SECRET_BASED_AUTH_METHODS = Set.of(
            ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
            ClientAuthenticationMethod.CLIENT_SECRET_POST,
            ClientAuthenticationMethod.CLIENT_SECRET_JWT
    );

    private CimdValidationRules() {
        // utility
    }
}

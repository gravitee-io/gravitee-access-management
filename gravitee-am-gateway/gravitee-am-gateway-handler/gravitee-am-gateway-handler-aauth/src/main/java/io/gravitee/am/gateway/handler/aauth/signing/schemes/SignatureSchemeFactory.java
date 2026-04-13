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
package io.gravitee.am.gateway.handler.aauth.signing.schemes;

import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatches to the correct {@link SignatureScheme} implementation by scheme name.
 * Phase 2 registers "hwk". Phase 3 adds "jwks_uri". Phase 8 adds "jwt".
 */
public class SignatureSchemeFactory {

    private final Map<String, SignatureScheme> schemes = new ConcurrentHashMap<>();

    public SignatureSchemeFactory() {
        // Phase 2: register HWK scheme
        schemes.put("hwk", new HeaderWebKeyScheme());
    }

    /**
     * Register a scheme implementation. Called by later phases to add jwks_uri, jwt, etc.
     */
    public void register(String schemeName, SignatureScheme scheme) {
        schemes.put(schemeName, scheme);
    }

    /**
     * Get the scheme implementation for the given name.
     *
     * @param schemeName the scheme name from the Signature-Key header
     * @return the scheme implementation
     * @throws SignatureVerificationException if the scheme is unknown
     */
    public SignatureScheme get(String schemeName) throws SignatureVerificationException {
        SignatureScheme scheme = schemes.get(schemeName);
        if (scheme == null) {
            throw new SignatureVerificationException("unsupported_algorithm",
                    Map.of("supported_algorithms", String.join(", ", schemes.keySet())));
        }
        return scheme;
    }
}

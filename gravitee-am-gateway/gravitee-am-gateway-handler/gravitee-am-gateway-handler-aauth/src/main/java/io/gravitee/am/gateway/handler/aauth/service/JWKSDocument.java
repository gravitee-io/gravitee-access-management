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

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;

/**
 * Wrapper around a Nimbus {@link JWKSet} providing key lookup by {@code kid}.
 *
 * @param jwkSet the parsed JWKS document
 */
public record JWKSDocument(JWKSet jwkSet) {

    /**
     * Find a key by its Key ID ({@code kid}).
     *
     * @param kid the key identifier
     * @return the matching JWK, or {@code null} if not found
     */
    public JWK findByKid(String kid) {
        return jwkSet.getKeyByKeyId(kid);
    }

    /**
     * @return the number of keys in this JWKS document
     */
    public int size() {
        return jwkSet.getKeys().size();
    }
}

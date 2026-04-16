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
package io.gravitee.am.gateway.handler.aauth.util;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utility for AAUTH public key serialization/deserialization.
 *
 * @author GraviteeSource Team
 */
public final class AAuthKeyUtils {

    private AAuthKeyUtils() {}

    /**
     * Deserialize a Base64-encoded Ed25519 public key.
     *
     * @param encoded the Base64-encoded key bytes (X.509 format)
     * @return the reconstructed public key
     * @throws InvalidKeySpecException if the key data is malformed
     */
    public static PublicKey deserializePublicKey(String encoded) throws InvalidKeySpecException {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (InvalidKeySpecException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidKeySpecException("Failed to deserialize Ed25519 public key: " + e.getMessage(), e);
        }
    }
}

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
package io.gravitee.am.certificate.api;

import io.gravitee.am.common.jwt.SignatureAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;

/**
 * Utility class for securely generating {@link java.security.Key}s.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class Keys {

    private Keys() { }

    /**
     * Creates a new SecretKey instance for use with HMAC-SHA algorithms based on the specified key byte array.
     *
     * @param bytes the key byte array
     * @return a new SecretKey instance for use with HMAC-SHA algorithms based on the specified key byte array.
     * @throws InvalidKeyException if the key byte array length is less than 256 bits (32 bytes) as mandated by the
     *                          <a href="https://tools.ietf.org/html/rfc7518#section-3.2">JWT JWA Specification
     *                          (RFC 7518, Section 3.2)</a>
     */
    public static SecretKey hmacShaKeyFor(byte[] bytes) throws InvalidKeyException {

        if (bytes == null) {
            throw new InvalidKeyException("SecretKey byte array cannot be null.");
        }

        int bitLength = bytes.length * 8;

        for (SignatureAlgorithm alg : SignatureAlgorithm.PREFERRED_HMAC_ALGS) {
            if (bitLength >= alg.getMinKeyLength()) {
                return new SecretKeySpec(bytes, alg.getJcaName());
            }
        }

        String msg = "The specified key byte array is " + bitLength + " bits which " +
                "is not secure enough for any JWT HMAC-SHA algorithm.  The JWT " +
                "JWA Specification (RFC 7518, Section 3.2) states that keys used with HMAC-SHA algorithms MUST have a " +
                "size >= 256 bits (the key size must be greater than or equal to the hash " +
                "output size).  Consider using the " + Keys.class.getName() + "#secretKeyFor(SignatureAlgorithm) method " +
                "to create a key guaranteed to be secure enough for your preferred HMAC-SHA algorithm.  See " +
                "https://tools.ietf.org/html/rfc7518#section-3.2 for more information.";
        throw new InvalidKeyException(msg);
    }

    /**
     * Creates a new SecretKey instance for use with HMAC-SHA algorithms based on the specified key byte array.
     *
     * @param bytes the key byte array
     * @return the SignatureAlgorithm of the key byte array, default to HMAC-SHA 256
     */
    public static SignatureAlgorithm hmacShaSignatureAlgorithmFor(byte[] bytes) {
        int bitLength = bytes.length * 8;

        for (SignatureAlgorithm alg : SignatureAlgorithm.PREFERRED_HMAC_ALGS) {
            if (bitLength >= alg.getMinKeyLength()) {
                return alg;
            }
        }
        return SignatureAlgorithm.HS256;
    }
}

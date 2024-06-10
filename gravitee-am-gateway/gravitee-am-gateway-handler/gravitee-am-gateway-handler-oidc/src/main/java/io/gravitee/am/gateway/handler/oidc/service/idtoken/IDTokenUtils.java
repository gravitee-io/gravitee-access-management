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
package io.gravitee.am.gateway.handler.oidc.service.idtoken;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IDTokenUtils {

    /**
     * When using the Hybrid Flow, additional hash requirements are mandatory to an ID Token returned from the Authorization Endpoint :
     * - at_hash
     *      Access Token hash value.
     *      Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII representation of the access_token value,
     *      where the hash algorithm used is the hash algorithm used in the alg Header Parameter of the ID Token's JOSE Header.
     *      For instance, if the alg is RS256, hash the access_token value with SHA-256, then take the left-most 128 bits and base64url encode them.
     *      The at_hash value is a case sensitive string.
     *      If the ID Token is issued from the Authorization Endpoint with an access_token value, which is the case for the response_type value code id_token token, this is REQUIRED;
     *      otherwise, its inclusion is OPTIONAL.
     * - c_hash
     *      Code hash value.
     *      Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII representation of the code value,
     *      where the hash algorithm used is the hash algorithm used in the alg Header Parameter of the ID Token's JOSE Header.
     *      For instance, if the alg is HS512, hash the code value with SHA-512, then take the left-most 256 bits and base64url encode them.
     *      The c_hash value is a case sensitive string.
     *      If the ID Token is issued from the Authorization Endpoint with a code, which is the case for the response_type values code id_token and code id_token token,
     *      this is REQUIRED; otherwise, its inclusion is OPTIONAL.
     * - s_hash
     *      State hash value.
     *      Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII representation
     *      of the state value, where the hash algorithm used is the hash algorithm used in the alg header parameter of
     *      the ID Token's JOSE header. For instance, if the alg is HS512, hash the state value with SHA-512, then take
     *      the left-most 256 bits and base64url encode them. The s_hash value is a case sensitive string.
     *
     * @param payload code or access token value
     * @param signingAlgorithm hash algorithm used in the alg Header Parameter of the ID Token's JOSE Header.
     * @return payload hash value
     */
    public static String generateHashValue(String payload, String signingAlgorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(signingAlgorithm);
            byte[] payloadHash = md.digest(payload.getBytes());
            byte[] leftMostHalfPayloadHash = Arrays.copyOf(payloadHash, payloadHash.length / 2);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(leftMostHalfPayloadHash);
        } catch (Exception e) {
            log.error("Unable to generate ID token claim hash value", e);
        }
        return null;
    }
}

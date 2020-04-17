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
package io.gravitee.am.gateway.handler.oidc.service.utils;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWSAlgorithm;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.nimbusds.jose.JWEAlgorithm.*;


/**
 * Related to JWA RFC - https://tools.ietf.org/html/rfc7518
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWAlgorithmUtils {

    /**
     * Unless we want specific values for id_token, userinfo, authorization and so on, we will share same settings.
     */
    private static final Set<String> SUPPORTED_SIGNING_ALG = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            JWSAlgorithm.RS256.getName(), JWSAlgorithm.RS384.getName(), JWSAlgorithm.RS512.getName(),
            JWSAlgorithm.HS256.getName(), JWSAlgorithm.HS384.getName(), JWSAlgorithm.HS512.getName()
    )));

    /**
     * https://tools.ietf.org/html/rfc7518#section-4.1
     */
    private static final Set<String> SUPPORTED_KEY_ENCRYPTION_ALG = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            //Elliptic/Edward Curve algorithm
            ECDH_ES.getName(), ECDH_ES_A128KW.getName(), ECDH_ES_A192KW.getName(), ECDH_ES_A256KW.getName(),
            //RSA algorithm
            RSA_OAEP_256.getName(),
            //Direct
            DIR.getName(),
            //AES Key wrap
            A128KW.getName(), A192KW.getName(), A256KW.getName(),
            //AES GCM
            A128GCMKW.getName(), A192GCMKW.getName(), A256GCMKW.getName(),
            //Password Base Encryption
            PBES2_HS256_A128KW.getName(), PBES2_HS384_A192KW.getName(), PBES2_HS512_A256KW.getName()

    )));

    /**
     * See https://tools.ietf.org/html/rfc7518#section-5.1
     */
    private static final Set<String> SUPPORTED_CONTENT_ENCRYPTION_ALG = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            EncryptionMethod.A128CBC_HS256.getName(), EncryptionMethod.A192CBC_HS384.getName(), EncryptionMethod.A256CBC_HS512.getName(),
            EncryptionMethod.A128GCM.getName(), EncryptionMethod.A192GCM.getName(), EncryptionMethod.A256GCM.getName()
    )));

    /**
     * @return the supported list of userinfo signing algorithm.
     */
    public static List<String> getSupportedUserinfoSigningAlg() {
        return Collections.unmodifiableList(SUPPORTED_SIGNING_ALG.stream().sorted().collect(Collectors.toList()));
    }

    /**
     * Throw InvalidClientMetadataException if null or contains unsupported userinfo signing algorithm.
     * @param signingAlg String userinfo signing algorithm to validate.
     * @return True if signingAlg is supported, false otherwise.
     */
    public static boolean isValidUserinfoSigningAlg(String signingAlg) {
        return SUPPORTED_SIGNING_ALG.contains(signingAlg);
    }

    /**
     * @return the supported list of userinfo key encryption algorithm.
     */
    public static List<String> getSupportedUserinfoResponseAlg() {
        return Collections.unmodifiableList(SUPPORTED_KEY_ENCRYPTION_ALG.stream().sorted().collect(Collectors.toList()));
    }

    /**
     * @param algorithm String userinfo key encryption algorithm to validate.
     * @return True if algorithm is supported, false otherwise.
     */
    public static boolean isValidUserinfoResponseAlg(String algorithm) {
        return SUPPORTED_KEY_ENCRYPTION_ALG.contains(algorithm);
    }

    /**
     * @return the supported list of userinfo content encryption algorithm.
     */
    public static List<String> getSupportedUserinfoResponseEnc() {
        return Collections.unmodifiableList(SUPPORTED_CONTENT_ENCRYPTION_ALG.stream().sorted().collect(Collectors.toList()));
    }

    /**
     * @return the default userinfo content encryption algorithm when userinfo_encrypted_response_alg is informed
     */
    public static String getDefaultUserinfoResponseEnc() {
        return EncryptionMethod.A128CBC_HS256.getName();
    }

    /**
     * @param algorithm String id token content encryption algorithm to validate.
     * @return True if algorithm is supported, false otherwise.
     */
    public static boolean isValidUserinfoResponseEnc(String algorithm) {
        return SUPPORTED_CONTENT_ENCRYPTION_ALG.contains(algorithm);
    }

    /**
     * @return the supported list of id token signing algorithm.
     */
    public static List<String> getSupportedIdTokenSigningAlg() {
        return Collections.unmodifiableList(SUPPORTED_SIGNING_ALG.stream().sorted().collect(Collectors.toList()));
    }

    /**
     * @param signingAlg String id token signing algorithm to validate.
     * @return True if signingAlg is supported, false otherwise.
     */
    public static boolean isValidIdTokenSigningAlg(String signingAlg) {
        return SUPPORTED_SIGNING_ALG.contains(signingAlg);
    }

    /**
     * @return the supported list of id token key encryption algorithm.
     */
    public static List<String> getSupportedIdTokenResponseAlg() {
        return Collections.unmodifiableList(SUPPORTED_KEY_ENCRYPTION_ALG.stream().sorted().collect(Collectors.toList()));
    }

    /**
     * @param algorithm String id token key encryption algorithm to validate.
     * @return True if algorithm is supported, false otherwise.
     */
    public static boolean isValidIdTokenResponseAlg(String algorithm) {
        return SUPPORTED_KEY_ENCRYPTION_ALG.contains(algorithm);
    }

    /**
     * @return the supported list of id token content encryption algorithm.
     */
    public static List<String> getSupportedIdTokenResponseEnc() {
        return Collections.unmodifiableList(SUPPORTED_CONTENT_ENCRYPTION_ALG.stream().sorted().collect(Collectors.toList()));
    }

    /**
     * @return the default id_token content encryption algorithm when id_token_encrypted_response_alg is informed
     */
    public static String getDefaultIdTokenResponseEnc() {
        return EncryptionMethod.A128CBC_HS256.getName();
    }

    /**
     * @param algorithm String id token content encryption algorithm to validate.
     * @return True if algorithm is supported, false otherwise.
     */
    public static boolean isValidIdTokenResponseEnc(String algorithm) {
        return SUPPORTED_CONTENT_ENCRYPTION_ALG.contains(algorithm);
    }

    /**
     * Authorization
     */
    /**
     * @return the supported list of authorization response signing algorithm.
     */
    public static List<String> getSupportedAuthorizationSigningAlg() {
        return Collections.unmodifiableList(SUPPORTED_SIGNING_ALG.stream().sorted().collect(Collectors.toList()));
    }

    /**
     * Throw InvalidClientMetadataException if null or contains unsupported authorization response signing algorithm.
     * @param signingAlg String authorization response signing algorithm to validate.
     * @return True if signingAlg is supported, false otherwise.
     */
    public static boolean isValidAuthorizationSigningAlg(String signingAlg) {
        return SUPPORTED_SIGNING_ALG.contains(signingAlg);
    }

    /**
     * @return the supported list of authorization response key encryption algorithm.
     */
    public static List<String> getSupportedAuthorizationResponseAlg() {
        return Collections.unmodifiableList(SUPPORTED_KEY_ENCRYPTION_ALG.stream().sorted().collect(Collectors.toList()));
    }

    /**
     * @param algorithm String authorization response key encryption algorithm to validate.
     * @return True if algorithm is supported, false otherwise.
     */
    public static boolean isValidAuthorizationResponseAlg(String algorithm) {
        return SUPPORTED_KEY_ENCRYPTION_ALG.contains(algorithm);
    }

    /**
     * @return the supported list of authorization response content encryption algorithm.
     */
    public static List<String> getSupportedAuthorizationResponseEnc() {
        return Collections.unmodifiableList(SUPPORTED_CONTENT_ENCRYPTION_ALG.stream().sorted().collect(Collectors.toList()));
    }

    /**
     * @return the default authorization response content encryption algorithm when authorization_encrypted_response_alg is informed
     */
    public static String getDefaultAuthorizationResponseEnc() {
        return EncryptionMethod.A128CBC_HS256.getName();
    }

    /**
     * @param algorithm String authorization response content encryption algorithm to validate.
     * @return True if algorithm is supported, false otherwise.
     */
    public static boolean isValidAuthorizationResponseEnc(String algorithm) {
        return SUPPORTED_CONTENT_ENCRYPTION_ALG.contains(algorithm);
    }
}

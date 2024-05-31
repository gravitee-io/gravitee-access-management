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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.nimbusds.jose.JWEAlgorithm.A128GCMKW;
import static com.nimbusds.jose.JWEAlgorithm.A128KW;
import static com.nimbusds.jose.JWEAlgorithm.A192GCMKW;
import static com.nimbusds.jose.JWEAlgorithm.A192KW;
import static com.nimbusds.jose.JWEAlgorithm.A256GCMKW;
import static com.nimbusds.jose.JWEAlgorithm.A256KW;
import static com.nimbusds.jose.JWEAlgorithm.DIR;
import static com.nimbusds.jose.JWEAlgorithm.ECDH_ES;
import static com.nimbusds.jose.JWEAlgorithm.ECDH_ES_A128KW;
import static com.nimbusds.jose.JWEAlgorithm.ECDH_ES_A192KW;
import static com.nimbusds.jose.JWEAlgorithm.ECDH_ES_A256KW;
import static com.nimbusds.jose.JWEAlgorithm.PBES2_HS256_A128KW;
import static com.nimbusds.jose.JWEAlgorithm.PBES2_HS384_A192KW;
import static com.nimbusds.jose.JWEAlgorithm.PBES2_HS512_A256KW;
import static com.nimbusds.jose.JWEAlgorithm.RSA_OAEP;
import static com.nimbusds.jose.JWEAlgorithm.RSA_OAEP_256;
import static com.nimbusds.jose.JWSAlgorithm.ES256;
import static com.nimbusds.jose.JWSAlgorithm.ES384;
import static com.nimbusds.jose.JWSAlgorithm.ES512;
import static com.nimbusds.jose.JWSAlgorithm.HS256;
import static com.nimbusds.jose.JWSAlgorithm.HS384;
import static com.nimbusds.jose.JWSAlgorithm.HS512;
import static com.nimbusds.jose.JWSAlgorithm.PS256;
import static com.nimbusds.jose.JWSAlgorithm.PS384;
import static com.nimbusds.jose.JWSAlgorithm.PS512;
import static com.nimbusds.jose.JWSAlgorithm.RS256;
import static com.nimbusds.jose.JWSAlgorithm.RS384;
import static com.nimbusds.jose.JWSAlgorithm.RS512;


/**
 * Related to <a href="https://tools.ietf.org/html/rfc7518">JWA RFC</a>
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JWAlgorithmUtils {

    /**
     * Unless we want specific values for id_token, userinfo, authorization and so on, we will share same settings.
     */
    private static final Set<String> SUPPORTED_SIGNING_ALG = Set.of(
            ES256.getName(), ES384.getName(), ES512.getName(),
            PS256.getName(), PS384.getName(), PS512.getName(),
            RS256.getName(), RS384.getName(), RS512.getName(),
            HS256.getName(), HS384.getName(), HS512.getName());

    public static boolean isSignAlgCompliantWithFapi(String alg) {
        return PS256.getName().equals(alg) || ES256.getName().equals(alg);
    }


    /**
     * <a href="https://tools.ietf.org/html/rfc7518#section-4.1">section-4.1</a>
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

    public static boolean isKeyEncCompliantWithFapiBrazil(String enc) {
        // RSA_OAP_256 should be used but FAPI Brazil specify RSA_OAEP
        return RSA_OAEP.getName().equals(enc) || RSA_OAEP_256.getName().equals(enc);
    }

    /**
     * See <a href="https://tools.ietf.org/html/rfc7518#section-5.1">section-5.1</a>
     */
    private static final Set<String> SUPPORTED_CONTENT_ENCRYPTION_ALG = Set.of(EncryptionMethod.A128CBC_HS256.getName(), EncryptionMethod.A192CBC_HS384.getName(), EncryptionMethod.A256CBC_HS512.getName(), EncryptionMethod.A128GCM.getName(), EncryptionMethod.A192GCM.getName(), EncryptionMethod.A256GCM.getName());

    public static boolean isContentEncCompliantWithFapiBrazil(String enc) {
        return EncryptionMethod.A256GCM.getName().equals(enc);
    }

    /**
     * @return the supported list of userinfo signing algorithm.
     */
    public static List<String> getSupportedUserinfoSigningAlg() {
        return SUPPORTED_SIGNING_ALG.stream().sorted().toList();
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
        return SUPPORTED_KEY_ENCRYPTION_ALG.stream().sorted().toList();
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
        return SUPPORTED_CONTENT_ENCRYPTION_ALG.stream().sorted().toList();
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
        return SUPPORTED_SIGNING_ALG.stream().sorted().toList();
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
        return SUPPORTED_KEY_ENCRYPTION_ALG.stream().sorted().toList();
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
        return SUPPORTED_CONTENT_ENCRYPTION_ALG.stream().sorted().toList();
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
     * @return the supported list of authorization response signing algorithm.
     */
    public static List<String> getSupportedAuthorizationSigningAlg() {
        return SUPPORTED_SIGNING_ALG.stream().sorted().toList();
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
        return SUPPORTED_KEY_ENCRYPTION_ALG.stream().sorted().toList();
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
        return SUPPORTED_CONTENT_ENCRYPTION_ALG.stream().sorted().toList();
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

    /**
     * @return the supported list of token introspection signing algorithm.
     */
    public static List<String> getSupportedIntrospectionEndpointAuthSigningAlg() {
        return SUPPORTED_SIGNING_ALG.stream().sorted().toList();
    }

    /**
     * @return the supported list of request object signing algorithm.
     */
    public static List<String> getSupportedRequestObjectSigningAlg() {
        return SUPPORTED_SIGNING_ALG.stream().sorted().toList();
    }

    /**
     * @return the supported list of token endpoint auth signing algorithm.
     */
    public static List<String> getSupportedTokenEndpointAuthSigningAlg() {
        return SUPPORTED_SIGNING_ALG.stream().sorted().toList();
    }

    public static boolean isValidRequestObjectSigningAlg(String algorithm) {
        return SUPPORTED_SIGNING_ALG.contains(algorithm);
    }

    public static List<String> getSupportedRequestObjectAlg() {
        return SUPPORTED_KEY_ENCRYPTION_ALG.stream().sorted().toList();
    }

    public static boolean isValidRequestObjectAlg(String algorithm) {
        return SUPPORTED_KEY_ENCRYPTION_ALG.contains(algorithm);
    }

    public static boolean isValidRequestObjectEnc(String algorithm) {
        return SUPPORTED_CONTENT_ENCRYPTION_ALG.contains(algorithm);
    }

    public static List<String> getSupportedRequestObjectEnc() {
        return SUPPORTED_CONTENT_ENCRYPTION_ALG.stream().sorted().toList();
    }

    /**
     * @return the default userinfo content encryption algorithm when userinfo_encrypted_response_alg is informed
     */
    public static String getDefaultRequestObjectEnc() {
        return EncryptionMethod.A128CBC_HS256.getName();
    }

    public static List<String> getSupportedBackchannelAuthenticationSigningAl() {
        return SUPPORTED_SIGNING_ALG.stream().sorted().toList();
    }
}

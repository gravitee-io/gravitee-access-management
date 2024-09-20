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
package io.gravitee.am.common.oauth2;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

/**
 * See <a href="https://tools.ietf.org/html/rfc7636#section-4.2>4.2. Client Creates the Code Challenge</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
@Getter
public enum CodeChallengeMethod {
    /**
     * code_challenge = code_verifier
     */
    PLAIN("plain") {
        @Override
        public String getChallenge(String verifier) {
            return verifier;
        }
    },
    /**
     * code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))
     */
    S256("S256") {
        @Override
        public String getChallenge(String codeVerifier) {
            try {
                byte[] bytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest(bytes));
            } catch (NoSuchAlgorithmException e) {
                // this should never happen, all implementations of Java platform are required to support SHA-256
                throw new IllegalStateException("Sha256 algorithm not supported", e);
            }
        }
    };

    private final String uriValue;

    public static CodeChallengeMethod fromUriParam(String codeChallengeMethod) {
        for (var value : values()) {
            if (value.uriValue.equalsIgnoreCase(codeChallengeMethod)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Generate the code challenge from the given verifier
     */
    public abstract String getChallenge(String verifier);

    public static List<String> supportedValues() {
        return Stream.of(values()).map(CodeChallengeMethod::getUriValue).toList();
    }
}

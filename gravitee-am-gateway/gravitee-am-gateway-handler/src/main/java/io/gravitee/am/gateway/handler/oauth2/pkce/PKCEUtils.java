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
package io.gravitee.am.gateway.handler.oauth2.pkce;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class PKCEUtils {

    // https://tools.ietf.org/html/rfc7636#section-4.1
    public static final int PKCE_CODE_VERIFIER_MIN_LENGTH = 43;
    public static final int PKCE_CODE_VERIFIER_MAX_LENGTH = 128;

    // https://tools.ietf.org/html/rfc7636#section-4.2
    private static final int PKCE_CODE_CHALLENGE_MIN_LENGTH = 43;
    private static final int PKCE_CODE_CHALLENGE_MAX_LENGTH = 128;

    private static final Pattern VALID_CODE_VERIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9\\-\\._~]+$");
    private static final Pattern CODE_CHALLENGE_PATTERN = Pattern.compile("^[A-Za-z0-9\\-\\._~]+$");

    /**
     * Check that code challenge is valid
     * <a href="https://tools.ietf.org/html/rfc7636#section-4"></a>
     *
     * @param codeChallenge Code challenge to validate
     * @return
     */
    public static boolean validCodeChallenge(String codeChallenge) {
        return !(codeChallenge.length() < PKCE_CODE_CHALLENGE_MIN_LENGTH
                || codeChallenge.length() > PKCE_CODE_CHALLENGE_MAX_LENGTH
                || !CODE_CHALLENGE_PATTERN.matcher(codeChallenge).matches());
    }

    /**
     * Check that code verifier is valid
     * <a href="https://tools.ietf.org/html/rfc7636#section-4.2"></a>
     *
     * @param codeVerifier Code verifier to validate
     * @return
     */
    public static boolean validCodeVerifier(String codeVerifier) {
        return !(codeVerifier.length() < PKCE_CODE_VERIFIER_MIN_LENGTH
                || codeVerifier.length() > PKCE_CODE_VERIFIER_MAX_LENGTH
                || !VALID_CODE_VERIFIER_PATTERN.matcher(codeVerifier).matches());
    }

    /**
     * This method generate an S256 code challenge from the given code verifier.
     * <a href="https://tools.ietf.org/html/rfc7636#section-4.6"></a>
     *
     * @param codeVerifier
     * @return
     * @throws Exception
     */
    public static String getS256CodeChallenge(String codeVerifier) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest(codeVerifier.getBytes()));
    }
}

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
package io.gravitee.am.gateway.handler.oidc.utils;

import com.nimbusds.jose.JWSAlgorithm;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class SigningAlgorithmUtils {

    private static final Set<String> SUPPORTED_USERINFO_SIGNING_ALG = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            JWSAlgorithm.RS256.getName(), JWSAlgorithm.RS384.getName(), JWSAlgorithm.RS512.getName()
    )));

    private static final Set<String> SUPPORTED_ID_TOKEN_SIGNING_ALG = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            JWSAlgorithm.RS256.getName(), JWSAlgorithm.RS512.getName(), JWSAlgorithm.HS512.getName()
    )));

    /**
     * Return the supported list of userinfo signing algorithm.
     * @return
     */
    public static List<String> getSupportedUserinfoSigningAlg() {
        return Collections.unmodifiableList(SUPPORTED_USERINFO_SIGNING_ALG.stream().collect(Collectors.toList()));
    }

    /**
     * Throw InvalidClientMetadataException if null or contains unsupported userinfo signing algorithm.
     * @param signingAlg String userinfo signing algorithm to validate.
     */
    public static boolean isValidUserinfoSigningAlg(String signingAlg) {
        return SUPPORTED_USERINFO_SIGNING_ALG.contains(signingAlg);
    }

    /**
     * Return the supported list of id token signing algorithm.
     * @return
     */
    public static List<String> getSupportedIdTokenSigningAlg() {
        return Collections.unmodifiableList(SUPPORTED_ID_TOKEN_SIGNING_ALG.stream().collect(Collectors.toList()));
    }

    /**
     * Throw InvalidClientMetadataException if null or contains unsupported id token signing algorithm.
     * @param signingAlg String id token signing algorithm to validate.
     */
    public static boolean isValidIdTokenSigningAlg(String signingAlg) {
        return SUPPORTED_ID_TOKEN_SIGNING_ALG.contains(signingAlg);
    }
}

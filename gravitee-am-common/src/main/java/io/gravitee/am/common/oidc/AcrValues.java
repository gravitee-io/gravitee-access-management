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
package io.gravitee.am.common.oidc;

import java.util.Arrays;
import java.util.List;

/**
 *  Authentication Context Class References
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AcrValues {

    /**
     * It is the intention of InCommon that an institution with an identity assurance profile of bronze could reasonably be mapped to what NIST SP 800-63 defines as level of assurance one.
     *
     * Level 1 -  Although there is no identity proofing requirement at this level, the authentication mechanism provides some assurance that the same Claimant who participated in previous transactions is accessing the protected transaction or data.
     * It allows a wide range of available authentication technologies to be employed and permits the use of any of the token methods of Levels 2, 3, or 4.
     * Successful authentication requires that the Claimant prove through a secure authentication protocol that he or shepossesses and controls the token.
     * Plaintext passwords or secrets are not transmitted across a network at Level 1.
     * However this level does not require cryptographic methods that block offline attacks byeavesdroppers.
     * For example, simple password challenge-response protocols are allowed.
     * In many cases an eavesdropper, having intercepted such a protocol exchange, will be able to find the password with a straightforward dictionary attack.
     * At Level 1, long-term shared authentication secrets may be revealed to Verifiers.
     * At Level 1, assertions and assertion references require protection from manufacture/modification and reuse attacks
     *
     * See https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-63-2.pdf
     */
    String IN_COMMON_BRONZE = "urn:mace:incommon:iap:bronze";

    /**
     * It is the intention of InCommon that an institution with an identity assurance profile of bronze could reasonably be mapped to what NIST SP 800-63 defines as level of assurance two.
     *
     * Level 2 – Level 2 provides single factor remote network authentication.
     * At Level 2, identity proofing requirements are introduced, requiring presentation of identifying materials or information. A wide range of available authentication technologies can be employed at Level 2.
     * For single factor authentication, Memorized Secret Tokens, Pre-Registered Knowledge Tokens, Look-up Secret Tokens, Out of Band Tokens, and Single Factor One-Time Password Devices are allowed at Level 2.
     * Level 2 also permits any of the token methods of Levels 3 or 4.
     * Successful authentication requires that the Claimant prove through a secure authentication protocol that he or she controls the token.
     * Online guessing, replay, session hijacking, and eavesdropping attacks are resisted.
     * Protocols are also required to be at least weakly resistant to man-in-the middle attacks as defined in Section 8.2.2.
     * Long-term shared authentication secrets, if used, are never revealed to any other party except Verifiers operated by the Credential Service Provider (CSP); however, session (temporary) shared secrets may be provided to independent Verifiers by the CSP.
     * In addition to Level 1 requirements, assertions are resistant to disclosure, redirection, capture and substitution attacks.
     * Approved cryptographic techniques are required for all assertion protocols used at Level 2 and above.
     */
    String IN_COMMON_SILVER = "urn:mace:incommon:iap:silver";

    static List<String> values() {
        return Arrays.asList(IN_COMMON_BRONZE, IN_COMMON_SILVER);
    }
}

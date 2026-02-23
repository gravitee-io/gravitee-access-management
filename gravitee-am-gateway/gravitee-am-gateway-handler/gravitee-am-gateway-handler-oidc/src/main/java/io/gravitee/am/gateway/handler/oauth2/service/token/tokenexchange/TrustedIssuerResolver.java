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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange;

import com.nimbusds.jwt.JWTClaimsSet;
import io.gravitee.am.model.TrustedIssuer;

/**
 * Resolves key material for trusted external issuers and verifies JWTs against it.
 *
 * @author GraviteeSource Team
 */
public interface TrustedIssuerResolver {

    /**
     * Resolve the issuer's key material and verify the JWT signature.
     *
     * @param rawToken the raw JWT string
     * @param trustedIssuer the trusted issuer configuration
     * @return the verified JWT claims set
     * @throws io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException if verification fails
     * @throws IllegalArgumentException if the issuer configuration is invalid
     */
    JWTClaimsSet resolve(String rawToken, TrustedIssuer trustedIssuer);
}

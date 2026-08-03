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
package io.gravitee.am.gateway.handler.common.dpop;

import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Single;

import java.util.Set;

/**
 * Centralized validation of DPoP proof JWTs (RFC 9449).
 *
 * <p>A single component so the token endpoint and the resource-enforcement path run identical
 * checks. Both entry points read the {@code DPoP} proof header from the request, verify the proof
 * against the public key embedded in its {@code jwk} header, and compute the RFC 7638 JWK SHA-256
 * thumbprint ({@code jkt}) of that key.</p>
 *
 * <p>Every failure is signalled with
 * {@link io.gravitee.am.common.exception.oauth2.InvalidDPoPProofException} carried on the returned
 * {@link Single}. Callers map that to the appropriate HTTP response (400 at the token endpoint,
 * 401 with a {@code DPoP} challenge at a resource).</p>
 *
 * @author GraviteeSource Team
 */
public interface DPoPProofValidator {

    /**
     * Token-endpoint mode: validate the DPoP proof carried by the request and return the computed
     * JWK SHA-256 thumbprint ({@code jkt}) to bind the issued access token to. No {@code ath} or
     * {@code cnf} comparison is performed (there is no access token yet). A proof whose {@code alg}
     * is outside {@code allowedAlgorithms} is rejected (RFC 9449).
     *
     * @param request           the current request; must carry exactly one {@code DPoP} header
     * @param allowedAlgorithms the accepted signing-algorithm names; {@code null} accepts the full base set
     * @return the base64url-encoded JWK SHA-256 thumbprint of the proof key
     */
    Single<String> validateForToken(Request request, Set<String> allowedAlgorithms);

    /**
     * Resource mode: validate the DPoP proof carried by the request, additionally checking that the
     * proof's {@code ath} claim matches the presented access token and that the proof key's
     * thumbprint equals the token's {@code cnf.jkt} binding.
     *
     * @param request            the current request; must carry exactly one {@code DPoP} header
     * @param accessToken        the access token string presented under the {@code DPoP} scheme
     * @param expectedThumbprint the {@code jkt} the token is bound to (its {@code cnf.jkt})
     * @return the base64url-encoded JWK SHA-256 thumbprint of the proof key (equal to {@code expectedThumbprint})
     */
    Single<String> validateForResource(Request request, String accessToken, String expectedThumbprint);

    /**
     * Refresh mode: validate the DPoP proof carried by a refresh request and additionally check that
     * the proof key's thumbprint equals the {@code jkt} the refresh token is bound to. No {@code ath}
     * comparison is performed (there is no presented access token at the token endpoint), which is
     * what distinguishes this from {@link #validateForResource}; the thumbprint assertion is what
     * distinguishes it from {@link #validateForToken}. A proof whose {@code alg} is outside
     * {@code allowedAlgorithms} is rejected (RFC 9449).
     *
     * @param request            the current request; must carry exactly one {@code DPoP} header
     * @param expectedThumbprint the {@code jkt} the refresh token is bound to (its stored binding)
     * @param allowedAlgorithms  the accepted signing-algorithm names; {@code null} accepts the full base set
     * @return the base64url-encoded JWK SHA-256 thumbprint of the proof key (equal to {@code expectedThumbprint})
     */
    Single<String> validateForRefresh(Request request, String expectedThumbprint, Set<String> allowedAlgorithms);
}

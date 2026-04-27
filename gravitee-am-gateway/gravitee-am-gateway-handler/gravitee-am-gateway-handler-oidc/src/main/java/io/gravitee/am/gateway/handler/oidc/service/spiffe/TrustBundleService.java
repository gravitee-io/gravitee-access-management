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
package io.gravitee.am.gateway.handler.oidc.service.spiffe;

import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.TrustDomain;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Resolves JWT-SVID signing keys for a {@link TrustDomain}. Keys are fetched from
 * the bundle source configured on the trust domain (JWKS URL or static), cached
 * with the domain's refresh interval, and re-fetched on a {@code kid} miss.
 */
public interface TrustBundleService {

    Maybe<JWKSet> getKeys(TrustDomain trustDomain);

    Maybe<JWK> getKey(TrustDomain trustDomain, String kid);

    /**
     * Drop the cached bundle for {@code trustDomainId}. Called on management events
     * or by the validator after a kid miss to force a re-fetch on next access.
     */
    void evict(String trustDomainId);
}

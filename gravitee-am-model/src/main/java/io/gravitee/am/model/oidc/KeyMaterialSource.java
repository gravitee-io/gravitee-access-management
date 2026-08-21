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
package io.gravitee.am.model.oidc;

/**
 * Where the signing keys of a {@link TrustDomain} come from. All sources are legal for every
 * {@link TrustDomainKind}.
 *
 * @author GraviteeSource Team
 */
public enum KeyMaterialSource {
    /**
     * Standard JWKS endpoint, fetched and cached on a configurable refresh interval.
     */
    JWKS_URL,

    /**
     * Inline JWK set supplied directly in the trusted domain configuration.
     */
    JWK_SET,

    /**
     * Inline PEM-encoded X.509 certificate holding a single signing key.
     */
    PEM
}

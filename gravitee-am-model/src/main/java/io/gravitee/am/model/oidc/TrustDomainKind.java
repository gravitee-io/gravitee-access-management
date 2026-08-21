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
 * The kind of external trust a {@link TrustDomain} represents. A trusted domain is exactly one
 * kind and carries only that kind's settings.
 *
 * @author GraviteeSource Team
 */
public enum TrustDomainKind {
    /**
     * SPIFFE trust domain, identified by its trust-domain name, used to validate JWT-SVIDs
     * presented as client assertions.
     */
    SPIFFE,

    /**
     * Token-exchange trusted domain, identified by the {@code iss} claim it vouches for, used to
     * validate external subject and actor tokens during an RFC 8693 exchange.
     */
    TOKEN_EXCHANGE;

    /**
     * Reads a stored discriminator. Trusted domains stored before the discriminator existed carry
     * none and are SPIFFE.
     */
    public static TrustDomainKind fromStored(String stored) {
        return stored != null ? valueOf(stored) : SPIFFE;
    }
}

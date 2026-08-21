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
package io.gravitee.am.gateway.handler.oidc.service.trustdomain;

import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.common.service.Service;

import java.util.Optional;

/**
 * Holds the security domain's trusted domains in memory and keeps them current from management
 * events, so that resolving trust on a token request never reads the database.
 *
 * @author GraviteeSource Team
 */
public interface TrustDomainManager extends Service {

    /**
     * The SPIFFE trusted domain registered under this name, if any.
     */
    Optional<TrustDomain> findSpiffeByName(String name);

    /**
     * The token-exchange trusted domain vouching for this issuer, if any.
     */
    Optional<TrustDomain> findByIssuer(String issuer);
}

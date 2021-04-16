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
package io.gravitee.am.gateway.reactor;

import io.gravitee.am.model.Domain;
import java.util.Collection;

/**
 * This manager interface acts as a bridge between the source of {@link Domain} (sync scheduler when using the sync mode)
 * and the {@link io.gravitee.am.gateway.reactor.Reactor}
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface SecurityDomainManager {
    /**
     * Deploy a security domain.
     * @param domain security domain to deploy.
     */
    void deploy(Domain domain);

    /**
     * Update a security domain already registered.
     * @param domain security domain to update.
     */
    void update(Domain domain);

    /**
     * Undeploy a security domain from the {@link io.gravitee.am.gateway.reactor.Reactor}.
     * @param domainId The ID of the security domain to undeploy.
     */
    void undeploy(String domainId);

    /**
     * Returns a collection of deployed {@link Domain}s.
     * @return A collection of deployed  {@link Domain}s.
     */
    Collection<Domain> domains();

    /**
     * Retrieve a deployed {@link Domain} using its ID.
     * @param domainId The ID of the deployed security domain.
     * @return A deployed {@link Domain}
     */
    Domain get(String domainId);
}

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
package io.gravitee.am.gateway.core.manager;

import java.util.Collection;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface EntityManager<T> {
    /**
     * Deploy an entity
     * @param entity entity to deploy.
     */
    void deploy(T entity);

    /**
     * Update a entity already registered.
     * @param entity entity to update.
     */
    void update(T entity);

    /**
     * Undeploy a entity
     * @param entityId The ID of the entity to undeploy.
     */
    void undeploy(String entityId);

    /**
     * Returns a collection of deployed {@link T}s.
     * @return A collection of deployed  {@link T}s.
     */
    Collection<T> entities();

    /**
     * Retrieve a deployed {@link T} using its ID.
     * @param entityId The ID of the deployed entity.
     * @return A deployed {@link T}
     */
    T get(String entityId);
}

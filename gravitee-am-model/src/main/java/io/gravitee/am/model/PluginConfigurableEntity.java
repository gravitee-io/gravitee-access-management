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
package io.gravitee.am.model;

/**
 * Interface for entities that have plugin-based configuration.
 * Entities implementing this interface can be used with AbstractSensitiveProxy
 * for filtering and updating sensitive data.
 *
 * @param <T> The concrete type of the entity implementing this interface (self-referential type)
 * @author GraviteeSource Team
 */
public interface PluginConfigurableEntity<T extends PluginConfigurableEntity<T>> {

    /**
     * Get the plugin type identifier.
     *
     * @return the plugin type
     */
    String getType();

    /**
     * Get the configuration as a JSON string.
     *
     * @return the configuration JSON string
     */
    String getConfiguration();

    /**
     * Set the configuration from a JSON string.
     *
     * @param configuration the configuration JSON string
     */
    void setConfiguration(String configuration);

    /**
     * Create a copy of this entity.
     *
     * @return a new instance that is a copy of this entity
     */
    T copy();
}


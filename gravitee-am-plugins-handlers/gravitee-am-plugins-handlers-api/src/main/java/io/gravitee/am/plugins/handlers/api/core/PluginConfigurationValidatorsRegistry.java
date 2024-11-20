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
package io.gravitee.am.plugins.handlers.api.core;

import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class PluginConfigurationValidatorsRegistry {
    private final Map<String, PluginConfigurationValidator> validators = new ConcurrentHashMap<>();

    public void put(PluginConfigurationValidator validator){
        this.validators.put(validator.getPluginIdentifier(), validator);
    }

    public PluginConfigurationValidator get(String id){
        return validators.get(id);
    }

    public boolean contains(String id){
        return validators.containsKey(id);
    }
}

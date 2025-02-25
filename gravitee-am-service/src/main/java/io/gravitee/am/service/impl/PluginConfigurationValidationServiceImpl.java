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

package io.gravitee.am.service.impl;


import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.am.service.PluginConfigurationValidationService;
import io.gravitee.am.service.exception.InvalidPluginConfigurationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@RequiredArgsConstructor
public class PluginConfigurationValidationServiceImpl implements PluginConfigurationValidationService {
    private final PluginConfigurationValidatorsRegistry pluginValidatorsRegistry;

    @Override
    public void validate(String pluginType, String configuration) {
        pluginValidatorsRegistry.get(pluginType)
                .ifPresent(pluginValidator -> {
                    final var result = pluginValidator.validate(configuration);
                    if (!result.isValid()) {
                        throw InvalidPluginConfigurationException.fromValidationError(result.getMsg());
                    }
                });
    }
}

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
package io.gravitee.am.plugins.handlers.api.core.impl;

import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationEvaluator;
import lombok.RequiredArgsConstructor;

/**
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class EvaluatedConfigurationFactoryImpl<CONFIGURATION> implements ConfigurationFactory<CONFIGURATION>  {

    private final ConfigurationFactory<CONFIGURATION> delegate = new ConfigurationFactoryImpl<>();
    private final Iterable<PluginConfigurationEvaluator> evaluators;

    @Override
    public <T extends CONFIGURATION> T create(Class<T> clazz, String content) {
        T configuration = delegate.create(clazz, content);
        evaluators.forEach(evaluator -> evaluator.evaluate(configuration));

        return configuration;
    }
}

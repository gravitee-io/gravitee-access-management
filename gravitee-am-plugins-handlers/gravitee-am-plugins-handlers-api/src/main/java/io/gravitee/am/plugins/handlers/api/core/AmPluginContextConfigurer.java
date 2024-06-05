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

import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.internal.AnnotationBasedPluginContextConfigurer;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AmPluginContextConfigurer<PROVIDER> extends AnnotationBasedPluginContextConfigurer {
    private final Class<PROVIDER> providerClass;
    private final List<? extends BeanFactoryPostProcessor> postProcessors;

    public AmPluginContextConfigurer(Plugin plugin, Class<PROVIDER> provider, List<? extends BeanFactoryPostProcessor> postProcessors) {
        super(plugin);
        this.providerClass = provider;
        this.postProcessors = postProcessors;
    }

    @Override
    public Set<Class<?>> configurations() {
        return Optional.ofNullable(providerClass.getAnnotation(Import.class))
                .map(Import::value)
                .map(Set::of)
                .orElse(Collections.emptySet());
    }

    @Override
    public ConfigurableApplicationContext applicationContext() {
        ConfigurableApplicationContext configurableApplicationContext = super.applicationContext();
        postProcessors.forEach(configurableApplicationContext::addBeanFactoryPostProcessor);
        return configurableApplicationContext;
    }

    public Class<PROVIDER> getProviderClass() {
        return providerClass;
    }
}

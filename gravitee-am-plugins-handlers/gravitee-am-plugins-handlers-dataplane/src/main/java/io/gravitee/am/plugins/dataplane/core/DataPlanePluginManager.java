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
package io.gravitee.am.plugins.dataplane.core;

import io.gravitee.am.dataplane.api.DataPlane;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.am.plugins.handlers.api.core.AmPluginContextConfigurer;
import io.gravitee.common.service.AbstractService;
import io.gravitee.common.service.Service;
import io.gravitee.plugin.core.api.AbstractConfigurablePluginManager;
import io.gravitee.plugin.core.api.PluginContextFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@Slf4j
@RequiredArgsConstructor
public class DataPlanePluginManager extends AbstractConfigurablePluginManager<DataPlane> {
    public static final String PLUGIN_ID_PREFIX = "dataplane-am-";

    private final PluginContextFactory pluginContextFactory;

    public Optional<DataPlaneProvider> create(DataPlaneDescription dataPlanDescription) {
        log.debug("Looking for a data plan provider for [{}]", dataPlanDescription.type());
        var dataPlan = ofNullable(get(PLUGIN_ID_PREFIX + dataPlanDescription.type())).orElseGet(() -> {
            throw new IllegalStateException("No data plan provider is registered for type " + dataPlanDescription.type());
        });
        if (dataPlan.provider() == null) {
            return Optional.empty();
        }
        List<BeanFactoryPostProcessor> postProcessors = new ArrayList<>();
        postProcessors.add(beanFactory -> beanFactory.registerSingleton("dataPlanDescription", dataPlanDescription));
        return createProvider(new AmPluginContextConfigurer<>(dataPlan.getDelegate(), dataPlan.provider(), postProcessors));
    }

    private Optional<DataPlaneProvider> createProvider(AmPluginContextConfigurer<DataPlaneProvider> amPluginContextConfigurer) {
        try {
            var pluginApplicationContext = pluginContextFactory.create(amPluginContextConfigurer);
            var autowireCapableBeanFactory = pluginApplicationContext.getAutowireCapableBeanFactory();
            DataPlaneProvider provider = autowireCapableBeanFactory.createBean(amPluginContextConfigurer.getProviderClass());
            if (provider instanceof AbstractService) {
                ((AbstractService<?>) provider).setApplicationContext(pluginApplicationContext);
            }
            if (provider instanceof Service) {
                ((Service) provider).start();
            }
            return Optional.of(provider);
        } catch (Exception ex) {
            log.error("An unexpected error occurs while loading", ex);
            return Optional.empty();
        }
    }
}

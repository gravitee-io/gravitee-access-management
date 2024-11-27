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

package io.gravitee.am.plugins.dataplan.core;


import io.gravitee.am.dataplan.api.DataPlan;
import io.gravitee.am.dataplan.api.DataPlanDescription;
import io.gravitee.am.dataplan.api.provider.DataPlanProvider;
import io.gravitee.am.plugins.handlers.api.core.AmPluginContextConfigurer;
import io.gravitee.common.service.AbstractService;
import io.gravitee.common.service.Service;
import io.gravitee.plugin.core.api.AbstractConfigurablePluginManager;
import io.gravitee.plugin.core.api.PluginContextFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@AllArgsConstructor
public class DataPlanPluginManager extends AbstractConfigurablePluginManager<DataPlan> {
    public static final String PLUGIN_ID_PREFIX = "dataplan-am-";
    private PluginContextFactory pluginContextFactory;

    public Optional<DataPlanProvider> create(DataPlanDescription dataPlanDescription) {
        log.debug("Looking for a data plan provider for [{}]", dataPlanDescription.type());
        var dataPlan = ofNullable(get(PLUGIN_ID_PREFIX + dataPlanDescription.type())).orElseGet(() -> {
            throw new IllegalStateException("No data plan provider is registered for type " + dataPlanDescription.type());
        });

        if (dataPlan.provider() == null) {
            return null;
        }
        final List<BeanFactoryPostProcessor> postProcessors = new ArrayList<>();
        postProcessors.add(new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                beanFactory.registerSingleton("dataPlanDescription", dataPlanDescription);
            }
        });
        return ofNullable(createProvider(new AmPluginContextConfigurer<>(dataPlan.getDelegate(), dataPlan.provider(), postProcessors)));
    }

    private DataPlanProvider createProvider(AmPluginContextConfigurer<DataPlanProvider> amPluginContextConfigurer) {
        try {
            var pluginApplicationContext = pluginContextFactory.create(amPluginContextConfigurer);

            final AutowireCapableBeanFactory autowireCapableBeanFactory = pluginApplicationContext.getAutowireCapableBeanFactory();
            DataPlanProvider provider = autowireCapableBeanFactory.createBean(amPluginContextConfigurer.getProviderClass());

            if (provider instanceof AbstractService) {
                ((AbstractService<?>) provider).setApplicationContext(pluginApplicationContext);
            }

            if (provider instanceof Service) {
                ((Service) provider).start();
            }

            return provider;
        } catch (Exception ex) {
            log.error("An unexpected error occurs while loading", ex);
            return null;
        }
    }
}

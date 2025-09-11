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
package io.gravitee.am.gateway.spring;

import io.gravitee.am.repository.Scope;
import io.gravitee.node.container.spring.env.GraviteeYamlPropertySource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.Map;

@RequiredArgsConstructor
public class PropertySourceFallbackConfigurer implements BeanFactoryPostProcessor, Ordered {
    private final Environment env;
    private final ApplicationContext applicationContext;

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        registerSource(gatewayTypePropertyFallback());
    }

    private void registerSource(PropertySource<?> source) {
        ConfigurableEnvironment cfgEnv = (ConfigurableEnvironment) env;
        cfgEnv.getPropertySources().addLast(source);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    // The purpose of adding this processor is to ensure compatibility between the Gateway and older versions of gravitee.yml.
    // If repositories.gateway.type is not specified, then ${management.type} will be used as a fallback.

    private PropertySource<?> gatewayTypePropertyFallback(){
        final Map<String, Object> fallbacks = Map.of("gateway.type", "${management.type}", "ratelimit.type","${repositories.gateway.type:${management.type}}");
        return new GraviteeYamlPropertySource(
                "gatewayTypeFallbackPropertySource",
                fallbacks,
                this.applicationContext);
    }


}

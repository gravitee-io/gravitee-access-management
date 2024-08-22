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

import io.gravitee.node.container.spring.env.AbstractGraviteePropertySource;
import io.gravitee.node.container.spring.env.GraviteeYamlPropertySource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.util.Map;

public class GatewayTypeBeanFactoryFallbackPostProcessor implements BeanFactoryPostProcessor, Ordered {
    private final static String GATEWAY_TYPE_PROPERTY = "gateway.type";
    private final static String GATEWAY_TYPE_DEFAULT_VALUE = "${management.type}";

    private final Environment env;
    private final ApplicationContext applicationContext;

    // The purpose of adding this processor is to ensure compatibility between the Gateway and older versions of gravitee.yml.
    // If gateway.type is not specified, then ${management.type} will be used as a fallback.

    public GatewayTypeBeanFactoryFallbackPostProcessor(Environment environment, ApplicationContext applicationContext) {
        this.env = environment;
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        GraviteeYamlPropertySource source = new GraviteeYamlPropertySource(
                "gatewayTypeFallbackPropertySource",
                Map.of(GATEWAY_TYPE_PROPERTY, GATEWAY_TYPE_DEFAULT_VALUE),
                this.applicationContext);
        registerSource((ConfigurableEnvironment) this.env, source);
    }

    private void registerSource(ConfigurableEnvironment env, AbstractGraviteePropertySource source) {
        env.getPropertySources().addLast(source);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

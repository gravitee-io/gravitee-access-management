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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Properties;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class PropertiesConfiguration {

    protected final static Logger LOGGER = LoggerFactory.getLogger(PropertiesConfiguration.class);

    public final static String GRAVITEE_CONFIGURATION = "gravitee.conf";

    @Bean(name = "graviteeProperties")
    public static Properties graviteeProperties() throws IOException {
        LOGGER.info("Loading configuration.");

        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();

        String yamlConfiguration = System.getProperty(GRAVITEE_CONFIGURATION);
        Resource yamlResource = new FileSystemResource(yamlConfiguration);

        LOGGER.info("\tConfiguration loaded from {}", yamlResource.getURL().getPath());

        yaml.setResources(yamlResource);
        Properties properties = yaml.getObject();
        LOGGER.info("Loading configuration. DONE");

        return properties;
    }
}
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
package io.gravitee.am.repository.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * @author David BRASSELY (david at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PropertySourceRepositoryInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private final Logger logger = LoggerFactory.getLogger(PropertySourceRepositoryInitializer.class);

    private static final String REPOSITORY_PROPERTY_SOURCE = "repository.properties";

    @Override
    @SuppressWarnings("rawtypes")
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        ConfigurableEnvironment environment = configurableApplicationContext.getEnvironment();

        try {
            logger.info("Trying to load a repository properties file from classpath: " + REPOSITORY_PROPERTY_SOURCE);
            PropertySource source = new ResourcePropertySource("repository", new ClassPathResource(REPOSITORY_PROPERTY_SOURCE));
            MutablePropertySources sources = environment.getPropertySources();
            sources.addFirst(source);
            logger.info("Repository properties file has been correctly added to environment");
        } catch (Exception ex) {
            logger.error("Unable to load repository properties file into environment", ex);
        }
    }
}
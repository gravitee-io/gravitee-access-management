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
package io.gravitee.am.gateway.handler.vertx.view;

import freemarker.cache.FileTemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class FreeMarkerConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreeMarkerConfiguration.class);

    @Value("${templates.path:${gravitee.home}/templates}")
    private String templatesPath;

    @Bean
    public freemarker.template.Configuration getConfiguration() {
        final freemarker.template.Configuration configuration =
                new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_22);
        try {
            configuration.setTemplateLoader(new FileTemplateLoader(new File(templatesPath)));
        } catch (final IOException e) {
            LOGGER.warn("Error occurred while trying to read email templates directory", e);
        }
        return configuration;
    }
}

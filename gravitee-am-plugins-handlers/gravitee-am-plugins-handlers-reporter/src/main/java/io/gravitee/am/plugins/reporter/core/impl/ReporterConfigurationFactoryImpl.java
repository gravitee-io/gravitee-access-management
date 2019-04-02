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
package io.gravitee.am.plugins.reporter.core.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.plugins.reporter.core.ReporterConfigurationFactory;
import io.gravitee.am.reporter.api.ReporterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ReporterConfigurationFactoryImpl implements ReporterConfigurationFactory {

    private final Logger logger = LoggerFactory.getLogger(ReporterConfigurationFactoryImpl.class);

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public <T extends ReporterConfiguration> T create(Class<T> clazz, String content) {
        logger.debug("Create a new instance of reporter configuration for class: {}", clazz.getName());

        try {
            return objectMapper.readValue(content, clazz);
        } catch (IOException ioe) {
            logger.error("Unable to create an reporter configuration", ioe);
            return null;
        }
    }
}

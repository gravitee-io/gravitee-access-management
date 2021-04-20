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
package io.gravitee.am.plugins.certificate.core.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.certificate.api.CertificateConfiguration;
import io.gravitee.am.plugins.certificate.core.CertificateConfigurationFactory;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateConfigurationFactoryImpl implements CertificateConfigurationFactory {

    private final Logger logger = LoggerFactory.getLogger(CertificateConfigurationFactoryImpl.class);

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public <T extends CertificateConfiguration> T create(Class<T> clazz, String content) {
        logger.debug("Create a new instance of certificate configuration for class: {}", clazz.getName());

        try {
            return objectMapper.readValue(content, clazz);
        } catch (IOException ioe) {
            logger.error("Unable to create an certificate configuration", ioe);
            return null;
        }
    }
}

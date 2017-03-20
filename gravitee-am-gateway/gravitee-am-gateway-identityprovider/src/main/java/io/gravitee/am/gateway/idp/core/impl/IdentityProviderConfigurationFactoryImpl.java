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
package io.gravitee.am.gateway.idp.core.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.idp.core.IdentityProviderConfigurationFactory;
import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderConfigurationFactoryImpl implements IdentityProviderConfigurationFactory {

    private final Logger logger = LoggerFactory.getLogger(IdentityProviderConfigurationFactoryImpl.class);

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public <T extends IdentityProviderConfiguration> T create(Class<T> clazz, String content) {
        logger.debug("Create a new instance of identity provider configuration for class: {}", clazz.getName());

        try {
            return objectMapper.readValue(content, clazz);
        } catch (IOException ioe) {
            logger.error("Unable to create an identity provider configuration", ioe);
            return null;
        }
    }
}

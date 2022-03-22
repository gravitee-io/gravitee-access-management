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
package io.gravitee.am.plugins.idp.core.impl;

import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.plugins.idp.core.IdentityProviderMapperFactory;
import java.lang.reflect.InvocationTargetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderMapperFactoryImpl implements IdentityProviderMapperFactory {

    private final Logger logger = LoggerFactory.getLogger(IdentityProviderMapperFactoryImpl.class);

    @Override
    public <T extends IdentityProviderMapper> T create(Class<T> clazz, Map<String, String> mappers) {
        if (clazz != null) {
            logger.debug("Create a new instance of identity provider mapper for class: {}", clazz.getName());
            try {
                T identityProviderMapper = clazz.getDeclaredConstructor().newInstance();
                identityProviderMapper.setMappers(mappers);

                return identityProviderMapper;
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException ex) {
                logger.error("Unable to create an identity provider mapper", ex);
            }
        }
        return null;
    }
}

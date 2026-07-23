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

import io.gravitee.am.identityprovider.api.IdentityProviderGroupMapper;
import io.gravitee.am.plugins.idp.core.IdentityProviderGroupMapperFactory;

import java.util.Map;
import lombok.CustomLog;

@CustomLog
public class IdentityProviderGroupMapperFactoryImpl implements IdentityProviderGroupMapperFactory {

    @Override
    public <T extends IdentityProviderGroupMapper> T create(Class<T> clazz, Map<String, String[]> groups) {
        if (clazz != null) {
            log.debug("Create a new instance of identity provider groups for class: {}", clazz.getName());
            try {
                T identityProviderRoleMapper = clazz.newInstance();
                identityProviderRoleMapper.setGroups(groups);

                return identityProviderRoleMapper;
            } catch (InstantiationException | IllegalAccessException ex) {
                log.error("Unable to create an identity provider groups", ex);
            }
        }
        return null;
    }
}

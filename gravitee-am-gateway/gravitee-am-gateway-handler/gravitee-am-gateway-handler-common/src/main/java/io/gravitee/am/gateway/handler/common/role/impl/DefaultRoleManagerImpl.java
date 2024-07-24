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
package io.gravitee.am.gateway.handler.common.role.impl;

import io.gravitee.am.gateway.handler.common.role.RoleFacade;
import io.gravitee.am.model.Role;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DefaultRoleManagerImpl implements RoleFacade {

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public Single<Set<Role>> findByIdIn(List<String> roles) {
        return roleRepository.findByIdIn(roles).collect(() -> (Set<Role>) new HashSet<Role>(), Set::add)
                .onErrorResumeNext(ex -> Single.error(new TechnicalManagementException("An error occurs while trying to find roles by ids", ex)));
    }
}

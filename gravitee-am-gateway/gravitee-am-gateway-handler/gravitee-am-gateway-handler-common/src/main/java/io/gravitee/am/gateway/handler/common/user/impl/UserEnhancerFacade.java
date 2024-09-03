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
package io.gravitee.am.gateway.handler.common.user.impl;

import io.gravitee.am.gateway.handler.common.group.GroupManager;
import io.gravitee.am.gateway.handler.common.role.RoleManager;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Role;
import io.gravitee.am.service.impl.user.BaseUserEnhancer;
import io.reactivex.rxjava3.core.Flowable;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class UserEnhancerFacade extends BaseUserEnhancer {
    private final GroupManager groupManager;
    private final RoleManager roleManager;

    @Override
    protected Flowable<Group> getGroupsByMemberId(String memberId) {
        return groupManager.findByMember(memberId);
    }

    @Override
    protected Flowable<Role> getRolesByIds(List<String> roleIds) {
        return roleManager.findByIdIn(roleIds);
    }

    @Override
    protected Flowable<Group> getGroupsByIds(List<String> groupIds) {
        return groupManager.findByIds(groupIds);
    }
}

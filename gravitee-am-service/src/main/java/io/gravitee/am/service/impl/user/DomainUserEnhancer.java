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
package io.gravitee.am.service.impl.user;

import io.gravitee.am.model.Group;
import io.gravitee.am.model.Role;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.RoleService;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Qualifier("DomainUserEnhancer")
public class DomainUserEnhancer extends BaseUserEnhancer {
    protected final Logger LOGGER = LoggerFactory.getLogger(DomainUserEnhancer.class);

    @Autowired
    private GroupService groupService;

    @Autowired
    private RoleService roleService;

    @Override
    protected Flowable<Group> getGroupsByMemberId(String memberId) {
        return groupService.findByMember(memberId);
    }

    @Override
    protected Flowable<Role> getRolesByIds(List<String> roleIds) {
        return roleService.findByIdIn(roleIds).flattenAsFlowable(set -> set);
    }

    @Override
    protected Flowable<Group> getGroupsByIds(List<String> groupIds) {
        return groupService.findByIdIn(groupIds);
    }
}

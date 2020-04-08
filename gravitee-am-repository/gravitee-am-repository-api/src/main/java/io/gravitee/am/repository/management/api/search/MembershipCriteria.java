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
package io.gravitee.am.repository.management.api.search;

import java.util.List;
import java.util.Optional;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MembershipCriteria {

    private boolean logicalOR;

    private List<String> groupIds;

    private String userId;

    private String roleId;

    public MembershipCriteria() {
        super();
    }

    public MembershipCriteria(String userId) {
        this.userId = userId;
    }

    public Optional<List<String>> getGroupIds() {

        return Optional.ofNullable(groupIds);
    }

    public Optional<String> getUserId() {

        return Optional.ofNullable(userId);
    }

    public Optional<String> getRoleId() {

        return Optional.ofNullable(roleId);
    }


    public boolean isLogicalOR() {
        return logicalOR;
    }

    public MembershipCriteria setLogicalOR(boolean logicalOR) {

        this.logicalOR = logicalOR;
        return this;
    }

    public MembershipCriteria setGroupIds(List<String> groupIds) {

        this.groupIds = groupIds;
        return this;
    }

    public MembershipCriteria setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public MembershipCriteria setRoleId(String roleId) {
        this.roleId = roleId;
        return this;
    }
}

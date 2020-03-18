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
package io.gravitee.am.management.handlers.management.api.model;

import io.gravitee.am.model.Membership;

import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MembershipListItem {

    private List<Membership> memberships;
    private Map<String, Map<String, Object>> metadata;

    public MembershipListItem() { }

    public MembershipListItem(List<Membership> memberships, Map<String, Map<String, Object>> metadata) {
        this.memberships = memberships;
        this.metadata = metadata;
    }

    public List<Membership> getMemberships() {
        return memberships;
    }

    public void setMemberships(List<Membership> memberships) {
        this.memberships = memberships;
    }

    public Map<String, Map<String, Object>> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Map<String, Object>> metadata) {
        this.metadata = metadata;
    }
}

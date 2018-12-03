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
package io.gravitee.am.gateway.handler.scim.model;

import java.util.Arrays;
import java.util.List;

/**
 * SCIM Group Resource
 *
 * See <a href="https://tools.ietf.org/html/rfc7643#section-4.2">4.2. "Group" Resource Schema</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Group extends Resource {

    public static final List<String> SCHEMAS = Arrays.asList("urn:ietf:params:scim:schemas:core:2.0:Group");
    public static String RESOURCE_TYPE = "Group";

    /**
     * A human-readable name for the Group.  REQUIRED.
     */
    private String displayName;

    /**
     *  A list of members of the Group.  While values MAY be added or
     *       removed, sub-attributes of members are "immutable".  The "value"
     *       sub-attribute contains the value of an "id" attribute of a SCIM
     *       resource, and the "$ref" sub-attribute must be the URI of a SCIM
     *       resource such as a "User", or a "Group".  The intention of the
     *       "Group" type is to allow the service provider to support nested
     *       groups.  Service providers MAY require clients to provide a
     *       non-empty value by setting the "required" attribute characteristic
     *       of a sub-attribute of the "members" attribute in the "Group"
     *       resource schema.
     */
    private List<Member> members;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<Member> getMembers() {
        return members;
    }

    public void setMembers(List<Member> members) {
        this.members = members;
    }
}

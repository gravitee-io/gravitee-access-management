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
package io.gravitee.am.service.model;

import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UpdateGroup {

    @NotNull
    private String name;
    private String description;
    private List<String> members;
    private List<String> roles;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }


    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    @Override
    public String toString() {
        return "{\"_class\":\"UpdateGroup\", " +
                "\"name\":" + (name == null ? "null" : "\"" + name + "\"") + ", " +
                "\"description\":" + (description == null ? "null" : "\"" + description + "\"") + ", " +
                "\"members\":" + (members == null ? "null" : Arrays.toString(members.toArray())) + ", " +
                "\"roles\":" + (roles == null ? "null" : Arrays.toString(roles.toArray())) +
                "}";
    }
}

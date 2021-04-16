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

import java.util.List;
import javax.validation.constraints.NotNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UpdateRole {

    @NotNull
    private String name;

    private String description;

    private List<String> permissions;

    private List<String> oauthScopes;

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

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    @Override
    public String toString() {
        return "UpdateRole{" + "name='" + name + '\'' + ", description='" + description + '\'' + ", permissions=" + permissions + '}';
    }

    public List<String> getOauthScopes() {
        return oauthScopes;
    }

    public void setOauthScopes(List<String> oauthScopes) {
        this.oauthScopes = oauthScopes;
    }
}

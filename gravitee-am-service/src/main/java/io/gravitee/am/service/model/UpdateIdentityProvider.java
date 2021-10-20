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
import java.util.List;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UpdateIdentityProvider {

    @NotNull
    private String name;

    @NotNull
    private String configuration;

    private Map<String, String> mappers;

    private Map<String, String[]> roleMapper;

    private List<String> domainWhitelist;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public Map<String, String> getMappers() {
        return mappers;
    }

    public void setMappers(Map<String, String> mappers) {
        this.mappers = mappers;
    }

    public Map<String, String[]> getRoleMapper() {
        return roleMapper;
    }

    public void setRoleMapper(Map<String, String[]> roleMapper) {
        this.roleMapper = roleMapper;
    }

    public List<String> getDomainWhitelist() {
        return domainWhitelist;
    }

    public void setDomainWhitelist(List<String> domainWhitelist) {
        this.domainWhitelist = domainWhitelist;
    }

    @Override
    public String toString() {
        return "UpdateIdentityProvider{" +
                ", name='" + name + '\'' +
                '}';
    }
}

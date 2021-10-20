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

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NewIdentityProvider {

    private String id;

    @NotNull
    private String type;

    @NotNull
    private String name;

    @NotNull
    private String configuration;

    private List<String> domainWhitelist;

    private boolean external;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

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

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public List<String> getDomainWhitelist() {
        return domainWhitelist;
    }

    public void setDomainWhitelist(List<String> domainWhitelist) {
        this.domainWhitelist = domainWhitelist;
    }

    @Override
    public String toString() {
        return "NewIdentityProvider{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}

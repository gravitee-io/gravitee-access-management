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

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UpdateScope {

    @NotNull
    private String name;

    @NotNull
    private String description;

    private Integer expiresIn;

    private Boolean discovery;

    private Boolean parameterized;

    private String iconUri;

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

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }

    public boolean isDiscovery() {
        return discovery;
    }

    public void setDiscovery(boolean discovery) {
        this.discovery = discovery;
    }

    public Boolean getDiscovery() {
        return discovery;
    }

    public String getIconUri() {
        return iconUri;
    }

    public void setIconUri(String iconUri) {
        this.iconUri = iconUri;
    }

    public Boolean getParameterized() {
        return parameterized;
    }

    public boolean isParameterized() {
        return parameterized;
    }

    public void setParameterized(Boolean parameterized) {
        this.parameterized = parameterized;
    }
}

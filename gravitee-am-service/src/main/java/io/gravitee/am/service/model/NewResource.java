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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.model.uma.Resource;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NewResource {

    @NotNull
    @JsonProperty("resource_scopes")
    private List<String> resourceScopes;

    private String description;

    @JsonProperty("icon_uri")
    private String iconUri;

    private String name;

    private String type;

    public List<String> getResourceScopes() {
        return resourceScopes;
    }

    public String getDescription() {
        return description;
    }

    public String getIconUri() {
        return iconUri;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    /**
     * Update input parameter with current request values.
     * @param toUpdate
     * @return
     */
    public Resource update(Resource toUpdate) {
        return toUpdate.setResourceScopes(this.getResourceScopes())
                .setDescription(this.getDescription())
                .setIconUri(this.getIconUri())
                .setName(this.getName())
                .setType(this.getType());
    }
}

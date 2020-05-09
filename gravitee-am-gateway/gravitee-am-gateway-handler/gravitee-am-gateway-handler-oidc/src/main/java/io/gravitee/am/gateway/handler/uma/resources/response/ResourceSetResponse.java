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
package io.gravitee.am.gateway.handler.uma.resources.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.model.uma.ResourceSet;

import java.util.Date;
import java.util.List;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceSetResponse {

    @JsonProperty("_id")
    private String id;

    @JsonProperty("resource_scopes")
    private List<String> resourceScopes;

    @JsonProperty("description")
    private String description;

    @JsonProperty("iconUri")
    private String iconUri;

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("created_at")
    private Date createdAt;

    @JsonProperty("updated_at")
    private Date updatedAt;

    @JsonProperty("user_access_policy_uri")
    private String userAccessPolicyUri;

    //Force to build response from a ResourceSet.
    private ResourceSetResponse() {
    }

    public ResourceSetResponse setId(String id) {
        this.id = id;
        return this;
    }

    public ResourceSetResponse setResourceScopes(List<String> resourceScopes) {
        this.resourceScopes = resourceScopes;
        return this;
    }

    public ResourceSetResponse setDescription(String description) {
        this.description = description;
        return this;
    }

    public ResourceSetResponse setIconUri(String iconUri) {
        this.iconUri = iconUri;
        return this;
    }

    public ResourceSetResponse setName(String name) {
        this.name = name;
        return this;
    }

    public ResourceSetResponse setType(String type) {
        this.type = type;
        return this;
    }

    public ResourceSetResponse setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public ResourceSetResponse setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public ResourceSetResponse setUserAccessPolicyUri(String userAccessPolicyUri) {
        this.userAccessPolicyUri = userAccessPolicyUri;
        return this;
    }

    public static ResourceSetResponse from(ResourceSet resourceSet) {
        return new ResourceSetResponse()
                .setId(resourceSet.getId())
                .setResourceScopes(resourceSet.getResourceScopes())
                .setDescription(resourceSet.getDescription())
                .setIconUri(resourceSet.getIconUri())
                .setName(resourceSet.getName())
                .setType(resourceSet.getType())
                .setCreatedAt(resourceSet.getCreatedAt())
                .setUpdatedAt(resourceSet.getUpdatedAt());
    }
}

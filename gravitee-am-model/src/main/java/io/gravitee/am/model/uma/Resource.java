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
package io.gravitee.am.model.uma;

import java.util.Date;
import java.util.List;

/**
 *
 * Resource as described <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#resource-set-desc">here</a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class Resource {

    /**
     * Resource technical id
     */
    private String id;

    /**
     * An array of strings, serving as scope identifiers, indicating the available scopes for this resource.
     */
    private List<String> resourceScopes;

    /**
     * A human-readable string describing the resource at length.
     */
    private String description;

    /**
     * A URI for a graphic icon representing the resource.
     */
    private String iconUri;

    /**
     * A human-readable string naming the resource.
     */
    private String name;

    /**
     * A string identifying the semantics of the resource.
     */
    private String type;

    /**
     * Security domain associated to the Resource
     */
    private String domain;

    /**
     * Resource Owner id
     */
    private String userId;

    /**
     * Resource Server client id
     */
    private String clientId;

    /**
     * The Client creation date
     */
    private Date createdAt;

    /**
     * The Client last updated date
     */
    private Date updatedAt;

    public List<String> getResourceScopes() {
        return resourceScopes;
    }

    public Resource setResourceScopes(List<String> resourceScopes) {
        this.resourceScopes = resourceScopes;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Resource setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getIconUri() {
        return iconUri;
    }

    public Resource setIconUri(String iconUri) {
        this.iconUri = iconUri;
        return this;
    }

    public String getName() {
        return name;
    }

    public Resource setName(String name) {
        this.name = name;
        return this;
    }

    public String getType() {
        return type;
    }

    public Resource setType(String type) {
        this.type = type;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public Resource setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public Resource setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getClientId() {
        return clientId;
    }

    public Resource setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Resource setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public Resource setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public String getId() {
        return id;
    }

    public Resource setId(String id) {
        this.id = id;
        return this;
    }
}

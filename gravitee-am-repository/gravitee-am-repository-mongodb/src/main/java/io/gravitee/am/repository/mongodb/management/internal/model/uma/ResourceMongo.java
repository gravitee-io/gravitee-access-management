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
package io.gravitee.am.repository.mongodb.management.internal.model.uma;

import io.gravitee.am.repository.mongodb.common.model.Auditable;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class ResourceMongo extends Auditable {

    @BsonId
    private String id;
    private List<String> resourceScopes;
    private String description;
    private String iconUri;
    private String name;
    private String type;
    private String domain;
    private String userId;
    private String clientId;

    public String getId() {
        return id;
    }

    public ResourceMongo setId(String id) {
        this.id = id;
        return this;
    }

    public List<String> getResourceScopes() {
        return resourceScopes;
    }

    public ResourceMongo setResourceScopes(List<String> resourceScopes) {
        this.resourceScopes = resourceScopes;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public ResourceMongo setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getIconUri() {
        return iconUri;
    }

    public ResourceMongo setIconUri(String iconUri) {
        this.iconUri = iconUri;
        return this;
    }

    public String getName() {
        return name;
    }

    public ResourceMongo setName(String name) {
        this.name = name;
        return this;
    }

    public String getType() {
        return type;
    }

    public ResourceMongo setType(String type) {
        this.type = type;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public ResourceMongo setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public ResourceMongo setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getClientId() {
        return clientId;
    }

    public ResourceMongo setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }
}

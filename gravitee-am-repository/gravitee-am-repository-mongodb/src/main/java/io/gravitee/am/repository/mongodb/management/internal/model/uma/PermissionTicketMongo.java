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

import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Date;
import java.util.List;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class PermissionTicketMongo {

    @BsonId
    private String id;
    private List<PermissionRequestMongo> permissionRequest;
    private String domain;
    private String userId;
    private String clientId;
    private Date createdAt;
    private Date expireAt;

    public String getId() {
        return id;
    }

    public PermissionTicketMongo setId(String id) {
        this.id = id;
        return this;
    }

    public List<PermissionRequestMongo> getPermissionRequest() {
        return permissionRequest;
    }

    public PermissionTicketMongo setPermissionRequest(List<PermissionRequestMongo> permissionRequest) {
        this.permissionRequest = permissionRequest;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public PermissionTicketMongo setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public PermissionTicketMongo setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getClientId() {
        return clientId;
    }

    public PermissionTicketMongo setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public PermissionTicketMongo setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Date getExpireAt() {
        return expireAt;
    }

    public PermissionTicketMongo setExpireAt(Date expireAt) {
        this.expireAt = expireAt;
        return this;
    }
}

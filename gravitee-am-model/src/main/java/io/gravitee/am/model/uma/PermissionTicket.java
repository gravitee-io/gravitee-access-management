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
 * Permission Ticket including permission request and ticket response ID.
 * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#rfc.section.4.1">here</a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class PermissionTicket {

    /**
     * Permission Ticket technical id
     * Named as ticket on Permission Endpoint
     */
    private String id;

    /**
     * Requested resources and corresponding scopes.
     */
    private List<PermissionRequest> permissionRequest;

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
    private Date expireAt;

    public String getId() {
        return id;
    }

    public PermissionTicket setId(String id) {
        this.id = id;
        return this;
    }

    public List<PermissionRequest> getPermissionRequest() {
        return permissionRequest;
    }

    public PermissionTicket setPermissionRequest(List<PermissionRequest> permissionRequest) {
        this.permissionRequest = permissionRequest;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public PermissionTicket setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public PermissionTicket setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getClientId() {
        return clientId;
    }

    public PermissionTicket setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public PermissionTicket setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Date getExpireAt() {
        return expireAt;
    }

    public PermissionTicket setExpireAt(Date expireAt) {
        this.expireAt = expireAt;
        return this;
    }
}

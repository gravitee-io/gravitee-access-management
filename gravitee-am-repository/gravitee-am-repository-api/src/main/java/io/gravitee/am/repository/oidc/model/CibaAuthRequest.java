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
package io.gravitee.am.repository.oidc.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CibaAuthRequest {

    /**
     * Technical ID
     */
    private String id;
    /**
     * Status
     */
    private String status;

    /**
     * The auth_req_id creation date
     */
    private Date createdAt;
    /**
     * The auth_req_id last access date
     */
    private Date lastAccessAt;

    /**
     * The auth_req_id expiration date
     */
    private Date expireAt;

    /**
     * The client which asks for the authentication request
     */
    private String clientId;

    /**
     * Technical identifier for user
     */
    private String subject;

    /**
     * The scopes of the request
     */
    private Set<String> scopes;

    /**
     * Transaction identifier user instead of the auth_req_id to notify the authentication device
     */
    private String externalTrxId;

    /**
     * Information provided by the AuthenticationDeviceNotifier in the response
     */
    private Map<String, Object> externalInformation = new HashMap<>();

    private String deviceNotifierId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Date expireAt) {
        this.expireAt = expireAt;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public void setScopes(Set<String> scopes) {
        this.scopes = scopes;
    }

    public Date getLastAccessAt() {
        return lastAccessAt;
    }

    public void setLastAccessAt(Date lastAccessAt) {
        this.lastAccessAt = lastAccessAt;
    }

    public String getExternalTrxId() {
        return externalTrxId;
    }

    public void setExternalTrxId(String externalTrxId) {
        this.externalTrxId = externalTrxId;
    }

    public Map<String, Object> getExternalInformation() {
        return externalInformation;
    }

    public void setExternalInformation(Map<String, Object> externalInformation) {
        this.externalInformation = externalInformation;
    }

    public String getDeviceNotifierId() {
        return deviceNotifierId;
    }

    public void setDeviceNotifierId(String deviceNotifierId) {
        this.deviceNotifierId = deviceNotifierId;
    }
}

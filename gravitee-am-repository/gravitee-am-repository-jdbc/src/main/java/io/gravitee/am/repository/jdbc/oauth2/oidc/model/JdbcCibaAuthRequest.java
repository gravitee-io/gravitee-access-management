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
package io.gravitee.am.repository.jdbc.oauth2.oidc.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("ciba_auth_requests")
public class JdbcCibaAuthRequest {
    @Id
    private String id;
    private String status;
    private String subject;
    private String scopes;
    @Column("device_notifier_id")
    private String deviceNotifierId;
    private String client;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("last_access_at")
    private LocalDateTime lastAccessAt;
    @Column("expire_at")
    private LocalDateTime expireAt;
    @Column("ext_transaction_id")
    private String externalTrxId;
    @Column("external_information")
    private String externalInformation;

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

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getDeviceNotifierId() {
        return deviceNotifierId;
    }

    public void setDeviceNotifierId(String deviceNotifierId) {
        this.deviceNotifierId = deviceNotifierId;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public LocalDateTime getLastAccessAt() {
        return lastAccessAt;
    }

    public void setLastAccessAt(LocalDateTime lastAccessAt) {
        this.lastAccessAt = lastAccessAt;
    }

    public String getExternalTrxId() {
        return externalTrxId;
    }

    public void setExternalTrxId(String externalTrxId) {
        this.externalTrxId = externalTrxId;
    }

    public String getExternalInformation() {
        return externalInformation;
    }

    public void setExternalInformation(String externalInformation) {
        this.externalInformation = externalInformation;
    }
}

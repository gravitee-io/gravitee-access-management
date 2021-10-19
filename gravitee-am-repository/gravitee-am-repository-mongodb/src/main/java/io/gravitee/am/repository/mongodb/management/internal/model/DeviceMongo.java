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

package io.gravitee.am.repository.mongodb.management.internal.model;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.util.Date;
import java.util.Objects;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceMongo {

    @BsonId
    private String id;

    private String referenceType;
    private String referenceId;

    private String client;
    private String userId;
    private String deviceIdentifierId;
    private String deviceId;

    private String type;

    @BsonProperty("created_at")
    private Date createdAt;
    @BsonProperty("expires_at")
    private Date expiresAt;

    public String getId() {
        return id;
    }

    public DeviceMongo setId(String id) {
        this.id = id;
        return this;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public DeviceMongo setReferenceType(String referenceType) {
        this.referenceType = referenceType;
        return this;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public DeviceMongo setReferenceId(String referenceId) {
        this.referenceId = referenceId;
        return this;
    }

    public String getClient() {
        return client;
    }

    public DeviceMongo setClient(String client) {
        this.client = client;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public DeviceMongo setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getDeviceIdentifierId() {
        return deviceIdentifierId;
    }

    public DeviceMongo setDeviceIdentifierId(String deviceIdentifierId) {
        this.deviceIdentifierId = deviceIdentifierId;
        return this;
    }

    public String getType() {
        return type;
    }

    public DeviceMongo setType(String type) {
        this.type = type;
        return this;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public DeviceMongo setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public DeviceMongo setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public DeviceMongo setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceMongo that = (DeviceMongo) o;
        return Objects.equals(id, that.id) && Objects.equals(referenceType, that.referenceType) && Objects.equals(referenceId, that.referenceId) && Objects.equals(client, that.client) && Objects.equals(userId, that.userId) && Objects.equals(deviceIdentifierId, that.deviceIdentifierId) && Objects.equals(type, that.type) && Objects.equals(createdAt, that.createdAt) && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, referenceType, referenceId, client, userId, deviceIdentifierId, type, createdAt, expiresAt);
    }
}

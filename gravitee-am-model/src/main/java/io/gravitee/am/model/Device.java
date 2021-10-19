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

package io.gravitee.am.model;

import java.util.Date;
import java.util.Objects;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Device {

    private String id;

    private ReferenceType referenceType;
    private String referenceId;

    private String client;
    private String userId;
    private String deviceIdentifierId;
    private String deviceId;

    private String type;

    private Date createdAt;
    private Date expiresAt;

    public Device() {
    }

    public Device(Device other) {
        this.setId(other.id)
                .setReferenceType(other.referenceType)
                .setReferenceId(other.referenceId)
                .setClient(other.client)
                .setUserId(other.userId)
                .setDeviceIdentifierId(other.deviceIdentifierId)
                .setDeviceId(other.deviceId)
                .setType(other.type)
                .setCreatedAt(other.createdAt)
                .setExpiresAt(other.expiresAt);
    }

    public String getId() {
        return id;
    }

    public Device setId(String id) {
        this.id = id;
        return this;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public Device setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
        return this;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public Device setReferenceId(String referenceId) {
        this.referenceId = referenceId;
        return this;
    }

    public String getClient() {
        return client;
    }

    public Device setClient(String client) {
        this.client = client;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public Device setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getDeviceIdentifierId() {
        return deviceIdentifierId;
    }

    public Device setDeviceIdentifierId(String deviceIdentifierId) {
        this.deviceIdentifierId = deviceIdentifierId;
        return this;
    }

    public String getType() {
        return type;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Device setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    public Device setType(String type) {
        this.type = type;
        return this;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Device setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public Device setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Device device = (Device) o;
        return Objects.equals(id, device.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

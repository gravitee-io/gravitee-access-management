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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.Objects;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Accessors(chain = true)
public class Device {

    private String id;

    private ReferenceType referenceType;
    private String referenceId;

    private String client;
    private UserId userId;
    private String deviceIdentifierId;
    private String deviceId;

    private String type;

    @Schema(type = "java.lang.Long")
    private Date createdAt;
    @Schema(type = "java.lang.Long")
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

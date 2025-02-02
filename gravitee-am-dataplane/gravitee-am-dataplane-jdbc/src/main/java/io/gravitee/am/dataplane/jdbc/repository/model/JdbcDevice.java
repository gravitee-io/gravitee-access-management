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

package io.gravitee.am.dataplane.jdbc.repository.model;

import io.gravitee.am.model.Device;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JdbcDevice {
    @Id
    private String id;

    @Column("reference_type")
    private String referenceType;
    @Column("reference_id")
    private String referenceId;
    private String client;
    @Column("user_id")
    private String userId;
    @Column("device_identifier_id")
    private String deviceIdentifierId;
    @Column("device_id")
    private String deviceId;

    private String type;

    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("expires_at")
    private LocalDateTime expiresAt;


    public static JdbcDevice from(Device device) {
        return JdbcDevice.builder()
                .id(device.getId())
                .referenceType(device.getReferenceType().name())
                .referenceId(device.getReferenceId())
                .client(device.getClient())
                .userId(device.getUserId().id())
                .deviceIdentifierId(device.getDeviceIdentifierId())
                .deviceId(device.getDeviceId())
                .type(device.getType())
                .createdAt(device.getCreatedAt() == null ? null : device.getCreatedAt().toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime())
                .expiresAt(device.getExpiresAt() == null ? null : device.getExpiresAt().toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime())
                .build();
    }

    public Device toEntity() {
        return new Device()
                .setId(this.id)
                .setDeviceId(this.deviceId)
                .setClient(this.client)
                .setCreatedAt(Date.from(this.createdAt.toInstant(ZoneOffset.UTC)))
                .setExpiresAt(Date.from(this.expiresAt.toInstant(ZoneOffset.UTC)))
                .setReferenceType(ReferenceType.valueOf(this.referenceType))
                .setReferenceId(this.referenceId)
                .setDeviceIdentifierId(this.deviceIdentifierId)
                .setType(this.type)
                .setUserId(UserId.internal(this.userId));
    }
}

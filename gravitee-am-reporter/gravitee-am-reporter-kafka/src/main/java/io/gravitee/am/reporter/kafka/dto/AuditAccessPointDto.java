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
package io.gravitee.am.reporter.kafka.dto;

import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.validator.routines.InetAddressValidator;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuditAccessPointDto {

    private String id;
    private String alternativeId;
    private String displayName;
    private String ipAddress;
    private String userAgent;

    public static AuditAccessPointDto from(AuditAccessPoint accessPoint) {
        if (accessPoint == null) {
            return null;
        }
        return builder()
                .id(accessPoint.getId())
                .alternativeId(accessPoint.getAlternativeId())
                .displayName(accessPoint.getDisplayName())
                .userAgent(accessPoint.getUserAgent())
                .ipAddress(ensureValid(accessPoint.getIpAddress()))
                .build();
    }

    private static String ensureValid(String ipAddress) {
        if (ipAddress == null || !InetAddressValidator.getInstance().isValid(ipAddress)) {
            return "0.0.0.0";
        }
        return ipAddress;
    }
}

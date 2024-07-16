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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.model.Certificate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.Map;

/**
 * A representation of a create/update op's result on a Certificate
 */
public record ModifiedCertificateEntity(
        String id,

        String name,

        String type,

        String configuration,

        String domain,

        Map<String, Object> metadata,

        @Schema(type = "java.lang.Long")
        Date createdAt,

        @Schema(type = "java.lang.Long")
        Date updatedAt,

        @Schema(type = "java.lang.Long")
        Date expiresAt,
        boolean system
) {
    public static ModifiedCertificateEntity of(Certificate certificate) {
        var exposedConfiguration = certificate.isSystem() ? "{}" : certificate.getConfiguration();
        return new ModifiedCertificateEntity(
                certificate.getId(),
                certificate.getName(),
                certificate.getType(),
                exposedConfiguration,
                certificate.getDomain(),
                certificate.getMetadata(),
                certificate.getCreatedAt(),
                certificate.getUpdatedAt(),
                certificate.getExpiresAt(),
                certificate.isSystem()
        );
    }
}

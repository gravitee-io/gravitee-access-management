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
package io.gravitee.am.service.model;

import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.service.dataplane.config.DataPlaneConnectionSummary;

import java.util.Date;
import java.util.List;

/**
 * What a caller of the data plane endpoints is allowed to see.
 *
 * The stored {@code configuration} blob is never part of this: it can hold connection credentials,
 * so it is replaced by the credential-free {@code database} / {@code hosts} pair produced by the
 * type's {@code DataPlaneConfigHandler}. This type exists so the raw blob has no route out of the
 * service layer at all — the only consumer that needs it reads the repository directly.
 *
 * @author GraviteeSource Team
 */
public record DataPlaneDefinitionSummary(
        String id,
        String name,
        String type,
        String gatewayUrl,
        String organizationId,
        String environmentId,
        String database,
        List<String> hosts,
        Date createdAt,
        Date updatedAt) {

    public static DataPlaneDefinitionSummary of(DataPlaneDefinition definition, DataPlaneConnectionSummary connection) {
        return new DataPlaneDefinitionSummary(
                definition.getId(),
                definition.getName(),
                definition.getType(),
                definition.getGatewayUrl(),
                definition.getOrganizationId(),
                definition.getEnvironmentId(),
                connection.database(),
                connection.hosts(),
                definition.getCreatedAt(),
                definition.getUpdatedAt());
    }
}

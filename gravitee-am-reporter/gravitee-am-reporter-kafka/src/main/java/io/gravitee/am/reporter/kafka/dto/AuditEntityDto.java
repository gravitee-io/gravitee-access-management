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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import lombok.Builder;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Builder
public record AuditEntityDto(String id,
                             String alternativeId,
                             String type,
                             String displayName,
                             String referenceType,
                             String referenceId,
                             Map<String, String> attributes) {

    public static AuditEntityDto from(AuditEntity entity) {
        if (entity == null) {
            return null;
        }
        return builder()
                .id(entity.getId())
                .alternativeId(entity.getAlternativeId())
                .type(entity.getType())
                .displayName(entity.getDisplayName())
                .referenceType(Objects.toString(entity.getReferenceType()))
                .referenceId(entity.getReferenceId())
                .attributes(getStringAttributes(entity))
                .build();
    }

    private static Map<String, String> getStringAttributes(AuditEntity entity) {
        var mapper = new ObjectMapper();
        return entity.getAttributes().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
            if (e.getValue() instanceof String value) {
                return value;
            } else {
                try {
                    return mapper.writeValueAsString(e.getValue());
                } catch (JsonProcessingException ex) {
                    return "";
                }
            }
        }));
    }
}

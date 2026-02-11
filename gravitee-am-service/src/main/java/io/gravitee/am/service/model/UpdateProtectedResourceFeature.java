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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.gravitee.am.model.ProtectedResourceFeature;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * Writable fields for a feature in an update request.
 * Additional properties (e.g. createdAt/updatedAt from the GET response) are ignored if sent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UpdateMcpTool.class, name = "MCP_TOOL"),
        // Management API ObjectMapperResolver serialises enums as lowercase (value.name().toLowerCase()),
        // so GET returns "mcp_tool". Accept it on PUT so the API is round-trip consistent.
        @JsonSubTypes.Type(value = UpdateMcpTool.class, name = "mcp_tool"),
})
@Getter
@Setter
public class UpdateProtectedResourceFeature {

    @NotBlank
    @Size(min = 1, max = NewProtectedResourceFeature.KEY_MAX_LENGTH, message = "Key must be between {min} and {max} characters")
    @Pattern(regexp = NewProtectedResourceFeature.KEY_PATTERN, message = "must match regex ^[a-zA-Z0-9_-]+$")
    private String key;

    private String description;

    public ProtectedResourceFeature asFeature(){
        ProtectedResourceFeature feature = new ProtectedResourceFeature();
        feature.setKey(StringUtils.trimToNull(getKey()));
        feature.setDescription(StringUtils.trimToNull(getDescription()));
        return feature;
    }
}


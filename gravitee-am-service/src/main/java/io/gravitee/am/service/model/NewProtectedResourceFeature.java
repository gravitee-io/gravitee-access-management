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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.gravitee.am.model.ProtectedResourceFeature;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = NewMcpTool.class, name = "MCP_TOOL"),
})
@Getter
@Setter
public class NewProtectedResourceFeature {

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "must match regex ^[a-zA-Z0-9_-]+$")
    private String key;

    private String description;

    public ProtectedResourceFeature asFeature(){
        ProtectedResourceFeature feature = new ProtectedResourceFeature();
        feature.setKey(getKey() != null ? getKey().trim() : null);
        feature.setDescription(getDescription() != null ? getDescription().trim() : null);
        return feature;
    }


}

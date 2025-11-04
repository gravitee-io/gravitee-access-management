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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpTool.class, name = "MCP_TOOL"),
})
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "MCP_TOOL", schema = McpTool.class)
        }
)
@Getter
@Setter
public class ProtectedResourceFeature {

    public enum Type {
        MCP_TOOL
    }

    private String key;

    private String description;

    private Type type;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public ProtectedResourceFeature() {
    }

    public ProtectedResourceFeature(ProtectedResourceFeature protectedResource) {
        this.key = protectedResource.getKey();
        this.type = protectedResource.getType();
        this.description = protectedResource.getDescription();
        this.createdAt = protectedResource.getCreatedAt();
        this.updatedAt = protectedResource.getUpdatedAt();
    }


}

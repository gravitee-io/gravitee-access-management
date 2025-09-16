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
package io.gravitee.am.model.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP Tool Definition
 * 
 * @author GraviteeSource Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "MCP Tool Definition")
public class MCPToolDefinition {

    /**
     * The name of the tool
     */
    @Schema(description = "The name of the tool", required = true)
    private String name;

    /**
     * Description of what the tool does
     */
    @Schema(description = "Description of what the tool does")
    private String description;

    /**
     * List of required scopes for executing this tool
     */
    @Schema(description = "List of required scopes for executing this tool")
    private List<String> requiredScopes;

    /**
     * Input schema for the tool (JSON schema)
     */
    @Schema(description = "Input schema for the tool (JSON schema)")
    private String inputSchema;

    public MCPToolDefinition(MCPToolDefinition other) {
        this.name = other.name;
        this.description = other.description;
        this.requiredScopes = other.requiredScopes != null ? new ArrayList<>(other.requiredScopes) : null;
        this.inputSchema = other.inputSchema;
    }
}

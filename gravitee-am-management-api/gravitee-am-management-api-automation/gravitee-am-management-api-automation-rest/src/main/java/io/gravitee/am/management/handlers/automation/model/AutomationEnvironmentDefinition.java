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
package io.gravitee.am.management.handlers.automation.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO for creating or updating an Environment via the Automation API.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class AutomationEnvironmentDefinition {

    @NotNull
    @Size(min = 1, max = 255)
    private String hrid;

    @NotNull
    @Size(min = 1, max = 255)
    private String name;

    private String description;

    private List<String> hrids;

    private List<String> domainRestrictions;
}

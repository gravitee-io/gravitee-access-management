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

import static java.util.stream.Collectors.toCollection;

/**
 * Which resource servers an application participating in Cross App Access may reach.
 *
 * <p>Application-specific and never inherited from the security domain, unlike the surrounding token
 * exchange settings. Absent reads the same as disabled.
 *
 * @author GraviteeSource Team
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "Application Cross App Access settings",
        description = "Resource servers this application may request an ID-JAG for.")
public class ApplicationCrossAppAccessSettings {

    @Schema(description = "Whether this application participates in Cross App Access.", defaultValue = "false")
    private boolean enabled;

    @Schema(description = "Resource servers this application may reach, one entry per resource server.")
    private List<ApplicationCrossAppAccessResourceServer> resourceServers;

    public ApplicationCrossAppAccessSettings(ApplicationCrossAppAccessSettings other) {
        this.enabled = other.enabled;
        this.resourceServers = other.resourceServers != null
                ? other.resourceServers.stream().map(ApplicationCrossAppAccessResourceServer::new).collect(toCollection(ArrayList::new))
                : null;
    }
}

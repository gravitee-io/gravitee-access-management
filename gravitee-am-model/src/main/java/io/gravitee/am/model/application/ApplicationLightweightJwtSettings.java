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

/**
 * @author GraviteeSource Team
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "Application Lightweight JWT settings",
        description = "Restricts the JWT access token returned to the client to the reserved protocol claims.")
public class ApplicationLightweightJwtSettings {

    @Schema(description = "Whether Lightweight JWT is enabled.", defaultValue = "false")
    private boolean enabled;

    public ApplicationLightweightJwtSettings(ApplicationLightweightJwtSettings other) {
        this.enabled = other.enabled;
    }
}

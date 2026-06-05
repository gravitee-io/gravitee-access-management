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
package io.gravitee.am.model.uma;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Schema(title = "UMA settings", description = "Configuration of the domain's User-Managed Access (UMA 2.0) " +
        "authorization features.")
public class UMASettings {

    @Schema(description = "Whether User-Managed Access is enabled for the domain.", defaultValue = "false")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public UMASettings setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }
}

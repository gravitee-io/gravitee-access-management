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
package io.gravitee.am.model.webprotection;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(title = "X-Frame-Options settings", description = "Controls whether the domain's pages may be " +
        "embedded in frames on other origins.")
public class XFrameSettings {

    @Schema(description = "Whether X-Frame-Options settings are inherited from the gateway defaults (gravitee.yml). " +
            "When null, legacy behaviour applies: enabled=true overrides and enabled=false inherits.",
            defaultValue = "true")
    private Boolean inherited;

    @Schema(description = "Whether X-Frame-Options is enabled for the domain when not inherited.",
            defaultValue = "false")
    private boolean enabled;

    @Schema(description = "X-Frame-Options action. Supported values: DENY, SAMEORIGIN. Leave empty to omit the header.",
            example = "DENY")
    private String action;

    public XFrameSettings() {
    }

    public XFrameSettings(XFrameSettings other) {
        this.inherited = other.inherited;
        this.enabled = other.enabled;
        this.action = other.action;
    }
}

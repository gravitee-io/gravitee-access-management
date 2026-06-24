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
@Schema(title = "Web protection settings", description = "HTTP security headers applied to the domain's " +
        "login and consent pages.")
public class WebProtectionSettings {

    @Schema(description = "Content Security Policy settings.")
    private CspSettings csp;

    @Schema(description = "X-Frame-Options settings.")
    private XFrameSettings xframe;

    @Schema(description = "X-XSS-Protection settings.")
    private XssProtectionSettings xss;

    public WebProtectionSettings() {
    }

    public WebProtectionSettings(WebProtectionSettings other) {
        this.csp = other.csp != null ? new CspSettings(other.csp) : null;
        this.xframe = other.xframe != null ? new XFrameSettings(other.xframe) : null;
        this.xss = other.xss != null ? new XssProtectionSettings(other.xss) : null;
    }
}

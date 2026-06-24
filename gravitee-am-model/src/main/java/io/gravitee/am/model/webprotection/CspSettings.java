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

import java.util.ArrayList;
import java.util.List;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(title = "CSP settings", description = "Content Security Policy configuration for the domain's " +
        "login and consent pages.")
public class CspSettings {

    @Schema(description = "Whether CSP settings are inherited from the gateway defaults (gravitee.yml). " +
            "When null, legacy behaviour applies: enabled=true overrides and enabled=false inherits.",
            defaultValue = "true")
    private Boolean inherited;

    @Schema(description = "Whether CSP is enabled for the domain when not inherited.", defaultValue = "false")
    private boolean enabled;

    @Schema(description = "When true, the policy is delivered as Content-Security-Policy-Report-Only.",
            defaultValue = "false")
    private boolean reportOnly;

    @Schema(description = "Whether inline scripts are allowed via a per-request nonce.",
            defaultValue = "true")
    private boolean scriptInlineNonce = true;

    @Schema(description = "CSP directives, each in the form \"directive-name value;\".",
            example = "[\"default-src 'self';\", \"script-src 'self';\"]")
    private List<String> directives;

    public CspSettings() {
    }

    public CspSettings(CspSettings other) {
        this.inherited = other.inherited;
        this.enabled = other.enabled;
        this.reportOnly = other.reportOnly;
        this.scriptInlineNonce = other.scriptInlineNonce;
        this.directives = other.directives != null ? new ArrayList<>(other.directives) : null;
    }
}

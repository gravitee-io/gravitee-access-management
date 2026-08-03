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
package io.gravitee.am.model.oidc;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain-level settings for Demonstrating Proof-of-Possession (DPoP, RFC 9449).
 *
 * @author GraviteeSource Team
 */
@Schema(title = "DPoP settings", description = "Demonstrating Proof-of-Possession (RFC 9449) configuration for the domain.")
public class DPoPSettings {

    @Schema(description = "Domain-wide floor (RFC 9449) requiring every application to present a DPoP " +
            "proof at the token endpoint. When true it cannot be overridden by an application whose " +
            "own dpop_bound_access_tokens flag is off.",
            defaultValue = "false")
    private boolean requireDpopForAll;

    @Schema(description = "Allowlist of DPoP (RFC 9449) proof signing algorithms accepted at the token " +
            "endpoint, drawn from the base supported set (ES256/384/512, RS256/384/512). When null the " +
            "full base set is accepted. Persisting an empty set is rejected.")
    private List<String> dpopSigningAlgorithms;

    public DPoPSettings() {
    }

    public DPoPSettings(DPoPSettings other) {
        this.requireDpopForAll = other.requireDpopForAll;
        this.dpopSigningAlgorithms = other.dpopSigningAlgorithms != null
                ? new ArrayList<>(other.dpopSigningAlgorithms) : null;
    }

    public boolean isRequireDpopForAll() {
        return requireDpopForAll;
    }

    public void setRequireDpopForAll(boolean requireDpopForAll) {
        this.requireDpopForAll = requireDpopForAll;
    }

    public List<String> getDpopSigningAlgorithms() {
        return dpopSigningAlgorithms;
    }

    public void setDpopSigningAlgorithms(List<String> dpopSigningAlgorithms) {
        this.dpopSigningAlgorithms = dpopSigningAlgorithms;
    }

    public static DPoPSettings defaultSettings() {
        return new DPoPSettings();
    }
}

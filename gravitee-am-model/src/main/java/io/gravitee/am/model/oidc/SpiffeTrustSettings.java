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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "SPIFFE trust settings",
        description = "Accepts JWT-SVIDs from this authority as client assertions.")
public class SpiffeTrustSettings {

    @Schema(description = "SPIFFE trust domain matched against the \"sub\" of a JWT-SVID, without the "
            + "\"spiffe://\" scheme. Lowercased on write. Unique within the security domain.",
            example = "acme.org")
    private String spiffeTrustDomain;

    @Schema(description = "Signature algorithms accepted for JWT-SVID validation. Defaults to "
            + "workloadIdentitySettings.defaultAllowedAlgorithms when absent.")
    private List<String> allowedAlgorithms;

    public SpiffeTrustSettings(SpiffeTrustSettings other) {
        this.spiffeTrustDomain = other.spiffeTrustDomain;
        this.allowedAlgorithms = other.allowedAlgorithms == null ? null : List.copyOf(other.allowedAlgorithms);
    }
}

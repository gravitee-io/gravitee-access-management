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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toCollection;

/**
 * What AM issues towards a {@link TrustDomain}, as opposed to what it accepts from it.
 *
 * <p>Absent on a trusted domain that does not participate in Cross App Access, which reads the same
 * as disabled.
 *
 * @author GraviteeSource Team
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "Cross App Access settings",
        description = "Outbound configuration for minting ID-JAGs towards this authority.")
public class CrossAppAccessSettings {

    public static final int AUD_SUB_MAPPING_MAX_LENGTH = 512;

    public static final int AUDIENCE_MAX_LENGTH = 512;

    @Schema(description = "Whether AM may mint an ID-JAG towards this authority.", defaultValue = "false")
    private boolean enabled;

    @Schema(description = "Issuer identifier of this authority's authorization server, the one an ID-JAG "
            + "is presented to. Carried by the \"aud\" claim of the ID-JAG. An absolute URI, unique "
            + "within the security domain.",
            example = "https://auth.acme.com", maxLength = AUDIENCE_MAX_LENGTH)
    private String audience;

    @Schema(description = "Resource servers this authority exposes behind that authorization server.")
    private List<CrossAppAccessResourceServer> resourceServers;

    @Schema(description = "Expression evaluated against the user profile (variable \"user\") to produce the "
            + "optional \"aud_sub\" claim. The claim is omitted when the expression yields nothing.",
            example = "{#user.email}", maxLength = AUD_SUB_MAPPING_MAX_LENGTH)
    private String audSubMapping;

    @Schema(description = "One-to-one mapping from domain scope to the name this authority knows it by. "
            + "Unmapped domain scopes are dropped (fail-closed).")
    private Map<String, String> scopeMappings;

    public CrossAppAccessSettings(CrossAppAccessSettings other) {
        this.enabled = other.enabled;
        this.audience = other.audience;
        this.resourceServers = other.resourceServers != null
                ? other.resourceServers.stream().map(CrossAppAccessResourceServer::new).collect(toCollection(ArrayList::new))
                : null;
        this.audSubMapping = other.audSubMapping;
        this.scopeMappings = other.scopeMappings != null ? new LinkedHashMap<>(other.scopeMappings) : null;
    }
}

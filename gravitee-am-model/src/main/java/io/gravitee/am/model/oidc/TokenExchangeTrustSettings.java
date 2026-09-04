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

import io.gravitee.am.model.UserBindingCriterion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "Token exchange trust settings",
        description = "Accepts this authority's JWTs as subject or actor tokens during an RFC 8693 "
                + "exchange, matched by domainIdentifier.")
public class TokenExchangeTrustSettings {

    @Schema(description = "One-to-one mapping from external scope to domain scope. Unmapped issuer "
            + "scopes are dropped (fail-closed).")
    private Map<String, String> scopeMappings;

    @Schema(description = "Whether the external JWT subject is resolved to a single domain user using "
            + "the user binding criteria. When false, a virtual user is built from the token claims only.",
            defaultValue = "false")
    private boolean userBindingEnabled;

    @Schema(description = "Criteria used to locate a domain user when user binding is enabled. All "
            + "criteria are combined with AND.")
    private List<UserBindingCriterion> userBindingCriteria;

    public TokenExchangeTrustSettings(TokenExchangeTrustSettings other) {
        this.scopeMappings = other.scopeMappings == null ? null : new LinkedHashMap<>(other.scopeMappings);
        this.userBindingEnabled = other.userBindingEnabled;
        this.userBindingCriteria = other.userBindingCriteria == null ? null : new ArrayList<>(other.userBindingCriteria);
    }
}

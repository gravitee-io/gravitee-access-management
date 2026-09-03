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
package io.gravitee.am.service.model;

import io.gravitee.am.model.oidc.SpiffeTrustSettings;
import io.gravitee.am.model.oidc.TokenExchangeTrustSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTrustDomainV2 implements UpdateTrustDomainRequest {

    @Schema(description = "Payload shape selector. Must be \"v2\" to select this shape.", example = "v2")
    private String version;

    @Schema(description = "New label for the trusted domain. Kept unchanged when absent.")
    @Size(max = TrustDomain.NAME_MAX_LENGTH, message = "Name must be at most {max} characters")
    private String name;

    private String description;

    @Schema(description = "Issuer identifier of this authority's authorization server. Matched against "
            + "the \"iss\" of an external JWT inbound, and carried as the \"aud\" of an ID-JAG outbound. "
            + "Required when tokenExchange is present.",
            example = "https://sso.acme.com")
    @Size(max = TrustDomain.ISSUER_MAX_LENGTH, message = "Domain identifier must be at most {max} characters")
    private String domainIdentifier;

    @Valid
    private TrustDomainKeyMaterial keyMaterial;

    @Valid
    private SpiffeTrustSettings spiffe;

    @Valid
    private TokenExchangeTrustSettings tokenExchange;
}

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

import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.oidc.CrossAppAccessSettings;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class UpdateTrustDomain {

    @Schema(description = "New label for the trusted domain. Left unchanged when absent.")
    @Size(max = TrustDomain.NAME_MAX_LENGTH, message = "Name must be at most {max} characters")
    private String name;

    private String description;

    /**
     * SPIFFE trust domain this authority issues IDs for. Declaring either matcher replaces both, so
     * omitting one clears it; omitting both leaves the trusted domain's usages untouched.
     */
    @Schema(description = "SPIFFE trust domain matched against the \"sub\" of a JWT-SVID. Supplying either "
            + "matcher replaces both; supplying neither leaves them unchanged.")
    @Size(max = TrustDomain.SPIFFE_TRUST_DOMAIN_MAX_LENGTH, message = "SPIFFE trust domain must be at most {max} characters")
    private String spiffeTrustDomain;

    @Size(max = TrustDomain.ISSUER_MAX_LENGTH, message = "Issuer must be at most {max} characters")
    private String issuer;

    private TrustDomainKeyMaterial keyMaterial;

    /**
     * @deprecated supply {@code keyMaterial} instead; ignored when {@code keyMaterial} is present.
     */
    @Deprecated
    @Schema(deprecated = true, description = "Use keyMaterial.source instead.")
    private SpiffeBundleSource bundleSource;

    /**
     * @deprecated supply {@code keyMaterial} instead; ignored when {@code keyMaterial} is present.
     */
    @Deprecated
    @Schema(deprecated = true, description = "Use keyMaterial.jwksUrl instead.")
    private String jwksUrl;

    private Integer refreshIntervalSeconds;

    private List<String> allowedAlgorithms;

    private Map<String, String> scopeMappings;

    private Boolean userBindingEnabled;

    private List<UserBindingCriterion> userBindingCriteria;

    @Schema(description = "What AM issues towards this authority. Replaces what is stored; absent clears it.")
    private CrossAppAccessSettings crossAppAccess;
}

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

import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class NewTrustDomain implements NewTrustDomainRequest {

    @Schema(description = "Payload shape selector. Omit or \"v1\" for this flat shape.", example = "v1")
    private String version;

    @Size(max = TrustDomain.NAME_MAX_LENGTH, message = "Name must be at most {max} characters")
    private String name;

    private String description;

    @Deprecated
    @Schema(deprecated = true, description = "Use keyMaterial.source instead.")
    private SpiffeBundleSource bundleSource;

    @Deprecated
    @Schema(deprecated = true, description = "Use keyMaterial.jwksUrl instead.")
    private String jwksUrl;

    @Deprecated
    @Schema(deprecated = true, description = "Use keyMaterial.refreshIntervalSeconds instead.")
    private Integer refreshIntervalSeconds;

    @Deprecated
    @Schema(deprecated = true, description = "Use spiffe.allowedAlgorithms instead.")
    private List<String> allowedAlgorithms;
}

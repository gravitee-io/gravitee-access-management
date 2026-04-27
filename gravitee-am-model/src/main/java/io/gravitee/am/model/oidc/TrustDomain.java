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

import io.gravitee.am.model.ReferenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * SPIFFE trust domain registered against an AM domain. Owns the bundle source used to
 * verify JWT-SVIDs presented by clients with the {@code spiffe_jwt} auth method.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class TrustDomain {

    public static final int DEFAULT_REFRESH_INTERVAL_SECONDS = 300;

    private String id;
    private String referenceId;
    private ReferenceType referenceType;

    /**
     * Trust-domain name as it appears in SPIFFE IDs. Unique per AM domain.
     */
    private String name;

    private String description;

    private SpiffeBundleSource bundleSource;

    private String jwksUrl;

    private JWKSet staticJwks;

    private int refreshIntervalSeconds = DEFAULT_REFRESH_INTERVAL_SECONDS;

    /**
     * Optional override of {@link SpiffeDomainSettings#getDefaultAllowedAlgorithms()}.
     */
    private List<String> allowedAlgorithms;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public TrustDomain(TrustDomain other) {
        this.id = other.id;
        this.referenceId = other.referenceId;
        this.referenceType = other.referenceType;
        this.name = other.name;
        this.description = other.description;
        this.bundleSource = other.bundleSource;
        this.jwksUrl = other.jwksUrl;
        this.staticJwks = cloneJwkSet(other.staticJwks);
        this.refreshIntervalSeconds = other.refreshIntervalSeconds;
        this.allowedAlgorithms = other.allowedAlgorithms;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    private static JWKSet cloneJwkSet(JWKSet source) {
        if (source == null) {
            return null;
        }
        try {
            return source.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("JWKSet clone failed", e);
        }
    }
}

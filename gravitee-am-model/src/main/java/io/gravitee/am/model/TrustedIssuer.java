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
package io.gravitee.am.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Trusted issuer configuration for RFC 8693 token exchange.
 * Defines an external issuer whose JWTs can be accepted as subject/actor tokens
 * when validated with the configured key material (JWKS URL or PEM certificate).
 *
 * @see TokenExchangeSettings#getTrustedIssuers()
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(title = "Trusted issuer", description = "An external token issuer whose JWTs are accepted as subject or " +
        "actor tokens during token exchange, validated with the configured key material.")
public class TrustedIssuer {

    @Schema(description = "Expected value of the \"iss\" claim in the external JWT.",
            example = "https://issuer.example.com")
    private String issuer;

    @Schema(description = "How the issuer's signing key is resolved. JWKS_URL fetches keys from a JWKS endpoint; " +
            "PEM uses an inline X.509 certificate.")
    private KeyResolutionMethod keyResolutionMethod;

    @Schema(description = "JWKS endpoint URL. Required when keyResolutionMethod is JWKS_URL.",
            example = "https://issuer.example.com/.well-known/jwks.json")
    private String jwksUri;

    @Schema(description = "PEM-encoded X.509 certificate. Required when keyResolutionMethod is PEM.")
    private String certificate;

    @Schema(description = "One-to-one mapping from external scope to domain scope. Unmapped issuer scopes are " +
            "dropped (fail-closed).")
    private Map<String, String> scopeMappings;

    @Schema(description = "Whether the external JWT subject is resolved to a single domain user using the user " +
            "binding criteria. When false, a virtual user is built from the token claims only.",
            defaultValue = "false")
    private boolean userBindingEnabled = false;

    @Schema(description = "Criteria used to locate a domain user when user binding is enabled. All criteria are " +
            "combined with AND.")
    private List<UserBindingCriterion> userBindingCriteria;
}

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
public class TrustedIssuer {

    public static final String KEY_RESOLUTION_JWKS_URL = "JWKS_URL";
    public static final String KEY_RESOLUTION_PEM = "PEM";

    /**
     * The expected "iss" claim value in the external JWT.
     */
    private String issuer;

    /**
     * Key resolution method: {@link #KEY_RESOLUTION_JWKS_URL} or {@link #KEY_RESOLUTION_PEM}.
     */
    private String keyResolutionMethod;

    /**
     * JWKS endpoint URL. Required when keyResolutionMethod is "JWKS_URL".
     */
    private String jwksUri;

    /**
     * PEM-encoded X.509 certificate. Required when keyResolutionMethod is "PEM".
     */
    private String certificate;

    /**
     * Optional 1-to-1 scope mapping: external scope → domain scope.
     * Unmapped issuer scopes are dropped (fail-closed).
     */
    private Map<String, String> scopeMappings;

    /**
     * When true, resolve the external JWT subject to exactly one domain user using
     * {@link #userBindingCriteria}. When false, a virtual user is built from token claims only.
     */
    private boolean userBindingEnabled = false;

    /**
     * List of (attribute, EL expression) pairs used to search for a domain user when
     * {@link #userBindingEnabled} is true. Required when user binding is enabled; all criteria
     * are ANDed. Attribute names must match repository search (e.g. {@code userName}, {@code emails.value}).
     */
    private List<UserBindingCriterion> userBindingCriteria;
}

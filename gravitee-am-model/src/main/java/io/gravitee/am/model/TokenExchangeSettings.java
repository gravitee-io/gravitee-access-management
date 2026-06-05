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

import io.gravitee.am.model.application.TokenExchangeOAuthSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static io.gravitee.am.common.oauth2.TokenType.ACCESS_TOKEN;
import static io.gravitee.am.common.oauth2.TokenType.ID_TOKEN;
import static io.gravitee.am.common.oauth2.TokenType.JWT;
import static io.gravitee.am.common.oauth2.TokenType.REFRESH_TOKEN;

/**
 * RFC 8693 Token Exchange Settings
 *
 * Configuration settings for OAuth 2.0 Token Exchange functionality.
 * Supports both impersonation and delegation use cases.
 * See <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(title = "Token exchange settings", description = "OAuth 2.0 Token Exchange (RFC 8693) configuration for " +
        "the domain, covering impersonation and delegation.")
public class TokenExchangeSettings {

    private static final List<String> DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES = List.of(ACCESS_TOKEN, ID_TOKEN);

    /**
     * Minimum allowed value for maxDelegationDepth.
     */
    public static final int MIN_MAX_DELEGATION_DEPTH = 1;

    /**
     * Maximum allowed value for maxDelegationDepth.
     */
    public static final int MAX_MAX_DELEGATION_DEPTH = 100;

    /**
     * Default value for maxDelegationDepth.
     */
    public static final int DEFAULT_MAX_DELEGATION_DEPTH = 25;

    @Schema(description = "Whether token exchange is enabled for the domain.", defaultValue = "false")
    private boolean enabled = false;

    @Schema(description = "Token types accepted as the subject token in an exchange.",
            example = "[\"urn:ietf:params:oauth:token-type:access_token\",\"urn:ietf:params:oauth:token-type:id_token\"]")
    private List<String> allowedSubjectTokenTypes;

    @Schema(description = "Token types that may be requested as the result of an exchange.",
            example = "[\"urn:ietf:params:oauth:token-type:access_token\",\"urn:ietf:params:oauth:token-type:id_token\"]")
    private List<String> allowedRequestedTokenTypes;

    @Schema(description = "Whether impersonation is allowed, where the issued token represents the subject " +
            "directly. At least one of allowImpersonation or allowDelegation must be enabled.",
            defaultValue = "true")
    private boolean allowImpersonation = true;

    @Schema(description = "Token types accepted as the actor token when delegating.",
            example = "[\"urn:ietf:params:oauth:token-type:access_token\",\"urn:ietf:params:oauth:token-type:id_token\"]")
    private List<String> allowedActorTokenTypes;

    @Schema(description = "Whether delegation is allowed, where an actor acts on behalf of the subject and an " +
            "\"act\" claim is added to the issued token. At least one of allowImpersonation or allowDelegation " +
            "must be enabled.", defaultValue = "false")
    private boolean allowDelegation = false;

    @Schema(description = "Maximum depth of the delegation chain (nested \"act\" claims). Clamped to the range " +
            "1–100.", defaultValue = "25")
    private int maxDelegationDepth = DEFAULT_MAX_DELEGATION_DEPTH;

    @Schema(description = "External issuers whose JWTs may be accepted as subject or actor tokens. When unset, " +
            "only domain-issued tokens are accepted.")
    private List<TrustedIssuer> trustedIssuers;

    @Schema(description = "Domain-level default token-exchange OAuth settings, such as scope handling. " +
            "Applications can inherit or override these. When unset, system defaults apply.")
    private TokenExchangeOAuthSettings tokenExchangeOAuthSettings;

    public TokenExchangeSettings() {
        this.allowedSubjectTokenTypes = new ArrayList<>(List.of(ACCESS_TOKEN, REFRESH_TOKEN, ID_TOKEN, JWT));
        this.allowedRequestedTokenTypes = new ArrayList<>(DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES);
        this.allowedActorTokenTypes = new ArrayList<>(List.of(ACCESS_TOKEN, ID_TOKEN, JWT));
    }

    /**
     * Gets allowed requested token types, returning default values if null.
     * This provides backward compatibility when loading old data that doesn't have this field.
     */
    public List<String> getAllowedRequestedTokenTypes() {
        if (allowedRequestedTokenTypes == null) {
            return new ArrayList<>(DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES);
        }
        return allowedRequestedTokenTypes;
    }

    /**
     * Set maximum delegation depth, clamped to the allowed range
     * [{@link #MIN_MAX_DELEGATION_DEPTH}, {@link #MAX_MAX_DELEGATION_DEPTH}].
     *
     * @param maxDelegationDepth the maximum delegation depth
     */
    public void setMaxDelegationDepth(int maxDelegationDepth) {
        this.maxDelegationDepth = Math.max(MIN_MAX_DELEGATION_DEPTH, Math.min(MAX_MAX_DELEGATION_DEPTH, maxDelegationDepth));
    }

}

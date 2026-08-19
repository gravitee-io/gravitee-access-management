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
package io.gravitee.am.model.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Copies one claim from a validated token onto the token issued by the Token Exchange grant flow
 * (RFC 8693). The mapping is declarative, so an administrator does not have to write an expression.
 */
@Schema(title = "Token exchange claim mapping", description = "Copies a claim from the validated subject " +
        "or actor token onto the issued token.")
public class TokenExchangeClaimMapping {

    @Schema(description = "The validated token the claim is read from.", defaultValue = "SUBJECT_TOKEN")
    private TokenExchangeClaimSource source = TokenExchangeClaimSource.SUBJECT_TOKEN;
    @Schema(description = "The name of the claim on the source token.")
    private String sourceClaim;
    @Schema(description = "The name the claim takes on the issued token.")
    private String tokenClaim;

    public TokenExchangeClaimMapping() {}

    public TokenExchangeClaimMapping(TokenExchangeClaimMapping other) {
        this.source = other.source;
        this.sourceClaim = other.sourceClaim;
        this.tokenClaim = other.tokenClaim;
    }

    public TokenExchangeClaimSource getSource() {
        return source;
    }

    public void setSource(TokenExchangeClaimSource source) {
        this.source = source;
    }

    public String getSourceClaim() {
        return sourceClaim;
    }

    public void setSourceClaim(String sourceClaim) {
        this.sourceClaim = sourceClaim;
    }

    public String getTokenClaim() {
        return tokenClaim;
    }

    public void setTokenClaim(String tokenClaim) {
        this.tokenClaim = tokenClaim;
    }
}

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
package io.gravitee.am.gateway.handler.aauth.service.token;

/**
 * Parsed and verified claims from an {@code aa-resource+jwt} token.
 * Per AAUTH spec Section 6.6.1.
 *
 * @param iss      resource server URL
 * @param aud      token audience (PS URL in three-party mode)
 * @param jti      unique token identifier
 * @param agent    agent identifier ({@code aauth:local@domain})
 * @param agentJkt JWK Thumbprint of agent's signing key
 * @param scope    requested scopes (space-separated, may be null)
 * @param iat      issued-at timestamp (epoch seconds)
 * @param exp      expiration timestamp (epoch seconds)
 */
public record ResourceTokenClaims(
        String iss,
        String aud,
        String jti,
        String agent,
        String agentJkt,
        String scope,
        long iat,
        long exp
) {
}

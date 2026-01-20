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
 * This implementation supports the impersonation use case only.
 * See <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class TokenExchangeSettings {

    /**
     * Enable or disable token exchange functionality.
     */
    private boolean enabled = false;

    /**
     * List of allowed subject token types that can be exchanged.
     * Supports ACCESS_TOKEN, REFRESH_TOKEN, ID_TOKEN, and JWT.
     */
    private List<String> allowedSubjectTokenTypes;

    /**
     * Allow impersonation scenarios where the actor becomes the subject.
     */
    private boolean allowImpersonation = true;

    public TokenExchangeSettings() {
        this.allowedSubjectTokenTypes = new ArrayList<>(List.of(ACCESS_TOKEN, REFRESH_TOKEN, ID_TOKEN, JWT));
    }
}

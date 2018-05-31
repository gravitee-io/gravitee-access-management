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
package io.gravitee.am.gateway.handler.oauth2.revocation;

import io.gravitee.am.gateway.handler.oauth2.utils.TokenTypeHint;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RevocationTokenRequest {

    private final String token;

    private TokenTypeHint hint;

    private String clientId;

    public RevocationTokenRequest(final String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public TokenTypeHint getHint() {
        return hint;
    }

    public void setHint(TokenTypeHint hint) {
        this.hint = hint;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}

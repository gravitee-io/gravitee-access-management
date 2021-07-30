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
package io.gravitee.am.gateway.handler.oauth2.service.introspection;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.idtoken.Claims;

import java.util.Map;

/**
 * The introspection response.
 *
 * See <a href="https://tools.ietf.org/html/rfc7662#section-2.2"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IntrospectionResponse extends JWT {
    private static final String ACTIVE = "active";
    private static final String CLIENT_ID = "client_id";
    private static final String USERNAME = "username";
    private static final String TOKEN_TYPE = "token_type";

    public IntrospectionResponse() {
        super();
    }

    public IntrospectionResponse(boolean active) {
        this();
        setActive(active);
    }

    public boolean isActive() {
        return containsKey(ACTIVE) ? (Boolean) get(ACTIVE) : false;
    }

    public void setActive(boolean active) {
        put(ACTIVE, active);
    }

    public String getClientId() {
        return containsKey(CLIENT_ID) ? (String) get(CLIENT_ID) : null;
    }

    public void setClientId(String clientId) {
        put(CLIENT_ID, clientId);
    }

    public String getUsername() {
        return containsKey(USERNAME) ? (String) get(USERNAME) : null;
    }

    public void setUsername(String username) {
        put(USERNAME, username);
    }

    public String getTokenType() {
        return containsKey(TOKEN_TYPE) ? (String) get(TOKEN_TYPE) : null;
    }

    public void setTokenType(String tokenType) {
        put(TOKEN_TYPE, tokenType);
    }

    public void setConfirmationMethod(Map<String, Object> confirmationMethod) {
        put(Claims.cnf, confirmationMethod);
    }
    public Object getConfirmationMethod() {
        return get(Claims.cnf);
    }
}

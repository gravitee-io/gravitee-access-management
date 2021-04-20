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
package io.gravitee.am.common.oidc.idtoken;

import io.gravitee.am.common.jwt.JWT;
import java.util.Map;

/**
 * The primary extension that OpenID Connect makes to OAuth 2.0 to enable End-Users to be Authenticated is the ID Token data structure.
 * The ID Token is a security token that contains Claims about the Authentication of an End-User by an Authorization Server when using a Client,
 * and potentially other requested Claims.
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDToken">2. ID Token</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IDToken extends JWT {

    public Long getAuthTime() {
        return (Long) get(Claims.auth_time);
    }

    public void setAuthTime(Long authTime) {
        put(Claims.auth_time, authTime);
    }

    public String getNonce() {
        return (String) get(Claims.nonce);
    }

    public void setNonce(String nonce) {
        put(Claims.nonce, nonce);
    }

    public String getAcr() {
        return (String) get(Claims.acr);
    }

    public void setAcr(String acr) {
        put(Claims.acr, acr);
    }

    public String getAmr() {
        return (String) get(Claims.amr);
    }

    public void setAmr(String amr) {
        put(Claims.amr, amr);
    }

    public String getAzp() {
        return (String) get(Claims.azp);
    }

    public void setAzp(String azp) {
        put(Claims.azp, azp);
    }

    public void addAdditionalClaim(String claimName, Object claimValue) {
        putIfAbsent(claimName, claimValue);
    }

    public void setAdditionalClaims(Map<String, Object> additionalClaims) {
        putAll(additionalClaims);
    }
}

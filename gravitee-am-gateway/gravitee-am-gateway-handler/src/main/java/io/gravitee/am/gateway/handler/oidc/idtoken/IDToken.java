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
package io.gravitee.am.gateway.handler.oidc.idtoken;

import io.gravitee.am.gateway.handler.oidc.utils.OIDCClaims;

import java.util.HashMap;
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
public class IDToken extends HashMap<String, Object> {

    public String getIss() {
        return (String) get(OIDCClaims.iss);
    }

    public void setIss(String iss) {
        put(OIDCClaims.iss, iss);
    }

    public String getSub() {
        return (String) get(OIDCClaims.sub);
    }

    public void setSub(String sub) {
        put(OIDCClaims.sub, sub);
    }

    public String getAud() {
        return (String) get(OIDCClaims.aud);
    }

    public void setAud(String aud) {
        put(OIDCClaims.aud, aud);
    }

    public long getExp() {
        return (long) get(OIDCClaims.exp);
    }

    public void setExp(long exp) {
        put(OIDCClaims.exp, exp);
    }

    public long getIat() {
        return (long) get(OIDCClaims.iat);
    }

    public void setIat(long iat) {
        put(OIDCClaims.iat, iat);
    }

    public long getAuthTime() {
        return (long) get(OIDCClaims.auth_time);
    }

    public void setAuthTime(long authTime) {
        put(OIDCClaims.auth_time, authTime);
    }

    public String getNonce() {
        return (String) get(OIDCClaims.nonce);
    }

    public void setNonce(String nonce) {
        put(OIDCClaims.nonce, nonce);
    }

    public String getAcr() {
        return (String) get(OIDCClaims.acr);
    }

    public void setAcr(String acr) {
        put(OIDCClaims.acr, acr);
    }

    public String getAmr() {
        return (String) get(OIDCClaims.amr);
    }

    public void setAmr(String amr) {
        put(OIDCClaims.amr, amr);
    }

    public String getAzp() {
        return (String) get(OIDCClaims.azp);
    }

    public void setAzp(String azp) {
        put(OIDCClaims.azp, azp);
    }

    public void addAdditionalClaim(String claimName, Object claimValue) {
        put(claimName, claimValue);
    }

    public void setAdditionalClaims(Map<String, Object> additionalClaims) {
        putAll(additionalClaims);
    }
}

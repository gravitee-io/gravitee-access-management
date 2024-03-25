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
package io.gravitee.am.common.jwt;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * See <a href="https://tools.ietf.org/html/rfc7519">JSON Web Token (JWT)</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JWT extends HashMap<String, Object> {

    public static final String CONFIRMATION_METHOD_X509_THUMBPRINT = "x5t#S256";

    public JWT() { }

    public JWT(Map<? extends String, ?> claims) {
        super(claims);
    }

    public String getIss() {
        return containsKey(Claims.iss) ? (String) get(Claims.iss) : null;
    }

    public void setIss(String iss) {
        put(Claims.iss, iss);
    }

    public String getSub() {
        return containsKey(Claims.sub) ? (String) get(Claims.sub) : null;
    }

    public void setSub(String sub) {
        put(Claims.sub, sub);
    }

    public String getAud() {
        if (containsKey(Claims.aud)) {
            Object aud = get(Claims.aud);
            if (aud instanceof List && !((List<?>) aud).isEmpty()) {
                return ((List<String>) aud).get(0);
            } else {
                return (String) aud;
            }
        } else {
            return null;
        }
    }

    public void setAud(String aud) {
        put(Claims.aud, aud);
    }

    public long getExp() {
        return containsKey(Claims.exp) ? ((Number) get(Claims.exp)).longValue(): 0l;
    }

    public void setExp(long exp) {
        put(Claims.exp, exp);
    }

    public long getNbf() {
        return containsKey(Claims.nbf) ? ((Number) get(Claims.nbf)).longValue() : 0l;
    }

    public void setNbf(long nbf) {
        put(Claims.nbf, nbf);
    }

    public long getIat() {
        return containsKey(Claims.iat) ? ((Number) get(Claims.iat)).longValue() : 0l;
    }

    public void setIat(long iat) {
        put(Claims.iat, iat);
    }

    public String getJti() {
        return containsKey(Claims.jti) ? (String) get(Claims.jti) : null;
    }

    public void setJti(String jti) {
        put(Claims.jti, jti);
    }

    public String getScope() {
        return containsKey(Claims.scope) ? (String) get(Claims.scope) : null;
    }

    public void setScope(String scope) {
        put(Claims.scope, scope);
    }

    public String getDomain() {
        return containsKey(Claims.domain) ? (String) get(Claims.domain) : null;
    }

    public void setDomain(String domain) {
        put(Claims.domain, domain);
    }

    public Object getClaimsRequestParameter() {
        return get(Claims.claims);
    }

    public void setClaimsRequestParameter(Object claims) {
        put(Claims.claims, claims);
    }

    public boolean hasScope(String scope) {
        return getScope() != null && Arrays.asList(getScope().split("\\s+")).contains(scope);
    }

    public void setConfirmationMethod(Map<String, Object> confirmation) {
        put(Claims.cnf, confirmation);
    }

    public Object getConfirmationMethod() {
        return get(Claims.cnf);
    }
}

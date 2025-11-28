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
import java.util.Collections;
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
        return containsKey(Claims.ISS) ? (String) get(Claims.ISS) : null;
    }

    public void setIss(String iss) {
        put(Claims.ISS, iss);
    }

    public String getSub() {
        return containsKey(Claims.SUB) ? (String) get(Claims.SUB) : null;
    }

    public void setSub(String sub) {
        put(Claims.SUB, sub);
    }

    public String getInternalSub() {
        return containsKey(Claims.GIO_INTERNAL_SUB) ? (String) get(Claims.GIO_INTERNAL_SUB) : null;
    }

    public void setInternalSub(String sub) {
        put(Claims.GIO_INTERNAL_SUB, sub);
    }

    public String getAud() {
        if (containsKey(Claims.AUD)) {
            Object aud = get(Claims.AUD);
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
        put(Claims.AUD, aud);
    }

    public List<String> getAudList() {
        if (containsKey(Claims.AUD)) {
            Object aud = get(Claims.AUD);
            if (aud instanceof List) {
                return (List<String>) aud;
            }
            return Collections.singletonList((String) aud);
        }
        return Collections.emptyList();
    }

    public void setAudList(List<String> audList) {
        put(Claims.AUD, audList);
    }

    public long getExp() {
        return containsKey(Claims.EXP) ? ((Number) get(Claims.EXP)).longValue(): 0L;
    }

    public void setExp(long exp) {
        put(Claims.EXP, exp);
    }

    public long getNbf() {
        return containsKey(Claims.NBF) ? ((Number) get(Claims.NBF)).longValue() : 0L;
    }

    public void setNbf(long nbf) {
        put(Claims.NBF, nbf);
    }

    public long getIat() {
        return containsKey(Claims.IAT) ? ((Number) get(Claims.IAT)).longValue() : 0L;
    }

    public void setIat(long iat) {
        put(Claims.IAT, iat);
    }

    public String getJti() {
        return containsKey(Claims.JTI) ? (String) get(Claims.JTI) : null;
    }

    public void setJti(String jti) {
        put(Claims.JTI, jti);
    }

    public String getScope() {
        return containsKey(Claims.SCOPE) ? (String) get(Claims.SCOPE) : null;
    }

    public void setScope(String scope) {
        put(Claims.SCOPE, scope);
    }

    public String getDomain() {
        return containsKey(Claims.DOMAIN) ? (String) get(Claims.DOMAIN) : null;
    }

    public void setDomain(String domain) {
        put(Claims.DOMAIN, domain);
    }

    public Object getClaimsRequestParameter() {
        return get(Claims.CLAIMS);
    }

    public void setClaimsRequestParameter(Object claims) {
        put(Claims.CLAIMS, claims);
    }

    public boolean hasScope(String scope) {
        return getScope() != null && Arrays.asList(getScope().split("\\s+")).contains(scope);
    }

    public void setConfirmationMethod(Map<String, Object> confirmation) {
        put(Claims.CNF, confirmation);
    }

    public Object getConfirmationMethod() {
        return get(Claims.CNF);
    }
}

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
package io.gravitee.am.gateway.handler.oidc.jwk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * See <a href="https://tools.ietf.org/html/rfc7517#section-4">4. JSON Web Key (JWK) Format</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class JWK {

    /**
     * "kty" (Key Type) Parameter
     */
    @JsonProperty("kty")
    private String kty;

    /**
     * "use" (Public Key Use) Parameter
     */
    @JsonProperty("use")
    private String use;

    /**
     * "key_ops" (Key Operations) Parameter
     */
    @JsonProperty("key_ops")
    private Set<String> keyOps;

    /**
     * "alg" (Algorithm) Parameter
     */
    @JsonProperty("alg")
    private String alg;

    /**
     * "kid" (Key ID) Parameter
     */
    @JsonProperty("kid")
    private String kid;

    /**
     * "x5u" (X.509 URL) Parameter
     */
    @JsonProperty("x5u")
    private String x5u;

    /**
     * "x5c" (X.509 Certificate Chain) Parameter
     */
    @JsonProperty("x5c")
    private Set<String> x5c;

    /**
     * "x5t" (X.509 Certificate SHA-1 Thumbprint) Parameter
     */
    @JsonProperty("x5t")
    private String x5t;

    /**
     * "x5t#S256" (X.509 Certificate SHA-256 Thumbprint) Parameter
     */
    @JsonProperty("x5t#S256")
    private String x5tS256;

    public String getKty() {
        return kty;
    }

    public void setKty(String kty) {
        this.kty = kty;
    }

    public String getUse() {
        return use;
    }

    public void setUse(String use) {
        this.use = use;
    }

    public Set<String> getKeyOps() {
        return keyOps;
    }

    public void setKeyOps(Set<String> keyOps) {
        this.keyOps = keyOps;
    }

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
    }

    public String getKid() {
        return kid;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    public String getX5u() {
        return x5u;
    }

    public void setX5u(String x5u) {
        this.x5u = x5u;
    }

    public Set<String> getX5c() {
        return x5c;
    }

    public void setX5c(Set<String> x5c) {
        this.x5c = x5c;
    }

    public String getX5t() {
        return x5t;
    }

    public void setX5t(String x5t) {
        this.x5t = x5t;
    }

    public String getX5tS256() {
        return x5tS256;
    }

    public void setX5tS256(String x5tS256) {
        this.x5tS256 = x5tS256;
    }
}

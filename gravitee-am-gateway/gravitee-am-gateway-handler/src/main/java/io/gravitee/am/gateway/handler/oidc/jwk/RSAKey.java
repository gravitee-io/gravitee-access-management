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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * See <a href="https://tools.ietf.org/html/rfc7638#section-3.2">3.2. JWK Members Used in the Thumbprint Computation</a>
 *
 *  The required members for an RSA public key, in lexicographic order, are:
 *    - "e"
 *    - "kty"
 *    - "n"
 * @author Titouan COMPIEGNE (titouan.compiegne@graviteesource.com)
 * @author GraviteeSource Team
 */
public class RSAKey extends JWK {

    /**
     * The public exponent of the RSA key.
     */
    @JsonProperty("e")
    private String e;

    /**
     * The modulus value for the RSA key.
     */
    @JsonProperty("n")
    private String n;

    public String getE() {
        return e;
    }

    public void setE(String e) {
        this.e = e;
    }

    public String getN() {
        return n;
    }

    public void setN(String n) {
        this.n = n;
    }

    public static RSAKey from(io.gravitee.am.model.jose.RSAKey source) {
        RSAKey rsaKey = new RSAKey();
        rsaKey.setE(source.getE());
        rsaKey.setN(source.getN());
        rsaKey.copy(source);
        return rsaKey;
    }
}

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
package io.gravitee.am.model.jose;

/**
 * See <a href="https://tools.ietf.org/html/rfc7638#section-3.2">3.2. JWK Members Used in the Thumbprint Computation</a>
 * See <a href="https://tools.ietf.org/html/draft-jones-jose-json-private-and-symmetric-key-00#section-3.2">JWK Parameters for RSA Private Keys</a>
 *
 *  The required members for an RSA public key, in lexicographic order, are:
 *    - "e"
 *    - "kty"
 *    - "n"
 *
 * @author Titouan COMPIEGNE (titouan.compiegne@graviteesource.com)
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RSAKey extends JWK {

    public RSAKey() {
        setKty(KeyType.RSA.getKeyType());
    }

    /**
     * The public exponent of the RSA key.
     */
    private String e;
    /**
     * The modulus value for the RSA key.
     */
    private String n;

    /**
     * Private exponent value for the RSA private key.
     */
    private String d;

    /**
     * First prime factor for the RSA private key.
     */
    private String p;

    /**
     * Second prime factor for the RSA private key.
     */
    private String q;

    /**
     * First factor CRT exponent for the RSA private key.
     */
    private String dp;

    /**
     * Second factor CRT exponent for the RSA private key.
     */
    private String dq;

    /**
     * First CRT coefficient for the RSA private key.
     */
    private String qi;

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

    public String getD() {
        return d;
    }

    public void setD(String d) {
        this.d = d;
    }

    public String getP() {
        return p;
    }

    public void setP(String p) {
        this.p = p;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getDp() {
        return dp;
    }

    public void setDp(String dp) {
        this.dp = dp;
    }

    public String getDq() {
        return dq;
    }

    public void setDq(String dq) {
        this.dq = dq;
    }

    public String getQi() {
        return qi;
    }

    public void setQi(String qi) {
        this.qi = qi;
    }

    public boolean isPrivate() {
        // Check if 1st or 2nd form params are specified, or PKCS#11 handle
        return d != null || p != null;
    }
}

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
package io.gravitee.am.gateway.handler.aauth.signing;

import java.util.Map;

/**
 * Parsed Signature-Key header information.
 * <p>
 * Example: {@code sig=hwk;kty="OKP";crv="Ed25519";x="..."}
 *
 * @param label  the signature label (e.g. "sig")
 * @param scheme the Signature-Key scheme (e.g. "hwk", "jwks_uri", "jwt")
 * @param params the scheme parameters as key-value pairs
 */
public record SignatureKeyInfo(
        String label,
        String scheme,
        Map<String, String> params
) {
    /**
     * Get a parameter value by name.
     * <p>
     * Common parameters:
     * <ul>
     *   <li><code>kty</code> — Key Type (e.g. "OKP" for Edwards-curve, "EC" for Elliptic Curve)</li>
     *   <li><code>crv</code> — Curve name (e.g. "Ed25519", "P-256")</li>
     *   <li><code>x</code> — Public key x-coordinate (base64url-encoded)</li>
     *   <li><code>y</code> — Public key y-coordinate (base64url-encoded, EC only)</li>
     *   <li><code>jwt</code> — Compact JWT serialization (for the jwt scheme)</li>
     * </ul>
     */
    public String getParam(String name) {
        return params.get(name);
    }
}

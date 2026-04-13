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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal parser for the Signature-Key header (RFC 8941 Dictionary, single member).
 * <p>
 * Parses: sig=hwk;kty="OKP";crv="Ed25519";x="base64url"
 * Into: SignatureKeyInfo(label="sig", scheme="hwk", params={kty=OKP, crv=Ed25519, x=base64url})
 * <p>
 * Also handles: sig=jwt; jwt="eyJhbGc..."
 * Into: SignatureKeyInfo(label="sig", scheme="jwt", params={jwt=eyJhbGc...})
 */
public final class SignatureKeyParser {

    private SignatureKeyParser() {
    }

    /**
     * Parse a Signature-Key header value.
     *
     * @param headerValue the raw header value, e.g. {@code sig=hwk;kty="OKP";crv="Ed25519";x="..."}
     * @return parsed SignatureKeyInfo
     * @throws SignatureVerificationException if the header is malformed
     */
    public static SignatureKeyInfo parse(String headerValue) throws SignatureVerificationException {
        if (headerValue == null || headerValue.isBlank()) {
            throw new SignatureVerificationException("invalid_request");
        }

        // Step 1: Split label from the rest on first '='
        int eqIdx = headerValue.indexOf('=');
        if (eqIdx <= 0) {
            throw new SignatureVerificationException("invalid_key");
        }
        String label = headerValue.substring(0, eqIdx).trim();
        String rest = headerValue.substring(eqIdx + 1).trim();

        // Step 2: Split scheme (first token before ';') from params
        int semiIdx = rest.indexOf(';');
        String scheme;
        String paramsPart;
        if (semiIdx < 0) {
            scheme = rest.trim();
            paramsPart = "";
        } else {
            scheme = rest.substring(0, semiIdx).trim();
            paramsPart = rest.substring(semiIdx + 1).trim();
        }

        // Step 3: Parse params — split on ';', each param is key="value" or key=value
        Map<String, String> params = new LinkedHashMap<>();
        if (!paramsPart.isEmpty()) {
            parseParams(paramsPart, params);
        }

        return new SignatureKeyInfo(label, scheme, params);
    }

    private static void parseParams(String paramsPart, Map<String, String> params) throws SignatureVerificationException {
        // Handle the case where a param value contains ';' (e.g. inside a JWT)
        // We use a state machine to track whether we're inside quotes
        int i = 0;
        while (i < paramsPart.length()) {
            // Skip whitespace
            while (i < paramsPart.length() && paramsPart.charAt(i) == ' ') i++;
            if (i >= paramsPart.length()) break;

            // Find '='
            int keyEnd = paramsPart.indexOf('=', i);
            if (keyEnd < 0) {
                throw new SignatureVerificationException("invalid_key");
            }
            String key = paramsPart.substring(i, keyEnd).trim();
            i = keyEnd + 1;

            // Parse value — may be quoted
            String value;
            if (i < paramsPart.length() && paramsPart.charAt(i) == '"') {
                // Quoted value — find closing quote
                int closeQuote = paramsPart.indexOf('"', i + 1);
                if (closeQuote < 0) {
                    throw new SignatureVerificationException("invalid_key");
                }
                value = paramsPart.substring(i + 1, closeQuote);
                i = closeQuote + 1;
                // Skip past optional ';'
                if (i < paramsPart.length() && paramsPart.charAt(i) == ';') i++;
            } else {
                // Unquoted value — read until ';' or end
                int nextSemi = paramsPart.indexOf(';', i);
                if (nextSemi < 0) {
                    value = paramsPart.substring(i).trim();
                    i = paramsPart.length();
                } else {
                    value = paramsPart.substring(i, nextSemi).trim();
                    i = nextSemi + 1;
                }
            }

            params.put(key, value);
        }
    }
}

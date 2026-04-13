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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal parser for the Signature-Input header (RFC 8941 Dictionary, inner list with params).
 *
 * Parses: sig=("@method" "@authority" "@path" "signature-key");created=1712345678
 * Into: SignatureInputInfo(label="sig", components=[@method, @authority, @path, signature-key], created=1712345678)
 */
public final class SignatureInputParser {

    private static final Pattern CREATED_PATTERN = Pattern.compile("created=(\\d+)");

    private SignatureInputParser() {
    }

    /**
     * Parse a Signature-Input header value.
     *
     * @param headerValue the raw header value
     * @return parsed SignatureInputInfo
     * @throws SignatureVerificationException if the header is malformed
     */
    public static SignatureInputInfo parse(String headerValue) throws SignatureVerificationException {
        if (headerValue == null || headerValue.isBlank()) {
            throw new SignatureVerificationException("invalid_request");
        }

        // Step 1: Split label from rest on first '='
        int eqIdx = headerValue.indexOf('=');
        if (eqIdx <= 0) {
            throw new SignatureVerificationException("invalid_request");
        }
        String label = headerValue.substring(0, eqIdx).trim();
        String rest = headerValue.substring(eqIdx + 1).trim();

        // Step 2: Extract inner list between ( and )
        int openParen = rest.indexOf('(');
        int closeParen = rest.indexOf(')');
        if (openParen < 0 || closeParen < 0 || closeParen <= openParen) {
            throw new SignatureVerificationException("invalid_request");
        }

        String innerList = rest.substring(openParen + 1, closeParen).trim();
        List<String> components = parseInnerList(innerList);

        // Step 3: Extract params after ')'
        String paramsPart = rest.substring(closeParen + 1).trim();

        // Step 4: Parse 'created' param
        long created = parseCreated(paramsPart);

        // The raw value is everything after label= (used to build @signature-params)
        String rawValue = rest;

        return new SignatureInputInfo(label, components, created, rawValue);
    }

    private static List<String> parseInnerList(String innerList) {
        List<String> components = new ArrayList<>();
        // Components are quoted strings separated by spaces: "@method" "@authority" "@path" "signature-key"
        int i = 0;
        while (i < innerList.length()) {
            while (i < innerList.length() && innerList.charAt(i) == ' ') i++;
            if (i >= innerList.length()) break;

            if (innerList.charAt(i) == '"') {
                int closeQuote = innerList.indexOf('"', i + 1);
                if (closeQuote < 0) break;
                components.add(innerList.substring(i + 1, closeQuote));
                i = closeQuote + 1;
            } else {
                // Unquoted token — read until space or end
                int end = innerList.indexOf(' ', i);
                if (end < 0) end = innerList.length();
                components.add(innerList.substring(i, end));
                i = end;
            }
        }
        return components;
    }

    private static long parseCreated(String paramsPart) throws SignatureVerificationException {
        Matcher matcher = CREATED_PATTERN.matcher(paramsPart);
        if (!matcher.find()) {
            throw new SignatureVerificationException("invalid_request");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new SignatureVerificationException("invalid_request");
        }
    }
}

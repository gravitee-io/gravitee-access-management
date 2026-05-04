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
package io.gravitee.am.gateway.handler.aauth.util;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Validates AAUTH identifiers and URLs per the protocol specification.
 *
 * Server identifiers: HTTPS, no port/path/query/fragment, lowercase, no trailing slash.
 * Agent identifiers: aauth:local@domain format.
 * Endpoint URLs: HTTPS, no fragment.
 */
public final class AAuthIdentifierValidator {

    // local part: lowercase a-z, 0-9, hyphen, underscore, plus, period
    private static final Pattern AGENT_ID_PATTERN =
            Pattern.compile("^aauth:[a-z0-9._+\\-]{1,255}@[a-z0-9.\\-]+$");

    private AAuthIdentifierValidator() {
    }

    /**
     * Validate a server identifier (agent server, PS, AS, or resource URL).
     * Must be HTTPS, lowercase, no port, no path, no query, no fragment, no trailing slash.
     */
    public static boolean isValidServerIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        try {
            URI uri = URI.create(identifier);
            return "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getPort() == -1
                    && (uri.getPath() == null || uri.getPath().isEmpty())
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && !identifier.endsWith("/")
                    && identifier.equals(identifier.toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Validate an agent identifier.
     * Format: aauth:local@domain where local is [a-z0-9._+-] and domain is a valid domain.
     */
    public static boolean isValidAgentIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        return AGENT_ID_PATTERN.matcher(identifier).matches();
    }

    /**
     * Validate an endpoint URL (token_endpoint, authorization_endpoint, etc.).
     * Must be HTTPS, no fragment.
     */
    public static boolean isValidEndpointUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Validate the {@code agent_server} URL submitted on a bootstrap request
     * (draft-hardt-aauth-bootstrap §6.2). The URL is placed in
     * {@code bootstrap_token.aud} and used to discover Agent Server metadata,
     * so it must be a clean origin URL: parseable, with a host, no query,
     * no fragment, no userinfo.
     *
     * @param url            the candidate URL
     * @param allowInsecure  when true, accept {@code http://} as well as {@code https://}
     *                       (development convenience — see {@code AAuthSettings})
     * @return {@code null} if the URL is valid; otherwise a short, end-user-facing
     *         description of the first failure encountered
     */
    public static String validateAgentServerUrl(String url, boolean allowInsecure) {
        if (url == null || url.isBlank()) {
            return "agent_server is required";
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return "agent_server is not a valid URL";
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return "agent_server must include a scheme";
        }
        boolean schemeOk = "https".equals(scheme) || (allowInsecure && "http".equals(scheme));
        if (!schemeOk) {
            return allowInsecure
                    ? "agent_server must use http or https scheme"
                    : "agent_server must use https scheme";
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            return "agent_server must include a host";
        }
        if (uri.getRawQuery() != null) {
            return "agent_server must not include a query string";
        }
        if (uri.getRawFragment() != null) {
            return "agent_server must not include a fragment";
        }
        if (uri.getRawUserInfo() != null) {
            return "agent_server must not include user info";
        }
        return null;
    }
}

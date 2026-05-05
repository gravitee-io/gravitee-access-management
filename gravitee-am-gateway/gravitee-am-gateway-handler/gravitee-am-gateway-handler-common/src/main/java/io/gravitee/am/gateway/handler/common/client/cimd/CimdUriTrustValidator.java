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
package io.gravitee.am.gateway.handler.common.client.cimd;

import io.gravitee.am.common.web.PrivateOrReservedHostException;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.web.HostSsrfGuard;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Completable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Validates CIMD HTTP(S) URLs for metadata and logo fetching: parses URLs, applies domain trust policy
 * (scheme, allowed host patterns, private or reserved IP literals in the authority), and optionally
 * verifies DNS resolution does not map to private addresses via {@link HostSsrfGuard}.
 */
public class CimdUriTrustValidator {

    private final HostSsrfGuard hostSsrfGuard;

    public CimdUriTrustValidator(HostSsrfGuard hostSsrfGuard) {
        this.hostSsrfGuard = hostSsrfGuard;
    }

    /**
     * Parses {@code value} as an HTTP(S) URL suitable for Vert.x HTTP client use.
     *
     * @throws InvalidClientMetadataException when the string is not a valid HTTP(S) URL
     */
    public URI parseHttpUrl(String value, String fieldName) {
        try {
            return UriBuilder.fromHttpUrl(value).build();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            throw new InvalidClientMetadataException(fieldName + " is not a valid URL.");
        }
    }

    /**
     * Validates scheme, host presence, allowed-domain patterns, and private/reserved IP literals in the host part.
     */
    public void validateTrust(URI uri, CIMDSettings settings, String fieldName) {
        if (!settings.isAllowUnsecuredHttpUri() && "http".equalsIgnoreCase(uri.getScheme())) {
            throw new InvalidClientMetadataException("Unsecured HTTP " + fieldName + " is not allowed.");
        }

        final String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidClientMetadataException(fieldName + " host is missing.");
        }

        if (!isAllowedDomain(host, settings.getAllowedDomains())) {
            throw new InvalidClientMetadataException(fieldName + " host is not in allowed domains.");
        }

        if (!settings.isAllowPrivateIpAddress() && UriBuilder.isPrivateOrReservedIpLiteral(host)) {
            throw new InvalidClientMetadataException(fieldName + " resolves to a private or reserved IP address.");
        }
    }

    /**
     * When private IPs are disallowed, resolves {@code host} and rejects private/reserved targets (DNS rebinding protection).
     */
    public Completable validateResolvableHost(String host, String fieldName, CIMDSettings settings) {
        if (settings.isAllowPrivateIpAddress()) {
            return Completable.complete();
        }
        return hostSsrfGuard.assertNotPrivateHost(host)
                .onErrorResumeNext(e -> e instanceof PrivateOrReservedHostException
                        ? Completable.error(new InvalidClientMetadataException(
                                fieldName + " resolves to a private or reserved IP address."))
                        : Completable.error(e));
    }

    private static boolean isAllowedDomain(String host, List<String> allowedDomains) {
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            return true;
        }

        final String normalizedHost = host.toLowerCase(Locale.ROOT);

        return allowedDomains.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .map(pattern -> pattern.toLowerCase(Locale.ROOT))
                .anyMatch(pattern -> {
                    if (pattern.startsWith("*.")) {
                        final String suffix = pattern.substring(2);
                        if (suffix.isEmpty() || !normalizedHost.endsWith("." + suffix)) {
                            return false;
                        }
                        final String subdomain = normalizedHost.substring(0, normalizedHost.length() - suffix.length() - 1);
                        return !subdomain.isEmpty();
                    }
                    return normalizedHost.equals(pattern);
                });
    }
}

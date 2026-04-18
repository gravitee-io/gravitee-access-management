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
package io.gravitee.am.gateway.handler.oidc.service.cimd;

import io.gravitee.am.model.oidc.CIMDSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Validates URIs against SSRF protection rules defined in CIMD settings.
 *
 * @author GraviteeSource Team
 */
public class SSRFValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSRFValidator.class);

    @FunctionalInterface
    public interface HostResolver {
        InetAddress resolve(String host) throws UnknownHostException;
    }

    private HostResolver hostResolver = InetAddress::getByName;

    public void setHostResolver(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    /**
     * Validate a URI against the CIMD SSRF protection settings.
     *
     * @throws CIMDException if validation fails
     */
    public void validate(URI uri, CIMDSettings settings) {
        validateAndResolve(uri, settings);
    }

    /**
     * Validate a URI and return the resolved {@link InetAddress} so callers can
     * pin the HTTP connection to the exact IP that was validated. Using the
     * returned address for the subsequent HTTP fetch prevents DNS rebinding:
     * without pinning, the HTTP client performs its own resolution that an
     * attacker with a short-TTL record can flip to a private/internal IP.
     *
     * @throws CIMDException if validation fails
     */
    public InetAddress validateAndResolve(URI uri, CIMDSettings settings) {
        validateScheme(uri, settings);
        validateDomainWhitelist(uri, settings);
        return validateIpAddress(uri, settings);
    }

    private void validateScheme(URI uri, CIMDSettings settings) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new CIMDException("CIMD metadata URI must have a scheme");
        }
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new CIMDException("CIMD metadata URI scheme must be http or https, got: " + scheme);
        }
        if ("http".equalsIgnoreCase(scheme) && !settings.isAllowUnsecuredHttpUri()) {
            throw new CIMDException("CIMD metadata URI must use https (HTTP is not allowed by domain policy)");
        }
    }

    private void validateDomainWhitelist(URI uri, CIMDSettings settings) {
        List<String> allowedDomains = settings.getAllowedDomains();
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            return; // no restrictions
        }

        String host = uri.getHost();
        if (host == null) {
            throw new CIMDException("CIMD metadata URI must have a host");
        }

        boolean matched = allowedDomains.stream().anyMatch(pattern -> matchesDomain(host, pattern));
        if (!matched) {
            throw new CIMDException("CIMD metadata URI host '" + host + "' is not in the allowed domains list");
        }
    }

    private InetAddress validateIpAddress(URI uri, CIMDSettings settings) {
        String host = uri.getHost();
        if (host == null) {
            return null;
        }

        InetAddress address;
        try {
            address = hostResolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new CIMDException("CIMD metadata URI host '" + host + "' could not be resolved");
        }

        if (!settings.isAllowPrivateIpAddress()
                && (address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isAnyLocalAddress())) {
            throw new CIMDException("CIMD metadata URI resolves to a private/loopback address (not allowed by domain policy)");
        }
        return address;
    }

    static boolean matchesDomain(String host, String pattern) {
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1); // e.g. ".example.com"
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return host.equalsIgnoreCase(pattern);
    }
}

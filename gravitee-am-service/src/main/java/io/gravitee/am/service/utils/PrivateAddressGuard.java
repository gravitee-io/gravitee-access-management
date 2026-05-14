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
package io.gravitee.am.service.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Helpers for refusing to dial hosts that resolve to private, loopback or link-local
 * addresses — used for SSRF protection on admin-supplied URLs.
 */
public final class PrivateAddressGuard {

    private PrivateAddressGuard() {
    }

    public static boolean isPrivate(InetAddress addr) {
        return addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || isUniqueLocalIpv6(addr);
    }

    /**
     * Resolve {@code host} and return the first address that fails {@link #isPrivate(InetAddress)}.
     * Empty if every resolved address is public.
     */
    public static Optional<InetAddress> firstPrivateAddress(String host) throws UnknownHostException {
        return Arrays.stream(InetAddress.getAllByName(host))
                .filter(PrivateAddressGuard::isPrivate)
                .findFirst();
    }

    /**
     * Validates an admin-supplied HTTP(S) URL against a trust policy: parseable URI, http/https
     * scheme (http only when {@code allowHttp}), non-blank host, and — unless {@code allowPrivateIp}
     * — DNS resolution that does not hit a private/loopback/link-local address. The returned
     * reason uses {@code fieldName} as the field prefix; empty when the URL passes.
     */
    public static Optional<String> validateHttpUrl(String fieldName, String urlValue, boolean allowHttp, boolean allowPrivateIp) {
        if (urlValue == null || urlValue.isBlank()) {
            return Optional.of(fieldName + " is required");
        }
        URI uri;
        try {
            uri = URI.create(urlValue);
        } catch (IllegalArgumentException e) {
            return Optional.of(fieldName + " is not a valid URI");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return Optional.of(fieldName + " must include a scheme");
        }
        boolean isHttp = "http".equalsIgnoreCase(scheme);
        boolean isHttps = "https".equalsIgnoreCase(scheme);
        if (!isHttp && !isHttps) {
            return Optional.of(fieldName + " scheme must be http or https");
        }
        if (isHttp && !allowHttp) {
            return Optional.of("http:// " + fieldName + " is not allowed by current policy");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return Optional.of(fieldName + " must include a host");
        }
        if (!allowPrivateIp) {
            try {
                Optional<InetAddress> privateAddr = firstPrivateAddress(host);
                if (privateAddr.isPresent()) {
                    return Optional.of(fieldName + " host " + host + " resolves to a private/loopback address ("
                            + privateAddr.get().getHostAddress() + ")");
                }
            } catch (UnknownHostException e) {
                return Optional.of(fieldName + " host " + host + " could not be resolved");
            }
        }
        return Optional.empty();
    }

    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
}

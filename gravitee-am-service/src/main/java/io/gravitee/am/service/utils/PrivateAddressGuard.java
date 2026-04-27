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

    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
}

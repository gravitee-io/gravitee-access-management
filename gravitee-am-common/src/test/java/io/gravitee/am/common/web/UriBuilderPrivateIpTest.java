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
package io.gravitee.am.common.web;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UriBuilderPrivateIpTest {

    private final String description;
    private final String host;
    private final boolean expected;

    public UriBuilderPrivateIpTest(String description, String host, boolean expected) {
        this.description = description;
        this.host = host;
        this.expected = expected;
    }

    @Parameters(name = "{0}: isPrivateOrReservedIpLiteral({1}) = {2}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                // Localhost hostname — no DNS; handled before IP-literal checks
                {"localhost name",       "localhost",        true},
                // Loopback — delegated to isLocalhost
                {"loopback ipv4",        "127.0.0.1",        true},
                {"loopback ipv6",        "::1",              true},
                // Private class A
                {"private class A low",  "10.0.0.1",         true},
                {"private class A high", "10.255.255.255",   true},
                // Private class B
                {"private class B low",  "172.16.0.1",       true},
                {"private class B high", "172.31.255.255",   true},
                {"class B lower edge",   "172.15.255.255",   false},
                {"class B upper edge",   "172.32.0.0",       false},
                // Private class C
                {"private class C",      "192.168.0.1",      true},
                {"private class C high", "192.168.255.255",  true},
                // IPv4 link-local (includes AWS IMDS endpoint)
                {"link-local ipv4",      "169.254.0.1",      true},
                {"AWS IMDS",             "169.254.169.254",  true},
                // Any-local
                {"any-local",            "0.0.0.0",          true},
                // IPv6 link-local (fe80::/10)
                {"link-local ipv6",      "fe80::1",          true},
                {"link-local ipv6 alt",  "fe80:0:0:0:1:2:3:4", true},
                // IPv6 unique-local (fc00::/7 — fc and fd prefixes)
                {"unique-local fc",      "fc00::1",          true},
                {"unique-local fd",      "fd12:3456::1",     true},
                // Public addresses — must not be blocked
                {"public ipv4 Google",   "8.8.8.8",          false},
                {"public ipv4 CF",       "1.1.1.1",          false},
                {"public ipv6",          "2606:4700::1",     false},
                // Hostnames — must return false (not IP literals)
                {"hostname",             "example.com",      false},
                {"unresolvable",         "non-resolvable.invalid", false},
        });
    }

    @Test
    public void test() {
        Assert.assertEquals(description, expected, UriBuilder.isPrivateOrReservedIpLiteral(host));
    }
}

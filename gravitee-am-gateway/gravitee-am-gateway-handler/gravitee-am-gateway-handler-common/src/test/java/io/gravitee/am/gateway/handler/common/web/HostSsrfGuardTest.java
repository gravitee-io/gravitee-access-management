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
package io.gravitee.am.gateway.handler.common.web;

import io.gravitee.am.common.web.PrivateOrReservedHostException;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class HostSsrfGuardTest {

    @Test
    public void shouldRejectHostnameThatResolvesToLoopbackAddress() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        HostSsrfGuard guard = new HostSsrfGuard(host -> new InetAddress[]{loopback});

        TestObserver<?> obs = guard.assertNotPrivateHost("internal.example.com").test();
        obs.awaitDone(2, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertError(PrivateOrReservedHostException.class);
    }

    @Test
    public void shouldRejectHostnameThatResolvesToPrivateSiteLocalAddress() throws Exception {
        InetAddress privateIp = InetAddress.getByName("10.0.0.1");
        HostSsrfGuard guard = new HostSsrfGuard(host -> new InetAddress[]{privateIp});

        TestObserver<?> obs = guard.assertNotPrivateHost("internal.corp").test();
        obs.awaitDone(2, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertError(PrivateOrReservedHostException.class);
    }

    @Test
    public void shouldRejectHostnameThatResolvesToLinkLocalAddress() throws Exception {
        InetAddress linkLocal = InetAddress.getByName("169.254.169.254");
        HostSsrfGuard guard = new HostSsrfGuard(host -> new InetAddress[]{linkLocal});

        TestObserver<?> obs = guard.assertNotPrivateHost("metadata.local").test();
        obs.awaitDone(2, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertError(e -> e instanceof PrivateOrReservedHostException
                && e.getMessage().contains("link-local"));
    }

    @Test
    public void shouldAllowHostnameThatResolvesToPublicAddress() throws Exception {
        InetAddress publicIp = InetAddress.getByName("8.8.8.8");
        HostSsrfGuard guard = new HostSsrfGuard(host -> new InetAddress[]{publicIp});

        TestObserver<?> obs = guard.assertNotPrivateHost("example.com").test();
        obs.awaitDone(2, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoErrors();
    }

    @Test
    public void shouldAllowUnresolvableHostname() {
        HostSsrfGuard guard = new HostSsrfGuard(host -> { throw new UnknownHostException(host); });

        TestObserver<?> obs = guard.assertNotPrivateHost("non-resolvable.invalid").test();
        obs.awaitDone(2, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoErrors();
    }

    @Test
    public void shouldSkipDnsCheckForIpv4Literal() {
        HostSsrfGuard.DnsResolver neverCalled = host -> {
            throw new AssertionError("DNS resolver must not be called for IP literals");
        };
        HostSsrfGuard guard = new HostSsrfGuard(neverCalled);

        // IP literals are handled by isIpLiteral() short-circuit
        TestObserver<?> obs = guard.assertNotPrivateHost("10.0.0.1").test();
        obs.awaitDone(2, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoErrors();
    }

    @Test
    public void shouldSkipDnsCheckForIpv6Literal() {
        HostSsrfGuard.DnsResolver neverCalled = host -> {
            throw new AssertionError("DNS resolver must not be called for IP literals");
        };
        HostSsrfGuard guard = new HostSsrfGuard(neverCalled);

        TestObserver<?> obs = guard.assertNotPrivateHost("::1").test();
        obs.awaitDone(2, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoErrors();
    }

    @Test
    public void shouldHandleNullHostGracefully() {
        HostSsrfGuard guard = new HostSsrfGuard();

        TestObserver<?> obs = guard.assertNotPrivateHost(null).test();
        obs.awaitDone(2, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoErrors();
    }
}

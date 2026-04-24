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
import io.gravitee.am.common.web.UriBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Guards against SSRF by rejecting hostnames whose DNS resolution includes a private,
 * loopback, link-local, or any-local address.
 * 
 * @author GraviteeSource Team
 */
public class HostSsrfGuard {

    @FunctionalInterface
    public interface DnsResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final DnsResolver dnsResolver;

    public HostSsrfGuard() {
        this(InetAddress::getAllByName);
    }

    public HostSsrfGuard(DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    /**
     * Resolves {@code host} via DNS and completes with an error if any resolved address is
     * private, loopback, link-local, or any-local. IP literals are skipped — callers should
     * validate literals separately (for example {@link UriBuilder#isPrivateOrReservedIpLiteral(String)}).
     * Unresolvable hosts complete normally so a later HTTP client can surface a more specific error.
     */
    public Completable assertNotPrivateHost(String host) {
        if (host == null || UriBuilder.isIpLiteral(host)) {
            return Completable.complete();
        }
        return Single.fromCallable(() -> dnsResolver.resolve(host))
                .subscribeOn(Schedulers.io())
                .flatMapCompletable(addrs -> {
                    for (InetAddress addr : addrs) {
                        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                                || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                            return Completable.error(new PrivateOrReservedHostException());
                        }
                    }
                    return Completable.complete();
                })
                .onErrorResumeNext(e -> e instanceof PrivateOrReservedHostException
                        ? Completable.error(e)
                        : Completable.complete());
    }
}

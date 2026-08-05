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
package io.gravitee.am.gateway.reactor.impl.router;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.junit.Test;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VHostDomainIndexTest {

    private final VHostDomainIndex index = new VHostDomainIndex();

    @Test
    public void shouldDispatchToMatchingHostAndSetEmptyContextPathForRootVhost() {
        Router delegate = mock(Router.class);
        mount(delegate, "domain1", "domain1.local.am", "/");

        RoutingContext context = routingContext("domain1.local.am", "/oidc/.well-known/jwks.json");
        index.dispatch(context);

        verify(delegate).handleContext(context);
        verify(context).put(CONTEXT_PATH, "");
        verify(context, never()).next();
    }

    @Test
    public void shouldSetContextPathForNonRootVhostPath() {
        Router delegate = mock(Router.class);
        mount(delegate, "domain1", "domain1.local.am", "/customers");

        RoutingContext context = routingContext("domain1.local.am", "/customers/login");
        index.dispatch(context);

        verify(delegate).handleContext(context);
        verify(context).put(CONTEXT_PATH, "/customers");
    }

    @Test
    public void shouldMatchHostIgnoringCase() {
        Router delegate = mock(Router.class);
        mount(delegate, "domain1", "domain1.local.am", "/");

        RoutingContext context = routingContext("DOMAIN1.LOCAL.AM", "/login");
        index.dispatch(context);

        verify(delegate).handleContext(context);
    }

    @Test
    public void shouldCallNextWhenHostIsNotRegistered() {
        Router delegate = mock(Router.class);
        mount(delegate, "domain1", "domain1.local.am", "/");

        RoutingContext context = routingContext("unknown.local.am", "/login");
        index.dispatch(context);

        verify(delegate, never()).handleContext(context);
        verify(context).next();
    }

    @Test
    public void shouldCallNextWhenPathDoesNotMatchForRegisteredHost() {
        Router delegate = mock(Router.class);
        mount(delegate, "domain1", "domain1.local.am", "/customers");

        RoutingContext context = routingContext("domain1.local.am", "/other");
        index.dispatch(context);

        verify(delegate, never()).handleContext(context);
        verify(context).next();
    }

    @Test
    public void shouldPreferMoreSpecificPathOverCatchAllOnSameHost() {
        Router catchAll = mock(Router.class);
        Router specific = mock(Router.class);
        mount(catchAll, "domain1", "shared.local.am", "/");
        mount(specific, "domain1", "shared.local.am", "/customers");

        RoutingContext specificContext = routingContext("shared.local.am", "/customers/login");
        index.dispatch(specificContext);
        verify(specific).handleContext(specificContext);
        verify(catchAll, never()).handleContext(specificContext);

        RoutingContext catchAllContext = routingContext("shared.local.am", "/other");
        index.dispatch(catchAllContext);
        verify(catchAll).handleContext(catchAllContext);
        verify(specific, never()).handleContext(catchAllContext);
    }

    @Test
    public void shouldNoLongerDispatchAfterUnmount() {
        Router delegate = mock(Router.class);
        mount(delegate, "domain1", "domain1.local.am", "/");

        index.unmount("domain1");

        RoutingContext context = routingContext("domain1.local.am", "/login");
        index.dispatch(context);

        verify(delegate, never()).handleContext(context);
        verify(context).next();
    }

    @Test
    public void unmountShouldNotAffectOtherDomains() {
        Router domain1Router = mock(Router.class);
        Router domain2Router = mock(Router.class);
        mount(domain1Router, "domain1", "domain1.local.am", "/");
        mount(domain2Router, "domain2", "domain2.local.am", "/");

        index.unmount("domain1");

        RoutingContext domain1Context = routingContext("domain1.local.am", "/login");
        index.dispatch(domain1Context);
        verify(domain1Router, never()).handleContext(domain1Context);

        RoutingContext domain2Context = routingContext("domain2.local.am", "/login");
        index.dispatch(domain2Context);
        verify(domain2Router).handleContext(domain2Context);
    }

    /**
     * Regression test for the bug this class fixes: mounting every vhost domain
     * as its own Vert.x route on a shared router made request routing recurse
     * one Java stack frame per previously-mounted domain (all vhost routes
     * shared the same wildcard path, so none could be skipped by Vert.x's own
     * path matching), causing a guaranteed StackOverflowError past ~3000
     * domains. Dispatch here is an O(1) hashmap lookup regardless of how many
     * domains are mounted, so this must succeed instantly and without error
     * even for a domain mounted deep into a large set - not just the first
     * few registered.
     */
    @Test
    public void shouldDispatchCorrectlyWithManyMountedDomainsWithoutStackOverflow() {
        int domainCount = 5000;
        Router[] routers = new Router[domainCount];
        for (int i = 0; i < domainCount; i++) {
            routers[i] = mock(Router.class);
            mount(routers[i], "domain" + i, "domain" + i + ".local.am", "/");
        }

        // last-registered domain: worst case for the old recursive/linear-scan approach
        RoutingContext lastContext = routingContext("domain" + (domainCount - 1) + ".local.am", "/oidc/.well-known/jwks.json");
        index.dispatch(lastContext);
        verify(routers[domainCount - 1]).handleContext(lastContext);

        // first-registered domain, and one in the middle, still resolve correctly too
        RoutingContext firstContext = routingContext("domain0.local.am", "/oidc/.well-known/jwks.json");
        index.dispatch(firstContext);
        verify(routers[0]).handleContext(firstContext);

        RoutingContext midContext = routingContext("domain2500.local.am", "/oidc/.well-known/jwks.json");
        index.dispatch(midContext);
        verify(routers[2500]).handleContext(midContext);
    }

    private void mount(Router delegate, String domainId, String host, String path) {
        Domain domain = new Domain();
        domain.setId(domainId);

        VirtualHost vhost = new VirtualHost();
        vhost.setHost(host);
        vhost.setPath(path);

        index.mount(domain, vhost, delegate);
    }

    private RoutingContext routingContext(String host, String path) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.authority()).thenReturn(HostAndPort.authority(host));
        when(request.path()).thenReturn(path);

        RoutingContext context = mock(RoutingContext.class);
        when(context.request()).thenReturn(request);

        return context;
    }
}

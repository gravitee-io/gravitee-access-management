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
import io.vertx.rxjava3.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VHostGroupRouterTest {

    private Vertx vertx;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
    }

    @After
    public void tearDown() {
        vertx.close();
    }

    @Test
    public void shouldDispatchToMatchingMemberAmongManySharingTheSamePath() {
        VHostGroupRouter group = VHostGroupRouter.create(vertx);

        // Simulate several thousand domains/vhosts sharing the exact same mount path,
        // differentiated only by their Host header (the scenario that used to blow the stack).
        int memberCount = 5000;
        Router targetDelegate = mock(Router.class);
        for (int i = 0; i < memberCount; i++) {
            io.vertx.rxjava3.ext.web.Router delegate = i == memberCount - 1
                    ? io.vertx.rxjava3.ext.web.Router.newInstance(targetDelegate)
                    : io.vertx.rxjava3.ext.web.Router.newInstance(mock(Router.class));
            group.addMember(vertx, new Domain(), virtualHost("host-" + i + ".gravitee.io", "/"), delegate);
        }

        RoutingContext routingContext = routingContext("host-" + (memberCount - 1) + ".gravitee.io", "/login");

        // Dispatching must not recurse through every non-matching member's context.next():
        // a StackOverflowError here indicates the fix regressed to O(n) stack depth.
        group.handleContext(routingContext);

        verify(targetDelegate).handleContext(routingContext);
        verify(routingContext, never()).next();
    }

    @Test
    public void shouldFallThroughToNextWhenNoMemberMatches() {
        VHostGroupRouter group = VHostGroupRouter.create(vertx);
        io.vertx.rxjava3.ext.web.Router delegate = io.vertx.rxjava3.ext.web.Router.newInstance(mock(Router.class));
        group.addMember(vertx, new Domain(), virtualHost("known.gravitee.io", "/"), delegate);

        RoutingContext routingContext = routingContext("unknown.gravitee.io", "/login");

        group.handleContext(routingContext);

        verify(routingContext).next();
    }

    @Test
    public void shouldStopMatchingRemovedMember() {
        VHostGroupRouter group = VHostGroupRouter.create(vertx);
        io.vertx.rxjava3.ext.web.Router delegate = io.vertx.rxjava3.ext.web.Router.newInstance(mock(Router.class));
        Object member = group.addMember(vertx, new Domain(), virtualHost("known.gravitee.io", "/"), delegate);

        assertFalse(group.isEmpty());
        group.removeMember(member);
        assertTrue(group.isEmpty());

        RoutingContext routingContext = routingContext("known.gravitee.io", "/login");
        group.handleContext(routingContext);

        verify(routingContext).next();
    }

    private VirtualHost virtualHost(String host, String path) {
        VirtualHost virtualHost = new VirtualHost();
        virtualHost.setHost(host);
        virtualHost.setPath(path);
        return virtualHost;
    }

    private RoutingContext routingContext(String host, String path) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.authority()).thenReturn(HostAndPort.authority(host));
        when(request.path()).thenReturn(path);

        RoutingContext routingContext = mock(RoutingContext.class);
        when(routingContext.request()).thenReturn(request);

        return routingContext;
    }
}

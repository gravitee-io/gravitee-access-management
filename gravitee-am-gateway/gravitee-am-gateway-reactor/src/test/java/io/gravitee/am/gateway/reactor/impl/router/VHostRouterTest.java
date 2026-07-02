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
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VHostRouterTest {

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
    public void shouldMatchVhostHostIgnoringCase() throws Exception {
        Router delegate = mock(Router.class);
        RoutingContext routingContext = routingContext("VALID.HOST.GRAVITEE.IO", "/test/login");
        VHostRouter router = router(delegate, "valid.host.gravitee.io", "/test");

        router.handleContext(routingContext);

        verify(delegate).handleContext(routingContext);
        verify(routingContext, never()).next();
    }

    private RoutingContext routingContext(String host, String path) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.authority()).thenReturn(HostAndPort.authority(host));
        when(request.path()).thenReturn(path);

        RoutingContext routingContext = mock(RoutingContext.class);
        when(routingContext.request()).thenReturn(request);

        return routingContext;
    }

    private VHostRouter router(Router delegate, String host, String path) throws Exception {
        VirtualHost virtualHost = new VirtualHost();
        virtualHost.setHost(host);
        virtualHost.setPath(path);

        Constructor<VHostRouter> constructor = VHostRouter.class.getDeclaredConstructor(Vertx.class, Domain.class, VirtualHost.class, Router.class);
        constructor.setAccessible(true);

        return constructor.newInstance(vertx, new Domain(), virtualHost, delegate);
    }
}

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
package io.gravitee.am.gateway.handler.root.resources.handler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class ConditionalBodyHandlerTest {

    @Mock
    private Handler<RoutingContext> handler;
    @Mock
    private HttpServerRequest httpServerRequest;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private Environment environment;

    @Test
    public void shouldCallBodyHandler_POST_method() {
        when(environment.getProperty("legacy.handler.alwaysApplyBodyHandler", Boolean.class, false)).thenReturn(false);
        when(httpServerRequest.method()).thenReturn(HttpMethod.POST);
        when(routingContext.request()).thenReturn(httpServerRequest);

        new ConditionalBodyHandler(environment, handler).handle(routingContext);

        verify(handler).handle(routingContext);
    }

    @Test
    public void shouldNotCallBodyHandler_GET_method() {
        when(environment.getProperty("legacy.handler.alwaysApplyBodyHandler", Boolean.class, false)).thenReturn(false);
        when(httpServerRequest.method()).thenReturn(HttpMethod.GET);
        when(routingContext.request()).thenReturn(httpServerRequest);

        new ConditionalBodyHandler(environment, handler).handle(routingContext);

        verify(handler, never()).handle(routingContext);
    }

    @Test
    public void shouldCallBodyHandler_GET_method_legacy() {
        when(environment.getProperty("legacy.handler.alwaysApplyBodyHandler", Boolean.class, false)).thenReturn(true);
        when(httpServerRequest.method()).thenReturn(HttpMethod.GET);
        when(routingContext.request()).thenReturn(httpServerRequest);

        new ConditionalBodyHandler(environment, handler).handle(routingContext);

        verify(handler).handle(routingContext);
    }
}

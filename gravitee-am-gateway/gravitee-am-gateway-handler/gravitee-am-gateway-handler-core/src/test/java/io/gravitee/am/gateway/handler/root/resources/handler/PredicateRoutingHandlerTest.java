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

import io.gravitee.am.gateway.handler.common.vertx.RxVertxTestBase;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.RoutingContext;
import jakarta.validation.constraints.AssertTrue;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

public class PredicateRoutingHandlerTest {

    @Test
    public void should_run_regular_handler_if_ctx_return_true() {
        var h1 = Mockito.mock(Handler.class);
        var h2 = Mockito.mock(Handler.class);
        PredicateRoutingHandler handler = new PredicateRoutingHandler(ctx -> true, h1, h2);
        handler.handle(Mockito.mock(RoutingContext.class));
        Mockito.verify(h1, Mockito.times(1)).handle(Mockito.any());
        Mockito.verify(h2, Mockito.times(0)).handle(Mockito.any());
    }

    @Test
    public void should_run_fallback_handler_if_ctx_return_false() {
        var h1 = Mockito.mock(Handler.class);
        var h2 = Mockito.mock(Handler.class);
        PredicateRoutingHandler handler = new PredicateRoutingHandler(ctx -> false, h1, h2);
        handler.handle(Mockito.mock(RoutingContext.class));
        Mockito.verify(h1, Mockito.times(0)).handle(Mockito.any());
        Mockito.verify(h2, Mockito.times(1)).handle(Mockito.any());
    }



}
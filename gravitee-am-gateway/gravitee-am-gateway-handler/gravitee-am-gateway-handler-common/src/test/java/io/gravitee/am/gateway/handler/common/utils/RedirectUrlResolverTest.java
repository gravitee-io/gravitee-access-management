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
package io.gravitee.am.gateway.handler.common.utils;

import io.gravitee.am.common.utils.ConstantKeys;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import org.junit.Test;
import org.mockito.Mockito;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.junit.jupiter.api.Assertions.*;

public class RedirectUrlResolverTest {

    @Test
    public void should_return_return_url_from_session_if_its_present(){
        RedirectUrlResolver redirectUrlResolver = new RedirectUrlResolver();
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        Session session = Mockito.mock(Session.class);
        Mockito.when(ctx.session()).thenReturn(session);
        Mockito.when(session.get(ConstantKeys.RETURN_URL_KEY)).thenReturn("https://www.gravitee.io");
        String path = redirectUrlResolver.resolveRedirectUrl(ctx, MultiMap.caseInsensitiveMultiMap());
        assertEquals("https://www.gravitee.io", path);
    }

    @Test
    public void should_return_return_url_from_request_if_its_present(){
        RedirectUrlResolver redirectUrlResolver = new RedirectUrlResolver();
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        Session session = Mockito.mock(Session.class);
        HttpServerRequest request = Mockito.mock(HttpServerRequest.class);

        Mockito.when(ctx.session()).thenReturn(session);
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam(ConstantKeys.RETURN_URL_KEY)).thenReturn("https://www.gravitee.io");
        String path = redirectUrlResolver.resolveRedirectUrl(ctx, MultiMap.caseInsensitiveMultiMap());
        assertEquals("https://www.gravitee.io", path);
    }

    @Test
    public void should_return_default_oauth_authrorize_endpoint(){
        RedirectUrlResolver redirectUrlResolver = new RedirectUrlResolver();
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        Session session = Mockito.mock(Session.class);
        HttpServerRequest request = Mockito.mock(HttpServerRequest.class);

        Mockito.when(ctx.session()).thenReturn(session);
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.scheme()).thenReturn("https");
        Mockito.when(request.host()).thenReturn("www.gravitee.io");
        Mockito.when(ctx.get(CONTEXT_PATH)).thenReturn("");
        String path = redirectUrlResolver.resolveRedirectUrl(ctx, MultiMap.caseInsensitiveMultiMap());
        assertEquals("https://www.gravitee.io/oauth/authorize", path);
    }

}
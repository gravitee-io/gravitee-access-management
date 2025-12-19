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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author GraviteeSource Team
 */
public class RememberedLoginRedirectToAuthorizeHandlerTest extends RxWebTestBase {

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.get(RootProvider.PATH_LOGIN + "/remembered/redirect")
                .handler(new RememberedLoginRedirectToAuthorizeHandler());
    }

    @Test
    public void shouldRedirectToAuthorizeWithOriginalQueryParams() throws Exception {
        testRequest(
                HttpMethod.GET,
                RootProvider.PATH_LOGIN + "/remembered/redirect?client_id=my-client&scope=openid",
                null,
                res -> {
                    String location = res.getHeader("Location");
                    assertTrue(location.contains("/oauth/authorize"));
                    assertTrue(location.contains("client_id=my-client"));
                    assertTrue(location.contains("scope=openid"));
                },
                302,
                "Found",
                null);
    }

    @Test
    public void shouldUseContextPathWhenPresent() {
        RememberedLoginRedirectToAuthorizeHandler handler = new RememberedLoginRedirectToAuthorizeHandler();

        RoutingContext ctx = Mockito.mock();
        io.vertx.rxjava3.core.http.HttpServerRequest request = Mockito.mock();
        io.vertx.rxjava3.core.http.HttpServerResponse response = Mockito.mock();

        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(ctx.response()).thenReturn(response);
        Mockito.when(ctx.get(UriBuilderRequest.CONTEXT_PATH)).thenReturn("/test-context");
        Mockito.when(response.putHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(response);
        Mockito.when(response.setStatusCode(Mockito.anyInt())).thenReturn(response);

        handler.handle(ctx);

        Mockito.verify(response).putHeader(Mockito.eq(io.gravitee.common.http.HttpHeaders.LOCATION), Mockito.contains("/test-context/oauth/authorize"));
        Mockito.verify(response).setStatusCode(302);
        Mockito.verify(response).end();
    }
}

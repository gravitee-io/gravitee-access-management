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
package io.gravitee.am.gateway.handler.vertx.handler.oidc.handler;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.reactivex.observers.TestObserver;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class AbstractProtectedHandlerTest {

    @InjectMocks
    private DynamicClientAccessHandler handler = new DynamicClientAccessHandler(null,null,null);

    @Test
    public void extractAccessToken_noBearer() {
        HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
        RoutingContext context = Mockito.mock(RoutingContext.class);

        when(context.request()).thenReturn(request);
        when(request.getHeader(eq("Authorization"))).thenReturn(null);
        when(request.getParam(any())).thenReturn(null);

        TestObserver testObserver = handler.extractAccessTokenFromRequest(context).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestException.class);
    }

    @Test
    public void extractAccessToken_badHeaderFormat() {
        HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
        RoutingContext context = Mockito.mock(RoutingContext.class);

        when(context.request()).thenReturn(request);
        when(request.getHeader(eq("Authorization"))).thenReturn("NotBearer eyxxx");

        TestObserver testObserver = handler.extractAccessTokenFromRequest(context).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestException.class);
    }

    @Test
    public void extractAccessToken_ok() {
        HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
        RoutingContext context = Mockito.mock(RoutingContext.class);

        when(context.request()).thenReturn(request);
        when(request.getHeader(eq("Authorization"))).thenReturn("Bearer eyxxx");

        TestObserver testObserver = handler.extractAccessTokenFromRequest(context).test();
        testObserver.assertComplete();
        testObserver.assertValue(o -> "eyxxx".equals(o));
    }

    @Test
    public void extractAccessToken_queryParam() {
        HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
        RoutingContext context = Mockito.mock(RoutingContext.class);

        when(context.request()).thenReturn(request);
        when(request.getParam("access_token")).thenReturn("tokenValue");

        TestObserver testObserver = handler.extractAccessTokenFromRequest(context).test();
        testObserver.assertComplete();
        testObserver.assertValue(o -> "tokenValue".equals(o));
    }
}

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
package io.gravitee.am.gateway.handler.vertx.endpoint;

import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.auth.user.Client;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.vertx.oauth2.endpoint.introspection.IntrospectionEndpointHandler;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.auth.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
// TODO : test valid use cases
public class IntrospectionEndpointHandlerTest extends RxWebTestBase {

    @InjectMocks
    private IntrospectionEndpointHandler introspectionEndpointHandler = new IntrospectionEndpointHandler();

    @Mock
    private IntrospectionService introspectionService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.POST, "/oauth/introspect")
                .handler(introspectionEndpointHandler);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldNotInvokeEndpoint_noClient() throws Exception {
        testRequest(
                HttpMethod.POST, "/oauth/introspect",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldReturnInvalidToken_noTokenProvided() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new Client("client-id")));
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                "/oauth/introspect",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

}

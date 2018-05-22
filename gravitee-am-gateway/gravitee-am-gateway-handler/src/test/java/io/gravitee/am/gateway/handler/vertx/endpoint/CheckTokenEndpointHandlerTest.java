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

import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.auth.user.Client;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.vertx.oauth2.endpoint.introspection.CheckTokenEndpointHandler;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.auth.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
// TODO : test valid use cases
public class CheckTokenEndpointHandlerTest extends RxWebTestBase {

    @InjectMocks
    private CheckTokenEndpointHandler checkTokenEndpointHandler = new CheckTokenEndpointHandler();

    @Mock
    private TokenService tokenService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.POST, "/oauth/check_token")
                .handler(checkTokenEndpointHandler);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldNotInvokeEndpoint_noClient() throws Exception {
        testRequest(
                HttpMethod.POST, "/oauth/check_token",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldReturnInvalidToken_noTokenProvided() throws Exception {
        io.gravitee.am.model.Client client = new io.gravitee.am.model.Client();
        client.setClientId("my-client-id");

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new Client(client)));
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                "/oauth/check_token",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }
}

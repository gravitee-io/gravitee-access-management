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

import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.vertx.handler.login.endpoint.LoginEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.login.endpoint.LoginRequestParseHandler;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginEndpointHandlerTest extends RxWebTestBase {

    @InjectMocks
    private LoginEndpointHandler loginEndpointHandler = new LoginEndpointHandler();

    @Mock
    private ClientSyncService clientSyncService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/login")
                .handler(new LoginRequestParseHandler(clientSyncService))
                .handler(loginEndpointHandler);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldNotInvokeLoginEndpoint_noClientParameter() throws Exception {
        testRequest(
                HttpMethod.GET, "/login",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldNotInvokeLoginEndpoint_noClient() throws Exception {
        when(clientSyncService.findByClientId(anyString())).thenReturn(Maybe.empty());

        testRequest(
                HttpMethod.GET, "/login?client_id=test",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

}

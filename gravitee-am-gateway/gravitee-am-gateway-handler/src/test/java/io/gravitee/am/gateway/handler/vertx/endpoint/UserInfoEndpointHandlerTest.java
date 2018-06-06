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

import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.token.impl.DefaultAccessToken;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oidc.endpoint.UserInfoEndpoint;
import io.gravitee.am.gateway.handler.vertx.handler.oidc.handler.UserInfoRequestParseHandler;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserInfoEndpointHandlerTest extends RxWebTestBase {

    @InjectMocks
    private UserInfoEndpoint userInfoEndpoint = new UserInfoEndpoint();

    @InjectMocks
    private UserInfoRequestParseHandler userInfoRequestParseHandler = new UserInfoRequestParseHandler();

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/userinfo")
                .handler(userInfoRequestParseHandler)
                .handler(userInfoEndpoint);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldNotInvokeUserEndpoint_noBearerToken() throws Exception {
        testRequest(
                HttpMethod.GET, "/userinfo",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldNotInvokeUserEndpoint_invalidHeaderBearerToken() throws Exception {
        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Error token"),
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_invalidToken() throws Exception {
        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.empty());

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_expiredToken() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setExpiresIn(0);

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_clientOnlyToken() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_userNotFound() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));
        when(userService.findById(anyString())).thenReturn(Maybe.empty());

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldInvokeUserEndpoint() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setSubject("user");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);

        User user = new User();
        user.setAdditionalInformation(Collections.singletonMap("sub", "user"));

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.OK_200, "OK", null);
    }
}

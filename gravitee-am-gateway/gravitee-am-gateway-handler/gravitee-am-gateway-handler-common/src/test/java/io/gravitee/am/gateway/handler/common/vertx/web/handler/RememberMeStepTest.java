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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.RememberMeStep;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.http.Cookie;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.auth.User;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.DEFAULT_REMEMBER_ME_COOKIE_NAME;
import static io.gravitee.am.common.utils.ConstantKeys.USER_ID_KEY;
import static org.mockito.Mockito.*;

/**
 * @author Aurélien PACAUD (aurelien.pacaud at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RememberMeStepTest {

    private static final Handler<RoutingContext> redirectHandler = RedirectHandler.create("/login");

    @Mock
    private JWTService jwtService;

    @Mock
    private UserService gatewayUserService;

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest httpServerRequest;

    @Mock
    private HttpServerResponse httpServerResponse;

    private AuthenticationFlowChain authenticationFlowChain;

    private RememberMeStep step;

    @Before
    public void setUp() {

        step = new RememberMeStep(redirectHandler, jwtService, gatewayUserService, DEFAULT_REMEMBER_ME_COOKIE_NAME);
        authenticationFlowChain = spy(new AuthenticationFlowChain(List.of(step)));

        when(routingContext.request()).thenReturn(httpServerRequest);
        when(routingContext.response()).thenReturn(httpServerResponse);
        doNothing().when(authenticationFlowChain).exit(Mockito.any());
        doNothing().when(authenticationFlowChain).doNext(Mockito.any());
    }

    @Test
    public void mustDoNext_userAlreadyAuthenticated() {
        when(routingContext.user()).thenReturn(User.create(new JsonObject()));

        step.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(step);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustDoNext_userNotAuthenticatedAndRememberMeCookieNotFound() {
        when(routingContext.user()).thenReturn(null);

        step.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(step);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustRemoveCookieAndDoNext_failedToDecodeRememberMeCookie() {
        when(routingContext.user()).thenReturn(null);

        when(httpServerRequest.getCookie(DEFAULT_REMEMBER_ME_COOKIE_NAME)).thenReturn(Cookie.cookie(DEFAULT_REMEMBER_ME_COOKIE_NAME, "value"));
        when(jwtService.decode(anyString(), any(JWTService.TokenType.class))).thenReturn(Single.error(new IllegalArgumentException("Error during decoding")));

        step.execute(routingContext, authenticationFlowChain);

        verify(httpServerResponse, times(1)).removeCookie(DEFAULT_REMEMBER_ME_COOKIE_NAME);
        verify(authenticationFlowChain, times(0)).exit(step);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustRemoveCookieAndDoNext_userNotFound() {
        when(routingContext.user()).thenReturn(null);

        when(httpServerRequest.getCookie(DEFAULT_REMEMBER_ME_COOKIE_NAME)).thenReturn(Cookie.cookie(DEFAULT_REMEMBER_ME_COOKIE_NAME, "value"));

        final var jwt = new JWT();
        jwt.put(USER_ID_KEY, "12345");
        when(jwtService.decode(anyString(), any(JWTService.TokenType.class))).thenReturn(Single.just(jwt));

        when(gatewayUserService.findById("12345")).thenReturn(Maybe.error(new IllegalStateException("User not found")));

        step.execute(routingContext, authenticationFlowChain);

        verify(httpServerResponse, times(1)).removeCookie(DEFAULT_REMEMBER_ME_COOKIE_NAME);
        verify(authenticationFlowChain, times(0)).exit(step);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustExit_cookieCorrectlyDecodedAndRead() {

        when(routingContext.user()).thenReturn(null);

        when(httpServerRequest.getCookie(DEFAULT_REMEMBER_ME_COOKIE_NAME)).thenReturn(Cookie.cookie(DEFAULT_REMEMBER_ME_COOKIE_NAME, "value"));

        final var jwt = new JWT();
        jwt.put(USER_ID_KEY, "12345");
        when(jwtService.decode(anyString(), any(JWTService.TokenType.class))).thenReturn(Single.just(jwt));

        when(gatewayUserService.findById("12345")).thenReturn(Maybe.just(new io.gravitee.am.model.User()));

        step.execute(routingContext, authenticationFlowChain);

        verify(routingContext, times(1)).setUser(any(io.vertx.rxjava3.ext.auth.User.class));
        verify(authenticationFlowChain, times(1)).exit(step);
        verify(authenticationFlowChain, times(0)).doNext(routingContext);
    }
}

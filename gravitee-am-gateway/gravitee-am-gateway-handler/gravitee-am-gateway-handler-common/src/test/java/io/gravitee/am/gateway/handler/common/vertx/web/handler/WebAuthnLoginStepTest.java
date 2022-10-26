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

import io.gravitee.am.common.exception.jwt.SignatureException;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.WebAuthnLoginStep;
import io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Completable;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.Cookie;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnLoginStepTest {

    private static final Handler<RoutingContext> redirectHandler = RedirectHandler.create("/webauthn/login");

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest httpServerRequest;

    @Mock
    private WebAuthnCookieService webAuthnCookieService;

    @Mock
    private Domain domain;

    private AuthenticationFlowChain authenticationFlowChain;

    private WebAuthnLoginStep webAuthnLoginStep;

    @Before
    public void setUp() {
        webAuthnLoginStep = new WebAuthnLoginStep(redirectHandler, domain, webAuthnCookieService);
        authenticationFlowChain = spy(new AuthenticationFlowChain(List.of(webAuthnLoginStep)));

        when(webAuthnCookieService.getRememberDeviceCookieName()).thenReturn("cookieName");
        when(routingContext.request()).thenReturn(httpServerRequest);
        doNothing().when(authenticationFlowChain).exit(Mockito.any());
        doNothing().when(authenticationFlowChain).doNext(Mockito.any());
    }

    @Test
    public void mustDoNext_userAlreadyAuthenticated() {
        when(routingContext.user()).thenReturn(User.create(new JsonObject()));

        webAuthnLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(webAuthnLoginStep);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustDoNext_optionDisabled() {
        LoginSettings loginSettings = mock(LoginSettings.class);
        when(loginSettings.isPasswordlessEnabled()).thenReturn(false);
        Client client = mock(Client.class);
        when(client.getLoginSettings()).thenReturn(loginSettings);
        when(routingContext.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        webAuthnLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(webAuthnLoginStep);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustDoNext_optionDisabled2() {
        LoginSettings loginSettings = mock(LoginSettings.class);
        when(loginSettings.isPasswordlessEnabled()).thenReturn(true);
        when(loginSettings.isPasswordlessRememberDeviceEnabled()).thenReturn(false);
        Client client = mock(Client.class);
        when(client.getLoginSettings()).thenReturn(loginSettings);
        when(routingContext.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        webAuthnLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(webAuthnLoginStep);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustDoNext_optionDisabled3() {
        Client client = mock(Client.class);
        when(client.getLoginSettings()).thenReturn(null);
        when(routingContext.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        webAuthnLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(webAuthnLoginStep);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustDoNext_noCookie() {
        LoginSettings loginSettings = mock(LoginSettings.class);
        when(loginSettings.isPasswordlessEnabled()).thenReturn(true);
        when(loginSettings.isPasswordlessRememberDeviceEnabled()).thenReturn(true);
        Client client = mock(Client.class);
        when(client.getLoginSettings()).thenReturn(loginSettings);
        when(routingContext.get(CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(httpServerRequest.getCookie("cookieName")).thenReturn(null);

        webAuthnLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(webAuthnLoginStep);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustDoNext_failedToDecodeCookie() {
        LoginSettings loginSettings = mock(LoginSettings.class);
        when(loginSettings.isPasswordlessEnabled()).thenReturn(true);
        when(loginSettings.isPasswordlessRememberDeviceEnabled()).thenReturn(true);
        Client client = mock(Client.class);
        when(client.getLoginSettings()).thenReturn(loginSettings);
        when(routingContext.get(CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(httpServerRequest.getCookie("cookieName")).thenReturn(Cookie.cookie("cookieName", "cookieValue"));
        when(webAuthnCookieService.verifyRememberDeviceCookieValue("cookieValue")).thenReturn(Completable.error(new SignatureException("Invalid payload")));

        webAuthnLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(webAuthnLoginStep);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustExit_nominalCase() {
        LoginSettings loginSettings = mock(LoginSettings.class);
        when(loginSettings.isPasswordlessEnabled()).thenReturn(true);
        when(loginSettings.isPasswordlessRememberDeviceEnabled()).thenReturn(true);
        Client client = mock(Client.class);
        when(client.getLoginSettings()).thenReturn(loginSettings);
        when(routingContext.get(CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(httpServerRequest.getCookie("cookieName")).thenReturn(Cookie.cookie("cookieName", "cookieValue"));
        when(webAuthnCookieService.verifyRememberDeviceCookieValue("cookieValue")).thenReturn(Completable.complete());

        webAuthnLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(1)).exit(webAuthnLoginStep);
        verify(authenticationFlowChain, times(0)).doNext(routingContext);
    }
}

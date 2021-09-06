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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.FormIdentifierFirstLoginStep;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
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

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.USERNAME_PARAM_KEY;
import static org.mockito.Mockito.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class FormIdentifierFirstLoginStepTest {

    private static final Handler<RoutingContext> redirectHandler = RedirectHandler.create("/login/identifier");

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest httpServerRequest;

    private AuthenticationFlowChain authenticationFlowChain;

    private FormIdentifierFirstLoginStep formIdentifierFirstLoginStep;
    private Client client;
    private Domain domain;

    @Before
    public void setUp() {
        domain = new Domain();
        domain.setLoginSettings(new LoginSettings());
        formIdentifierFirstLoginStep = new FormIdentifierFirstLoginStep(redirectHandler, domain);
        authenticationFlowChain = spy(new AuthenticationFlowChain(List.of(formIdentifierFirstLoginStep)));
        client = new Client();
        client.setLoginSettings(new LoginSettings());

        when(routingContext.get(CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.request()).thenReturn(httpServerRequest);
        doNothing().when(authenticationFlowChain).exit(Mockito.any());
        doNothing().when(authenticationFlowChain).doNext(Mockito.any());
    }

    @Test
    public void mustExitFlow_identifierFirstLoginEnabledAndEmptyUsernameAndEmptyUser() {
        client.getLoginSettings().setInherited(false);
        client.getLoginSettings().setIdentifierFirstEnabled(true);

        when(routingContext.request()).thenReturn(httpServerRequest);
        when(routingContext.user()).thenReturn(null);
        when(httpServerRequest.getParam(USERNAME_PARAM_KEY)).thenReturn(null);

        formIdentifierFirstLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(1)).exit(formIdentifierFirstLoginStep);
        verify(authenticationFlowChain, times(0)).doNext(routingContext);
    }

    @Test
    public void mustExitFlow_identifierFirstLoginEnabledAndEmptyUsernameAndEmptyUserWithDomain() {
        client.getLoginSettings().setInherited(true);
        domain.getLoginSettings().setIdentifierFirstEnabled(true);

        when(routingContext.request()).thenReturn(httpServerRequest);
        when(routingContext.user()).thenReturn(null);
        when(httpServerRequest.getParam(USERNAME_PARAM_KEY)).thenReturn(null);

        formIdentifierFirstLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(1)).exit(formIdentifierFirstLoginStep);
        verify(authenticationFlowChain, times(0)).doNext(routingContext);
    }

    @Test
    public void mustNotExitFlow_identifierFirstLoginDisabledAtDomain() {
        client.getLoginSettings().setInherited(true);
        domain.getLoginSettings().setIdentifierFirstEnabled(false);

        when(routingContext.request()).thenReturn(httpServerRequest);
        when(routingContext.user()).thenReturn(null);
        when(httpServerRequest.getParam(USERNAME_PARAM_KEY)).thenReturn(null);

        formIdentifierFirstLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(formIdentifierFirstLoginStep);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustDoNext_identifierFirstLoginDisabled() {
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(routingContext.user()).thenReturn(User.create(new JsonObject()));
        when(httpServerRequest.getParam(USERNAME_PARAM_KEY)).thenReturn("a username");

        formIdentifierFirstLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(formIdentifierFirstLoginStep);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustDoNext_userNotEmpty() {
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(routingContext.user()).thenReturn(User.create(new JsonObject()));
        when(httpServerRequest.getParam(USERNAME_PARAM_KEY)).thenReturn(null);

        formIdentifierFirstLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(formIdentifierFirstLoginStep);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustDoNext_usernameNotEmpty() {
        client.getLoginSettings().setIdentifierFirstEnabled(true);

        when(routingContext.request()).thenReturn(httpServerRequest);
        when(routingContext.user()).thenReturn(null);
        when(httpServerRequest.getParam(USERNAME_PARAM_KEY)).thenReturn("a username");

        formIdentifierFirstLoginStep.execute(routingContext, authenticationFlowChain);

        verify(authenticationFlowChain, times(0)).exit(formIdentifierFirstLoginStep);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }
}

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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.ForceResetPasswordStep;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ForceResetPasswordStepTest {

    private static final Handler<RoutingContext> redirectHandler = RedirectHandler.create("/login");

    private ForceResetPasswordStep step;

    @Mock
    private JWTService jwtService;

    @Mock
    private CertificateManager certificateManager;

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest httpServerRequest;

    private AuthenticationFlowChain authenticationFlowChain;


    @Before
    public void setUp() {
        step = new ForceResetPasswordStep(redirectHandler, jwtService, certificateManager);
        authenticationFlowChain = spy(new AuthenticationFlowChain(List.of(step)));

        when(routingContext.request()).thenReturn(httpServerRequest);
        doNothing().when(authenticationFlowChain).exit(any());
        doNothing().when(authenticationFlowChain).doNext(any());
    }

    @Test
    public void mustDoNext_onForceResetPassword_false() {
        mockAuthUser(false);
        step.execute(routingContext, authenticationFlowChain);
        verify(authenticationFlowChain, times(0)).exit(step);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }

    @Test
    public void mustExit_onForceResetPassword_true() {
        Client client = new Client();
        client.setId("client");
        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(jwtService.encode(any(), (CertificateProvider) any())).thenReturn(Single.just("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"));

        mockAuthUser(true);
        step.execute(routingContext, authenticationFlowChain);
        verify(authenticationFlowChain, times(1)).exit(step);
        verify(authenticationFlowChain, times(0)).doNext(routingContext);
    }

    @Test
    public void mustDoNext_missing_user() {
        when(routingContext.user()).thenReturn(null);
        step.execute(routingContext, authenticationFlowChain);
        verify(authenticationFlowChain, times(0)).exit(step);
        verify(authenticationFlowChain, times(1)).doNext(routingContext);
    }


    private void mockAuthUser(Boolean forceResetPassword) {
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        when(routingContext.user()).thenReturn(authenticatedUser);

        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User delegateUser = mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
        when(authenticatedUser.getDelegate()).thenReturn(delegateUser);

        io.gravitee.am.model.User mockUser = new User();
        mockUser.setForceResetPassword(forceResetPassword);
        when(delegateUser.getUser()).thenReturn(mockUser);
    }

}

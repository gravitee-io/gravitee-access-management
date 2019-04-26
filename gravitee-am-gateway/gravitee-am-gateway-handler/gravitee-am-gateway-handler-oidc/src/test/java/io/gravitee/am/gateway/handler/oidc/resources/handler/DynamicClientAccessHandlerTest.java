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
package io.gravitee.am.gateway.handler.oidc.resources.handler;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.Scope;
/*import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oidc.exception.ClientRegistrationForbiddenException;
import io.gravitee.am.gateway.handler.vertx.handler.oidc.handler.DynamicClientAccessHandler;*/
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;

import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
//@RunWith(MockitoJUnitRunner.class)
public class DynamicClientAccessHandlerTest {

   /* @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private JWTService jwtService;

    @Mock
    private Domain domain;

    @InjectMocks
    DynamicClientAccessHandler handler = new DynamicClientAccessHandler(clientSyncService, jwtService, domain);

    @Mock
    private RoutingContext context;

    @Mock
    private HttpServerRequest request;

    @Mock
    private Client client;

    private static final String CLIENT_ID="my-test-client-id";

    @Before
    public void setUp() {
        when(domain.isDynamicClientRegistrationEnabled()).thenReturn(true);
        when(context.request()).thenReturn(request);
        when(request.getHeader("Authorization")).thenReturn("Bearer "+"my-test-token");
        when(request.getParam("client_id")).thenReturn(CLIENT_ID);
        when(client.getClientId()).thenReturn(CLIENT_ID);
        when(client.getRegistrationAccessToken()).thenReturn("my-test-token");
    }

    @Test
    public void register_withOidcDcrDisabled() {
        when(domain.isDynamicClientRegistrationEnabled()).thenReturn(false);

        handler.handle(context);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(context, times(1)).fail(exceptionCaptor.capture());
        Assert.assertTrue("Should return a DCR disabled exception", exceptionCaptor.getValue() instanceof ClientRegistrationForbiddenException);
    }

    @Test
    public void register_withOidcDcrEnabled_authenticatedButNotSameClientId() {
        JWT jwt = Mockito.mock(JWT.class);
        when(jwt.getSub()).thenReturn("notSameClientId");

        when(jwtService.decode(any())).thenReturn(Single.just(new JWT()));
        when(clientSyncService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(any(),any())).thenReturn(Single.just(jwt));

        handler.handle(context);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(context, times(1)).fail(exceptionCaptor.capture());
        Assert.assertTrue("Should return a DCR disabled exception", exceptionCaptor.getValue() instanceof InvalidTokenException);
    }

    @Test
    public void register_withOidcDcrEnabled_authenticatedButTokenExpired() {
        JWT jwt = Mockito.mock(JWT.class);
        when(jwt.getSub()).thenReturn(CLIENT_ID);

        when(jwtService.decode(any())).thenReturn(Single.just(new JWT()));
        when(clientSyncService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(any(),any())).thenReturn(Single.just(jwt));

        handler.handle(context);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(context, times(1)).fail(exceptionCaptor.capture());
        Assert.assertTrue("Should return a DCR disabled exception", exceptionCaptor.getValue() instanceof InvalidTokenException);
    }

    @Test
    public void register_withOidcDcrEnabled_authenticatedButNoRequiredScope() {
        JWT jwt = Mockito.mock(JWT.class);
        when(jwt.getSub()).thenReturn(CLIENT_ID);
        when(jwt.getExp()).thenReturn(Date.from(new Date().toInstant().plusSeconds(3600)).getTime()/1000l);

        when(jwtService.decode(any())).thenReturn(Single.just(new JWT()));
        when(clientSyncService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(any(),any())).thenReturn(Single.just(jwt));

        handler.handle(context);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(context, times(1)).fail(exceptionCaptor.capture());
        Assert.assertTrue("Should return a DCR disabled exception", exceptionCaptor.getValue() instanceof ClientRegistrationForbiddenException);
    }

    @Test
    public void register_withOidcDcrEnabled_wrongRegistrationAccessToken() {
        JWT jwt = Mockito.mock(JWT.class);
        when(jwt.getSub()).thenReturn(CLIENT_ID);
        when(jwt.getExp()).thenReturn(Date.from(new Date().toInstant().plusSeconds(3600)).getTime()/1000l);
        when(jwt.getScope()).thenReturn(Scope.DCR.getKey());

        when(client.getRegistrationAccessToken()).thenReturn("wrongAccessToken");
        when(jwtService.decode(any())).thenReturn(Single.just(new JWT()));
        when(clientSyncService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(any(),any())).thenReturn(Single.just(jwt));

        handler.handle(context);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(context, times(1)).fail(exceptionCaptor.capture());
        Assert.assertTrue("Should return a DCR disabled exception", exceptionCaptor.getValue() instanceof ClientRegistrationForbiddenException);
    }

    @Test
    public void register_withOidcDcrEnabled_wrongPathClientId() {
        JWT jwt = Mockito.mock(JWT.class);
        when(jwt.getSub()).thenReturn(CLIENT_ID);
        when(jwt.getExp()).thenReturn(Date.from(new Date().toInstant().plusSeconds(3600)).getTime()/1000l);
        when(jwt.getScope()).thenReturn(Scope.DCR.getKey());

        when(request.getParam("client_id")).thenReturn("wrongPathClientIdParameter");
        when(jwtService.decode(any())).thenReturn(Single.just(new JWT()));
        when(clientSyncService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(any(),any())).thenReturn(Single.just(jwt));

        handler.handle(context);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(context, times(1)).fail(exceptionCaptor.capture());
        Assert.assertTrue("Should return a DCR disabled exception", exceptionCaptor.getValue() instanceof ClientRegistrationForbiddenException);
    }

    @Test
    public void register_withOidcDcrEnabled_isAdmin() {
        JWT jwt = Mockito.mock(JWT.class);
        when(jwt.getSub()).thenReturn(CLIENT_ID);
        when(jwt.getExp()).thenReturn(Date.from(new Date().toInstant().plusSeconds(3600)).getTime()/1000l);
        when(jwt.getScope()).thenReturn(Scope.DCR_ADMIN.getKey());

        when(jwtService.decode(any())).thenReturn(Single.just(new JWT()));
        when(clientSyncService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(any(),any())).thenReturn(Single.just(jwt));

        handler.handle(context);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(context, times(2)).put(keyCaptor.capture(), any());
        Assert.assertTrue("Should put the domain in context", keyCaptor.getValue().equals("domain"));
        verify(context, times(1)).next();
    }*/
}

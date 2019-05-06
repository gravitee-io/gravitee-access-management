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

import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.oidc.exception.ClientRegistrationForbiddenException;
import io.gravitee.am.model.Domain;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicClientRegistrationHandlerTest {

    @Mock
    private Domain domain;

    @Mock
    private OAuth2AuthHandler oAuth2AuthHandler;

    @InjectMocks
    private DynamicClientRegistrationHandler handler = new DynamicClientRegistrationHandler(domain, oAuth2AuthHandler);

    @Mock
    private RoutingContext context;

    @Test
    public void register_withNullOidcSettings() {
        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        doNothing().when(context).fail(exceptionCaptor.capture());

        handler.handle(context);

        Assert.assertTrue("Should return a DCR disabled exception", exceptionCaptor.getValue() instanceof ClientRegistrationForbiddenException);
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
    public void register_withOidcDcrEnabled_openDcrEnabled() {
        when(domain.isOpenDynamicClientRegistrationEnabled()).thenReturn(true);

        handler.handle(context);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(context, times(1)).next();
    }

    @Test
    public void register_withOidcDcrEnabled() {
        when(domain.isDynamicClientRegistrationEnabled()).thenReturn(true);

        handler.handle(context);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(oAuth2AuthHandler, times(1)).handle(context);
    }

    /*
    @Test
    public void register_withOidcDcrEnabled_notAuthenticated() {
        when(domain.isDynamicClientRegistrationEnabled()).thenReturn(true);

        HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
        when(context.request()).thenReturn(request);
        when(request.getHeader("Authorization")).thenReturn("booom");

        handler.handle(context);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(context, times(1)).fail(exceptionCaptor.capture());
        Assert.assertTrue("Should return a DCR disabled exception", exceptionCaptor.getValue() instanceof InvalidRequestException);
    }

    @Test
    public void register_withOidcDcrEnabled_authenticatedButNotSameClientId() {
        when(domain.isDynamicClientRegistrationEnabled()).thenReturn(true);

        Client client = new Client();
        client.setClientId("client_id");
        AccessToken token = Mockito.mock(AccessToken.class);
        when(token.getSubject()).thenReturn("notSameClientId");

        when(jwtService.decode(any())).thenReturn(Single.just(new JWT()));
        when(tokenService.getAccessToken(any(),any())).thenReturn(Maybe.just(token));
        when(clientSyncService.findByClientId(any())).thenReturn(Maybe.just(client));

        handler.handle(context);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(context, times(1)).fail(exceptionCaptor.capture());
        Assert.assertTrue("Should return a DCR disabled exception", exceptionCaptor.getValue() instanceof InvalidTokenException);
    }


    @Test
    public void register_withOidcDcrEnabled_authenticatedButTokenExpired() {
        when(domain.isDynamicClientRegistrationEnabled()).thenReturn(true);

        Client client = new Client();
        client.setClientId("client_id");
        AccessToken token = Mockito.mock(AccessToken.class);
        when(token.getSubject()).thenReturn(client.getClientId());
        when(token.getExpireAt()).thenReturn(Date.from(new Date().toInstant().minusSeconds(3600)));

        when(jwtService.decode(any())).thenReturn(Single.just(new JWT()));
        when(tokenService.getAccessToken(any(),any())).thenReturn(Maybe.just(token));
        when(clientSyncService.findByClientId(any())).thenReturn(Maybe.just(client));

        handler.handle(context);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(context, times(1)).fail(exceptionCaptor.capture());
        Assert.assertTrue("Should return a DCR disabled exception", exceptionCaptor.getValue() instanceof InvalidTokenException);
    }

    @Test
    public void register_withOidcDcrEnabled_authenticatedButNoRequiredScope() {
        when(domain.isDynamicClientRegistrationEnabled()).thenReturn(true);

        Client client = new Client();
        client.setClientId("client_id");
        AccessToken token = Mockito.mock(AccessToken.class);
        when(token.getSubject()).thenReturn(client.getClientId());
        when(token.getExpireAt()).thenReturn(Date.from(new Date().toInstant().plusSeconds(3600)));

        when(jwtService.decode(any())).thenReturn(Single.just(new JWT()));
        when(tokenService.getAccessToken(any(),any())).thenReturn(Maybe.just(token));
        when(clientSyncService.findByClientId(any())).thenReturn(Maybe.just(client));

        handler.handle(context);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(context, times(1)).fail(exceptionCaptor.capture());
        System.out.println( exceptionCaptor.getValue());
        Assert.assertTrue("Should return a DCR disabled exception", exceptionCaptor.getValue() instanceof ClientRegistrationForbiddenException);
    }

    @Test
    public void register_withOidcDcrEnabled_authenticatedWithExpectedScope() {
        when(domain.isDynamicClientRegistrationEnabled()).thenReturn(true);

        Client client = new Client();
        client.setClientId("client_id");
        AccessToken token = Mockito.mock(AccessToken.class);
        when(token.getSubject()).thenReturn(client.getClientId());
        when(token.getExpireAt()).thenReturn(Date.from(new Date().toInstant().plusSeconds(3600)));
        when(token.getScope()).thenReturn(Scope.DCR_ADMIN.getKey());

        when(jwtService.decode(any())).thenReturn(Single.just(new JWT()));
        when(tokenService.getAccessToken(any(),any())).thenReturn(Maybe.just(token));
        when(clientSyncService.findByClientId(any())).thenReturn(Maybe.just(client));

        handler.handle(context);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(context, times(2)).put(keyCaptor.capture(), any());
        Assert.assertTrue("Should put the domain in context", keyCaptor.getValue().equals("domain"));
        verify(context, times(1)).next();
    }
    */
}

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
package io.gravitee.am.gateway.handler.root.resources.auth.provider;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.gateway.handler.common.auth.event.AuthenticationEvent;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.User;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class SocialAuthenticationProviderTest {

    @InjectMocks
    private SocialAuthenticationProvider authProvider = new SocialAuthenticationProvider();

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private AuthenticationProvider authenticationProvider;

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest httpServerRequest;

    @Mock
    private EventManager eventManager;

    @Test
    public void shouldAuthenticateUser() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.User user  = new io.gravitee.am.identityprovider.api.DefaultUser("username");

        Client client = new Client();

        when(userAuthenticationManager.connect(any())).thenReturn(Single.just(new User()));

        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("provider")).thenReturn(authenticationProvider);
        when(routingContext.request()).thenReturn(httpServerRequest);
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertNotNull(userAsyncResult.result());
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        verify(userAuthenticationManager, times(1)).connect(any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.SUCCESS), any());
    }


    @Test
    public void shouldNotAuthenticateUser_badCredentials() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");

        Client client = new Client();

        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.error(BadCredentialsException::new));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("provider")).thenReturn(authenticationProvider);
        when(routingContext.request()).thenReturn(httpServerRequest);
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertTrue(userAsyncResult.failed());
            Assert.assertTrue(userAsyncResult.cause() instanceof BadCredentialsException);
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        verify(userAuthenticationManager, never()).connect(any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldNotAuthenticateUser_noUser() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");

        Client client = new Client();

        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.empty());
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("provider")).thenReturn(authenticationProvider);
        when(routingContext.request()).thenReturn(httpServerRequest);
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertTrue(userAsyncResult.failed());
            Assert.assertTrue(userAsyncResult.cause() instanceof BadCredentialsException);
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        verify(userAuthenticationManager, never()).connect(any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.FAILURE), any());
    }


}

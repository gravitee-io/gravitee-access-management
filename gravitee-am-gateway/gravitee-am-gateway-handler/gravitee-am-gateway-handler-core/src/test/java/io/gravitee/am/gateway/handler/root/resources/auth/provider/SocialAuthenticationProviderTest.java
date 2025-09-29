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
import io.gravitee.am.common.exception.authentication.LoginCallbackFailedException;
import io.gravitee.am.common.exception.authentication.UserAuthenticationAbortedException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.auth.event.AuthenticationEvent;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.social.CloseSessionMode;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.Getter;
import lombok.Setter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ID_TOKEN_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class SocialAuthenticationProviderTest {

    @InjectMocks
    private SocialAuthenticationProvider authProvider;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider authenticationProvider;

    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest httpServerRequest;

    @Mock
    private EventManager eventManager;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private GatewayMetricProvider gatewayMetricProvider;

    @Before
    public void setUp() {
        routingContext = mock();
        when(routingContext.queryParams()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(routingContext.get("provider")).thenReturn(authenticationProvider);

    }

    @Test
    public void shouldAuthenticateUser() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.User user  = new io.gravitee.am.identityprovider.api.DefaultUser("username");

        Client client = new Client();

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.getDomainWhitelist()).thenReturn(null);

        when(userAuthenticationManager.connect(any(), any(), any())).thenReturn(Single.just(new User()));
        when(identityProviderManager.getIdentityProvider("idp")).thenReturn(identityProvider);
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("providerId")).thenReturn("idp");
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(asyncResult.result);
        verify(userAuthenticationManager, times(1)).connect(any(), any(), any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.SUCCESS), any());
    }

    @Test
    public void shouldAuthenticateUser_sso_no_id_token() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.User user  = new io.gravitee.am.identityprovider.api.DefaultUser("username");

        Client client = new Client();
        client.setSingleSignOut(true);

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.getDomainWhitelist()).thenReturn(null);

        when(userAuthenticationManager.connect(any(), any(), any())).thenReturn(Single.just(new User()));
        when(identityProviderManager.getIdentityProvider("idp")).thenReturn(identityProvider);
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(routingContext.get("client")).thenReturn(client);

        when(routingContext.get("providerId")).thenReturn("idp");
        when(routingContext.data()).thenReturn(Map.of(
                "id_token", "some_id_token"
        ));
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);

        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertFalse(asyncResult.isFailed());
        verify(userAuthenticationManager, times(1)).connect(any(), any(), any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.SUCCESS), any());
    }

    @Test
    public void shouldAuthenticateUser_sso_keep_id_token_if_close_session_after_signIn() throws Exception {
        common_keep_id_token_if_close_session_after_signIn(CloseSessionMode.REDIRECT);
    }

    @Test
    public void shouldAuthenticateUser_sso_not_keep_id_token_if_keep_session_active_after_signIn() throws Exception {
        common_keep_id_token_if_close_session_after_signIn(CloseSessionMode.KEEP_ACTIVE);
    }

    private void common_keep_id_token_if_close_session_after_signIn(CloseSessionMode keepAlive) throws InterruptedException {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.User user = new io.gravitee.am.identityprovider.api.DefaultUser("username");

        Client client = new Client();
        client.setSingleSignOut(false);

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.getDomainWhitelist()).thenReturn(null);


        Map<String, Object> contextDataMap = new HashMap<>();
        contextDataMap.put(ID_TOKEN_KEY, "idp_id_token_value");

        when(userAuthenticationManager.connect(any(), any(), any())).thenReturn(Single.just(new User()));
        when(identityProviderManager.getIdentityProvider("idp")).thenReturn(identityProvider);
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(authenticationProvider.closeSessionAfterSignIn()).thenReturn(keepAlive);
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("providerId")).thenReturn("idp");
        when(routingContext.data()).thenReturn(contextDataMap);

        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);

        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertFalse(asyncResult.isFailed());
        verify(userAuthenticationManager, times(1)).connect(any(), any(), any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.SUCCESS), any());
        verify(routingContext, keepAlive == CloseSessionMode.KEEP_ACTIVE ? never() : times(1)).put(OIDC_PROVIDER_ID_TOKEN_KEY, "idp_id_token_value");
    }

    @Test
    public void shouldAuthenticateUser_with_access_token_and_id_token() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.User user  = new io.gravitee.am.identityprovider.api.DefaultUser("username");

        Client client = new Client();

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.getDomainWhitelist()).thenReturn(null);

        when(userAuthenticationManager.connect(any(), any(), any())).thenReturn(Single.just(new User()));
        when(identityProviderManager.getIdentityProvider("idp")).thenReturn(identityProvider);
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("providerId")).thenReturn("idp");
        when(routingContext.data()).thenReturn(Map.of(
                "access_token", "some_access_token",
                "id_token", "some_id_token"
        ));
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);

        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertFalse(asyncResult.isFailed());
        verify(userAuthenticationManager, times(1)).connect(any(), any(), any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.SUCCESS), any());
    }

    @Test
    public void shouldAuthenticateUser_with_id_token_client_sso() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.User user  = new io.gravitee.am.identityprovider.api.DefaultUser("username");

        Client client = new Client();
        client.setSingleSignOut(true);

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.getDomainWhitelist()).thenReturn(null);

        when(userAuthenticationManager.connect(any(), any(), any())).thenReturn(Single.just(new User()));
        when(identityProviderManager.getIdentityProvider("idp")).thenReturn(identityProvider);
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("providerId")).thenReturn("idp");
        when(routingContext.data()).thenReturn(Map.of(
                "id_token", "some_id_token"
        ));
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);

        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Assert.assertFalse(asyncResult.isFailed());
        verify(userAuthenticationManager, times(1)).connect(any(), any(), any());
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
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);

        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Assert.assertTrue(asyncResult.isFailed());
        Assert.assertTrue(asyncResult.getCause() instanceof BadCredentialsException);
        verify(userAuthenticationManager, never()).connect(any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldNotAuthenticateUser_errorParameter() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");

        Client client = new Client();

        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(new DefaultUser("my-user-id")));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("provider")).thenReturn(authenticationProvider);
        when(routingContext.request()).thenReturn(httpServerRequest);
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(httpServerRequest.getParam(ERROR_PARAM_KEY)).thenReturn("login_required");
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);

        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Assert.assertTrue(asyncResult.isFailed());
        Assert.assertTrue(asyncResult.getCause() instanceof InvalidRequestException && asyncResult.getCause().getMessage().contains("login_required"));
        verify(userAuthenticationManager, never()).connect(any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldNotAuthenticateUser_errorParameter_access_denied_User_auth_aborted_franceConnect() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");

        Client client = new Client();

        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(new DefaultUser("my-user-id")));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("provider")).thenReturn(authenticationProvider);
        IdentityProvider mockIdp = mock(IdentityProvider.class);
        when(mockIdp.getType()).thenReturn("franceconnect-am-idp");
        when(identityProviderManager.getIdentityProvider(any())).thenReturn(mockIdp);
        when(routingContext.request()).thenReturn(httpServerRequest);
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(httpServerRequest.getParam(ERROR_PARAM_KEY)).thenReturn("access_denied");
        when(httpServerRequest.getParam(ERROR_DESCRIPTION_PARAM_KEY)).thenReturn("User auth aborted");
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
                asyncResult.setFailed(userAsyncResult.failed());
                asyncResult.setCause(userAsyncResult.cause());
                latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Assert.assertTrue(asyncResult.isFailed());
        Assert.assertTrue(asyncResult.getCause() instanceof UserAuthenticationAbortedException);
        verify(userAuthenticationManager, never()).connect(any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldNotAuthenticateUser_errorParameter_access_denied_User_auth_aborted_noFranceConnect() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");

        Client client = new Client();

        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(new DefaultUser("my-user-id")));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("provider")).thenReturn(authenticationProvider);
        IdentityProvider mockIdp = mock(IdentityProvider.class);
        when(mockIdp.getType()).thenReturn("unknown-am-idp");
        when(identityProviderManager.getIdentityProvider(any())).thenReturn(mockIdp);
        when(routingContext.request()).thenReturn(httpServerRequest);
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(httpServerRequest.getParam(ERROR_PARAM_KEY)).thenReturn("access_denied");
        when(httpServerRequest.getParam(ERROR_DESCRIPTION_PARAM_KEY)).thenReturn("User auth aborted");
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
                asyncResult.setFailed(userAsyncResult.failed());
                asyncResult.setCause(userAsyncResult.cause());
                latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Assert.assertTrue(asyncResult.isFailed());
        Assert.assertTrue(asyncResult.getCause() instanceof InvalidRequestException);
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
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Assert.assertTrue(asyncResult.isFailed());
        Assert.assertTrue(asyncResult.getCause() instanceof BadCredentialsException);
        verify(userAuthenticationManager, never()).connect(any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldAuthenticateUser_Username_DomainAllowed() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id@acme.com");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.User user  = new io.gravitee.am.identityprovider.api.DefaultUser("my-user-id@acme.com");

        Client client = new Client();

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.getDomainWhitelist()).thenReturn(List.of("acme.com"));

        when(userAuthenticationManager.connect(any(), any(), any())).thenReturn(Single.just(new User()));
        when(identityProviderManager.getIdentityProvider("idp")).thenReturn(identityProvider);
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("providerId")).thenReturn("idp");
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);

        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertFalse(asyncResult.isFailed());
        verify(userAuthenticationManager).connect(any(), any(), any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.SUCCESS), any());
    }
    @Test
    public void shouldAuthenticateUser_Username_And_Email_DomainAllowed() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id@acme.com");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.DefaultUser user  = new io.gravitee.am.identityprovider.api.DefaultUser("my-user-id@acme.com");
        user.setEmail("my-user-id@acme.com");
        Client client = new Client();

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.getDomainWhitelist()).thenReturn(List.of("acme.com"));

        when(userAuthenticationManager.connect(any(), any(), any())).thenReturn(Single.just(new User()));
        when(identityProviderManager.getIdentityProvider("idp")).thenReturn(identityProvider);
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("providerId")).thenReturn("idp");
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Assert.assertFalse(asyncResult.isFailed());
        verify(userAuthenticationManager).connect(any(), any(), any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.SUCCESS), any());
    }

    @Test
    public void shouldAuthenticateUser_Email_DomainAllowed() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.DefaultUser user  = new io.gravitee.am.identityprovider.api.DefaultUser("my-user-id");
        user.setEmail("my-user-id@acme.com");

        Client client = new Client();

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.getDomainWhitelist()).thenReturn(List.of("acme.com"));

        when(userAuthenticationManager.connect(any(), any(), any())).thenReturn(Single.just(new User()));
        when(identityProviderManager.getIdentityProvider("idp")).thenReturn(identityProvider);
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("providerId")).thenReturn("idp");
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertFalse(asyncResult.isFailed());
        verify(userAuthenticationManager).connect(any(), any(), any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.SUCCESS), any());
    }

    @Test
    public void shouldNotAuthenticateUser_Email_DomainNotAllowed() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.DefaultUser user  = new io.gravitee.am.identityprovider.api.DefaultUser("my-user-id");
        user.setEmail("my-user-id@acme.com");

        Client client = new Client();

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.getDomainWhitelist()).thenReturn(List.of("otheracme.com"));

        when(identityProviderManager.getIdentityProvider("idp")).thenReturn(identityProvider);
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("providerId")).thenReturn("idp");
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Assert.assertTrue(asyncResult.isFailed());
        Assert.assertTrue(asyncResult.getCause() instanceof LoginCallbackFailedException);
        verify(userAuthenticationManager, never()).connect(any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldNotAuthenticateUser_Username_and_Email_DomainNotAllowed() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id@acme.com");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.DefaultUser user  = new io.gravitee.am.identityprovider.api.DefaultUser("my-user-id@acme.com");
        user.setEmail("my-user-id@acme.com");

        Client client = new Client();

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.getDomainWhitelist()).thenReturn(List.of("otheracme.com"));

        when(identityProviderManager.getIdentityProvider("idp")).thenReturn(identityProvider);
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("providerId")).thenReturn("idp");
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Assert.assertTrue(asyncResult.isFailed());
        Assert.assertTrue(asyncResult.getCause() instanceof LoginCallbackFailedException);
        verify(userAuthenticationManager, never()).connect(any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldNotAuthenticateUser_Username_DomainNotAllowed() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id@acme.com");
        credentials.put("password", "my-user-password");
        credentials.put("provider", "idp");
        credentials.put("additionalParameters", Collections.emptyMap());

        io.gravitee.am.identityprovider.api.User user  = new io.gravitee.am.identityprovider.api.DefaultUser("my-user-id@acme.com");

        Client client = new Client();

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.getDomainWhitelist()).thenReturn(List.of("otheracme.com"));

        when(identityProviderManager.getIdentityProvider("idp")).thenReturn(identityProvider);
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(routingContext.get("client")).thenReturn(client);
        when(routingContext.get("providerId")).thenReturn("idp");
        final io.vertx.core.http.HttpServerRequest delegateRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.getDelegate()).thenReturn(delegateRequest);
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);

        CountDownLatch latch = new CountDownLatch(1);
        Result asyncResult = new Result();
        authProvider.authenticate(routingContext, credentials, userAsyncResult -> {
            asyncResult.setFailed(userAsyncResult.failed());
            asyncResult.setCause(userAsyncResult.cause());
            asyncResult.setResult(userAsyncResult.result());
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        Assert.assertTrue(asyncResult.isFailed());
        Assert.assertTrue(asyncResult.getCause() instanceof LoginCallbackFailedException);
        verify(userAuthenticationManager, never()).connect(any());
        verify(eventManager).publishEvent(argThat(evt -> evt == AuthenticationEvent.FAILURE), any());
    }

    @Setter
    @Getter
    private static class Result {
        boolean failed;
        Throwable cause;
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User result;
    }
}

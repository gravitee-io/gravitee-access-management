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
package io.gravitee.am.gateway.handler.vertx.auth.provider;

import io.gravitee.am.gateway.handler.auth.EndUserAuthentication;
import io.gravitee.am.gateway.handler.auth.exception.BadCredentialsException;
import io.gravitee.am.gateway.handler.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.User;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class OAuth2ClientAuthenticationProviderTest {

    @InjectMocks
    private OAuth2ClientAuthenticationProvider authProvider = new OAuth2ClientAuthenticationProvider();

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private UserService userService;

    @Mock
    private AuthenticationProvider authenticationProvider;

    @Test
    public void shouldAuthenticateUser() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");

        io.gravitee.am.identityprovider.api.User user  = new io.gravitee.am.identityprovider.api.DefaultUser("username");

        when(userService.findOrCreate(any())).thenReturn(Single.just(new User()));
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(user));
        when(identityProviderManager.get(anyString())).thenReturn(Maybe.just(authenticationProvider));

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(credentials, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertNotNull(userAsyncResult.result());
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        verify(userService, times(1)).findOrCreate(any());
    }


    @Test
    public void shouldNotAuthenticateUser_badCredentials() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");

        when(userService.findOrCreate(any())).thenReturn(Single.just(new User()));
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.error(new BadCredentialsException()));
        when(identityProviderManager.get(anyString())).thenReturn(Maybe.just(authenticationProvider));

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(credentials, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertTrue(userAsyncResult.failed());
            Assert.assertTrue(userAsyncResult.cause() instanceof BadCredentialsException);
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        verify(userService, never()).findOrCreate(any());
    }

    @Test
    public void shouldNotAuthenticateUser_noUser() throws Exception {
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-user-id");
        credentials.put("password", "my-user-password");

        when(userService.findOrCreate(any())).thenReturn(Single.just(new User()));
        when(authenticationProvider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.empty());
        when(identityProviderManager.get(anyString())).thenReturn(Maybe.just(authenticationProvider));

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(credentials, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertTrue(userAsyncResult.failed());
            Assert.assertTrue(userAsyncResult.cause() instanceof BadCredentialsException);
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        verify(userService, never()).findOrCreate(any());
    }


}

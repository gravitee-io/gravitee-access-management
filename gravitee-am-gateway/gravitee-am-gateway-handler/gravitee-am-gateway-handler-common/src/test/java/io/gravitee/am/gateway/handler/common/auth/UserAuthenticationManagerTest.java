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
package io.gravitee.am.gateway.handler.common.auth;

import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.impl.UserAuthenticationManagerImpl;
import io.gravitee.am.gateway.handler.common.auth.event.AuthenticationEvent;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.common.exception.authentication.AccountDisabledException;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.InternalAuthenticationServiceException;
import io.gravitee.common.event.EventManager;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserAuthenticationManagerTest {

    @InjectMocks
    private UserAuthenticationManagerImpl userAuthenticationManager = new UserAuthenticationManagerImpl();

    @Mock
    private UserAuthenticationService userAuthenticationService;

    @Mock
    private Domain domain;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private EventManager eventManager;

    @Test
    public void shouldNotAuthenticateUser_noIdentityProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentities(Collections.emptySet());

        TestObserver<User> observer = userAuthenticationManager.authenticate(client, new Authentication() {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return null;
            }

            @Override
            public AuthenticationContext getContext() {
                return null;
            }
        }).test();
        observer.assertNotComplete();
        observer.assertError(InternalAuthenticationServiceException.class);
        verifyZeroInteractions(userAuthenticationService);
    }

    @Test
    public void shouldAuthenticateUser_singleIdentityProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentities(Collections.singleton("idp-1"));

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);

        when(userAuthenticationService.connect(any(), eq(true))).then(invocation -> {
            io.gravitee.am.identityprovider.api.User idpUser = invocation.getArgument(0);
            User user = new User();
            user.setUsername(idpUser.getUsername());
            return Single.just(user);
        });

        when(identityProviderManager.get("idp-1")).thenReturn(Maybe.just(new AuthenticationProvider() {
            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(Authentication authentication) {
                return Maybe.just(new DefaultUser("username"));
            }

            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(String username) {
                return Maybe.empty();
            }
        }));

        TestObserver<User> observer = userAuthenticationManager.authenticate(client, new Authentication() {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return "username";
            }

            @Override
            public AuthenticationContext getContext() {
                return null;
            }
        }).test();

        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(user -> user.getUsername().equals("username"));
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.SUCCESS), any());
    }

    @Test
    public void shouldAuthenticateUser_singleIdentityProvider_throwException() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentities(Collections.singleton("idp-1"));

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);

        when(identityProviderManager.get("idp-1")).thenReturn(Maybe.just(new AuthenticationProvider() {
            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(Authentication authentication) {
                throw new BadCredentialsException();
            }

            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(String username) {
                return Maybe.empty();
            }
        }));

        TestObserver<User> observer = userAuthenticationManager.authenticate(client, new Authentication() {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return "username";
            }

            @Override
            public AuthenticationContext getContext() {
                return null;
            }
        }).test();

        verifyZeroInteractions(userAuthenticationService);
        observer.assertError(BadCredentialsException.class);
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldAuthenticateUser_multipleIdentityProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentities(new LinkedHashSet<>(Arrays.asList("idp-1", "idp-2")));

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");


        IdentityProvider identityProvider2 = new IdentityProvider();
        identityProvider2.setId("idp-2");

        when(userAuthenticationService.connect(any(), eq(true))).then(invocation -> {
            io.gravitee.am.identityprovider.api.User idpUser = invocation.getArgument(0);
            User user = new User();
            user.setUsername(idpUser.getUsername());
            return Single.just(user);
        });

        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);
        when(identityProviderManager.get("idp-1")).thenReturn(Maybe.just(new AuthenticationProvider() {
            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(Authentication authentication) {
                throw new BadCredentialsException();
            }

            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(String username) {
                return Maybe.empty();
            }
        }));

        when(identityProviderManager.getIdentityProvider("idp-2")).thenReturn(identityProvider2);
        when(identityProviderManager.get("idp-2")).thenReturn(Maybe.just(new AuthenticationProvider() {
            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(Authentication authentication) {
                return Maybe.just(new DefaultUser("username"));
            }

            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(String username) {
                return Maybe.empty();
            }
        }));

        TestObserver<User> observer = userAuthenticationManager.authenticate(client, new Authentication() {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return "username";
            }

            @Override
            public AuthenticationContext getContext() {
                return null;
            }
        }).test();

        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(user -> user.getUsername().equals("username"));
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.SUCCESS), any());
    }

    @Test
    public void shouldNotAuthenticateUser_accountDisabled() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentities(Collections.singleton("idp-1"));

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);

        when(userAuthenticationService.connect(any(), eq(true))).then(invocation -> {
            io.gravitee.am.identityprovider.api.User idpUser = invocation.getArgument(0);
            return Single.error(new AccountDisabledException(idpUser.getUsername()));
        });

        when(identityProviderManager.get("idp-1")).thenReturn(Maybe.just(new AuthenticationProvider() {
            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(Authentication authentication) {
                return Maybe.just(new DefaultUser("username"));
            }

            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(String username) {
                return Maybe.empty();
            }
        }));

        TestObserver<User> observer = userAuthenticationManager.authenticate(client, new Authentication() {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return "username";
            }

            @Override
            public AuthenticationContext getContext() {
                return null;
            }
        }).test();

        observer.assertError(AccountDisabledException.class);
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldNotAuthenticateUser_onlyExternalProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentities(Collections.singleton("idp-1"));

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");
        identityProvider.setExternal(true);
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);

        TestObserver<User> observer = userAuthenticationManager.authenticate(client, null).test();
        observer.assertNotComplete();
        observer.assertError(InternalAuthenticationServiceException.class);
        verifyZeroInteractions(userAuthenticationService);
    }
}

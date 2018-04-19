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
package io.gravitee.am.gateway.handler.auth;

import io.gravitee.am.gateway.handler.auth.exception.BadCredentialsException;
import io.gravitee.am.gateway.handler.auth.impl.UserAuthenticationManagerImpl;
import io.gravitee.am.gateway.handler.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserAuthenticationManagerTest {

    @InjectMocks
    private UserAuthenticationManagerImpl userAuthenticationManager = new UserAuthenticationManagerImpl();

    @Mock
    private ClientService clientService;

    @Mock
    private UserService userService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Test
    public void shouldNotAuthenticateUser_noClient() {
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.empty());

        TestObserver<User> observer = userAuthenticationManager.authenticate("client-id", new Authentication() {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return null;
            }

            @Override
            public Map<String, Object> getAdditionalInformation() {
                return null;
            }
        }).test();

        verifyZeroInteractions(userService);
        observer.assertError(NoSuchElementException.class);
    }

    @Test
    public void shouldNotAuthenticateUser_noIdentityProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentities(Collections.emptySet());

        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        TestObserver<User> observer = userAuthenticationManager.authenticate("client-id", new Authentication() {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return null;
            }

            @Override
            public Map<String, Object> getAdditionalInformation() {
                return null;
            }
        }).test();


        verifyZeroInteractions(userService);
        observer.assertError(NoSuchElementException.class);
    }

    @Test
    public void shouldAuthenticateUser_singleIdentityProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentities(Collections.singleton("idp-1"));

        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(userService.findOrCreate(any())).then(invocation -> {
            io.gravitee.am.identityprovider.api.User idpUser = invocation.getArgumentAt(0, io.gravitee.am.identityprovider.api.User.class);
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

        TestObserver<User> observer = userAuthenticationManager.authenticate("client-id", new Authentication() {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return null;
            }

            @Override
            public Map<String, Object> getAdditionalInformation() {
                return null;
            }
        }).test();

        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(user -> user.getUsername().equals("username"));
    }

    @Test
    public void shouldAuthenticateUser_singleIdentityProvider_throwException() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentities(Collections.singleton("idp-1"));

        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));
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

        TestObserver<User> observer = userAuthenticationManager.authenticate("client-id", new Authentication() {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return null;
            }

            @Override
            public Map<String, Object> getAdditionalInformation() {
                return null;
            }
        }).test();

        verifyZeroInteractions(userService);
        observer.assertError(BadCredentialsException.class);
    }

    @Test
    public void shouldAuthenticateUser_multipleIdentityProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentities(new HashSet<>(Arrays.asList("idp-1", "idp-2")));

        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(userService.findOrCreate(any())).then(invocation -> {
            io.gravitee.am.identityprovider.api.User idpUser = invocation.getArgumentAt(0, io.gravitee.am.identityprovider.api.User.class);
            User user = new User();
            user.setUsername(idpUser.getUsername());
            return Single.just(user);
        });
        when(identityProviderManager.get("idp-2")).thenReturn(Maybe.just(new AuthenticationProvider() {
            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(Authentication authentication) {
                return Maybe.empty();
            }

            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(String username) {
                return Maybe.empty();
            }
        }));

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

        TestObserver<User> observer = userAuthenticationManager.authenticate("client-id", new Authentication() {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return null;
            }

            @Override
            public Map<String, Object> getAdditionalInformation() {
                return null;
            }
        }).test();

        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(user -> user.getUsername().equals("username"));
    }
}

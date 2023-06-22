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

import io.gravitee.am.common.exception.authentication.*;
import io.gravitee.am.gateway.handler.common.auth.event.AuthenticationEvent;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationService;
import io.gravitee.am.gateway.handler.common.auth.user.impl.UserAuthenticationManagerImpl;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.common.event.EventManager;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
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

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private UserService userService;

    @Mock
    private PasswordService passwordService;

    @Mock
    private GatewayMetricProvider gatewayMetricProvider;

    @Test
    public void shouldNotAuthenticateUser_noIdentityProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentityProviders(new TreeSet<>());

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
        verify(userAuthenticationService, times(0)).connect(any());
        verify(userAuthenticationService, times(0)).connect(any(), anyBoolean());
    }

    @Test
    public void shouldAuthenticateUser_singleIdentityProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentityProviders(getApplicationIdentityProviders("idp-1"));

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);

        when(passwordService.checkAccountPasswordExpiry(any(), any(), any())).thenReturn(false);

        when(userAuthenticationService.connect(any(), any(), any(), eq(true))).then(invocation -> {
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
                return new SimpleAuthenticationContext();
            }
        }).test();

        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(user -> user.getUsername().equals("username"));
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.SUCCESS), any());
    }

    @Test
    public void shouldAuthenticateUser_singleIdentityProvider_PasswordExpiry() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentityProviders(getApplicationIdentityProviders("idp-1"));

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);

        when(passwordService.checkAccountPasswordExpiry(any(), any(), any())).thenReturn(true);

        when(userAuthenticationService.connect(any(), any(), any(), eq(true))).then(invocation -> {
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
                return new SimpleAuthenticationContext();
            }
        }).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(AccountPasswordExpiredException.class);
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldAuthenticateUser_singleIdentityProvider_throwException() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentityProviders(getApplicationIdentityProviders("idp-1"));

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

        verify(userAuthenticationService, times(0)).connect(any());
        verify(userAuthenticationService, times(0)).connect(any(), anyBoolean());

        observer.assertError(BadCredentialsException.class);
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldAuthenticateUser_multipleIdentityProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentityProviders(getApplicationIdentityProviders("idp-1", "idp-2"));

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");


        IdentityProvider identityProvider2 = new IdentityProvider();
        identityProvider2.setId("idp-2");

        when(passwordService.checkAccountPasswordExpiry(any(), any(), any())).thenReturn(false);
        when(userAuthenticationService.connect(any(), any(), any(), eq(true))).then(invocation -> {
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
                return new SimpleAuthenticationContext();
            }
        }).test();

        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(user -> user.getUsername().equals("username"));
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.SUCCESS), any());
    }

    @Test
    public void shouldAuthenticateUser_multipleIDPs_firstPriorityIdentityProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        ApplicationIdentityProvider applicationIdentityProvider1 = new ApplicationIdentityProvider();
        applicationIdentityProvider1.setIdentity("idp-1");
        applicationIdentityProvider1.setPriority(2);
        ApplicationIdentityProvider applicationIdentityProvider2 = new ApplicationIdentityProvider();
        applicationIdentityProvider2.setIdentity("idp-2");
        applicationIdentityProvider2.setPriority(1);
        // add idp in wrong order to test the behavior
        var identityProviders = new TreeSet<ApplicationIdentityProvider>();
        identityProviders.add(applicationIdentityProvider1);
        identityProviders.add(applicationIdentityProvider2);
        client.setIdentityProviders(identityProviders);

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");

        IdentityProvider identityProvider2 = new IdentityProvider();
        identityProvider2.setId("idp-2");

        when(userAuthenticationService.connect(any(), any(), any(), eq(true))).then(invocation -> {
            io.gravitee.am.identityprovider.api.User idpUser = invocation.getArgument(0);
            User user = new User();
            user.setUsername(idpUser.getUsername());
            return Single.just(user);
        });

        AuthenticationProvider authenticationProvider1 = mock(AuthenticationProvider.class);
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);

        AuthenticationProvider authenticationProvider2 = mock(AuthenticationProvider.class);
        when(authenticationProvider2.loadUserByUsername(any(Authentication.class))).thenReturn(Maybe.just(new DefaultUser("username2")));
        when(identityProviderManager.getIdentityProvider("idp-2")).thenReturn(identityProvider2);
        when(identityProviderManager.get("idp-2")).thenReturn(Maybe.just(authenticationProvider2));

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
                return new SimpleAuthenticationContext();
            }
        }).test();

        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(user -> user.getUsername().equals("username2"));
        verify(authenticationProvider1, never()).loadUserByUsername(any(Authentication.class));
        verify(authenticationProvider2, times(1)).loadUserByUsername(any(Authentication.class));
    }

    @Test
    public void shouldAuthenticateUser_multipleIdentityProvider_one_rule_matching() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentityProviders(getApplicationIdentityProviders("idp-1", "idp-2"));
        client.getIdentityProviders().forEach(appIdp -> appIdp.setSelectionRule("{#context.attributes['testAttribute'] == 'valueAttribute'}"));
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");


        IdentityProvider identityProvider2 = new IdentityProvider();
        identityProvider2.setId("idp-2");

        when(passwordService.checkAccountPasswordExpiry(any(), any(), any())).thenReturn(false);
        when(userAuthenticationService.connect(any(), any(), any(), eq(true))).then(invocation -> {
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
                var simpleAuthenticationContext = new SimpleAuthenticationContext();
                simpleAuthenticationContext.setAttribute("testAttribute", "valueAttribute");
                return simpleAuthenticationContext;
            }
        }).test();

        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(user -> user.getUsername().equals("username"));
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.SUCCESS), any());
    }
    @Test
    public void shouldNotAuthenticateUser_multipleIdentityProvider_wrongRule() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentityProviders(getApplicationIdentityProviders("idp-1", "idp-2"));
        client.getIdentityProviders().forEach(appIdp -> appIdp.setSelectionRule("{#context.attributes.testAttribute == 'valueAttribute'}"));
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");


        IdentityProvider identityProvider2 = new IdentityProvider();
        identityProvider2.setId("idp-2");

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
                var simpleAuthenticationContext = new SimpleAuthenticationContext();
                simpleAuthenticationContext.setAttribute("testAttribute", "valueAttribute");
                return simpleAuthenticationContext;
            }
        }).test();

        observer.assertNotComplete();
        observer.assertError(InternalAuthenticationServiceException.class);
    }

    @Test
    public void shouldNotAuthenticateUser_accountDisabled() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentityProviders(getApplicationIdentityProviders("idp-1"));

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);

        when(userAuthenticationService.connect(any(), any(), any(), eq(true))).then(invocation -> {
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
                return new SimpleAuthenticationContext();
            }
        }).test();

        observer.assertError(AccountDisabledException.class);
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldNotAuthenticateUser_onlyExternalProvider() {
        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentityProviders(getApplicationIdentityProviders("idp-1"));

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");
        identityProvider.setExternal(true);
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);

        TestObserver<User> observer = userAuthenticationManager.authenticate(client, null).test();
        observer.assertNotComplete();
        observer.assertError(InternalAuthenticationServiceException.class);
        verify(userAuthenticationService, times(0)).connect(any());
        verify(userAuthenticationService, times(0)).connect(any(), anyBoolean());
    }

    @Test
    public void shouldNotAuthenticateUser_unknownUserFromIdp_loginAttempt_enabled() {
        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setInherited(false);
        accountSettings.setLoginAttemptsDetectionEnabled(true);
        accountSettings.setMaxLoginAttempts(1);

        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentityProviders(getApplicationIdentityProviders("idp-1"));
        client.setAccountSettings(accountSettings);

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);
        when(identityProviderManager.get("idp-1")).thenReturn(Maybe.just(new AuthenticationProvider() {
            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(Authentication authentication) {
                return Maybe.error(new UsernameNotFoundException("username"));
            }

            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(String username) {
                return Maybe.empty();
            }
        }));

        when(loginAttemptService.checkAccount(any(), any())).thenReturn(Maybe.empty());
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

        observer.assertError(BadCredentialsException.class);
        verify(userService, never()).findByDomainAndUsernameAndSource(anyString(), anyString(), anyString());
        verify(loginAttemptService, never()).loginFailed(any(), any());
        verify(userAuthenticationService, never()).lockAccount(any(), any(), any(), any());
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.FAILURE), any());
    }

    @Test
    public void shouldNotAuthenticateUser_unknownUserFromAM_loginAttempt_enabled() {
        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setInherited(false);
        accountSettings.setLoginAttemptsDetectionEnabled(true);
        accountSettings.setMaxLoginAttempts(1);

        Client client = new Client();
        client.setClientId("client-id");
        client.setIdentityProviders(getApplicationIdentityProviders("idp-1"));
        client.setAccountSettings(accountSettings);

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(identityProvider);
        when(identityProviderManager.get("idp-1")).thenReturn(Maybe.just(new AuthenticationProvider() {
            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(Authentication authentication) {
                return Maybe.error(new BadCredentialsException("username"));
            }

            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(String username) {
                return Maybe.empty();
            }
        }));

        when(domain.getId()).thenReturn("domain-id");
        when(userService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(loginAttemptService.checkAccount(any(), any())).thenReturn(Maybe.empty());
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

        observer.assertError(BadCredentialsException.class);
        verify(userService, times(1)).findByDomainAndUsernameAndSource(anyString(), anyString(), anyString());
        verify(loginAttemptService, never()).loginFailed(any(), any());
        verify(userAuthenticationService, never()).lockAccount(any(), any(), any(), any());
        verify(eventManager, times(1)).publishEvent(eq(AuthenticationEvent.FAILURE), any());
    }

    private SortedSet<ApplicationIdentityProvider> getApplicationIdentityProviders(String... identities) {
        var set = new TreeSet<ApplicationIdentityProvider>();
        Arrays.stream(identities).forEach(identity -> {
            var patchAppIdp = new ApplicationIdentityProvider();
            patchAppIdp.setIdentity(identity);
            set.add(patchAppIdp);
        });
        return set;
    }
}

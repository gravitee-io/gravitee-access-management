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
package io.gravitee.am.gateway.handler.oauth2.provider.security;

import io.gravitee.am.gateway.handler.oauth2.provider.client.DelegateClientDetails;
import io.gravitee.am.gateway.handler.oauth2.security.IdentityProviderManager;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Client;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.util.OAuth2Utils;
import org.springframework.security.oauth2.provider.ClientDetailsService;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientBasedAuthenticationProviderTest {

    @InjectMocks
    private ClientBasedAuthenticationProvider clientBasedAuthenticationProvider = new ClientBasedAuthenticationProvider();

    @Mock
    private ClientDetailsService clientDetailsService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private Authentication authentication;

    @Test
    public void shouldAuthenticate_firstAuthenticationProviderNull() {
        final String identity1 = "identity-1";
        final String identity2 = "identity-2";
        final String clientId = "client-id";

        final Client client = new Client();
        client.setId(clientId);
        client.setClientId(clientId);
        client.setIdentities(new HashSet<>(Arrays.asList(identity1, identity2)));

        final DelegateClientDetails clientDetails = new DelegateClientDetails(client);

        final AuthenticationProvider authenticationProvider = new AuthenticationProvider() {
            @Override
            public User loadUserByUsername(io.gravitee.am.identityprovider.api.Authentication authentication) {
                return new DefaultUser("username");
            }

            @Override
            public User loadUserByUsername(String username) {
                return new DefaultUser("username");
            }
        };

        Map<String, String> details = Collections.singletonMap(OAuth2Utils.CLIENT_ID, clientId);
        when(authentication.getDetails()).thenReturn(details);
        when(authentication.getName()).thenReturn("name");
        when(authentication.getCredentials()).thenReturn("password");
        when(clientDetailsService.loadClientByClientId(clientId)).thenReturn(clientDetails);
        when(identityProviderManager.get(anyString())).thenReturn(null).thenReturn(authenticationProvider);

        Authentication user = clientBasedAuthenticationProvider.authenticate(authentication);
        Assert.assertNotNull(user);
        Assert.assertEquals(user.getName(), "username");
    }

    @Test
    public void shouldAuthenticate_firstAuthenticationBadCredentials() {
        final String identity1 = "identity-1";
        final String identity2 = "identity-2";
        final String clientId = "client-id";

        final Client client = new Client();
        client.setId(clientId);
        client.setClientId(clientId);
        client.setIdentities(new HashSet<>(Arrays.asList(identity1, identity2)));

        final DelegateClientDetails clientDetails = new DelegateClientDetails(client);

        final AuthenticationProvider badCredentialsAuthenticationProvider = new AuthenticationProvider() {
            @Override
            public User loadUserByUsername(io.gravitee.am.identityprovider.api.Authentication authentication) {
                throw new BadCredentialsException("authentication failed");
            }

            @Override
            public User loadUserByUsername(String username) {
                return new DefaultUser("username");
            }
        };

        final AuthenticationProvider authenticationProvider = new AuthenticationProvider() {
            @Override
            public User loadUserByUsername(io.gravitee.am.identityprovider.api.Authentication authentication) {
                return new DefaultUser("username");
            }

            @Override
            public User loadUserByUsername(String username) {
                return new DefaultUser("username");
            }
        };

        Map<String, String> details = Collections.singletonMap(OAuth2Utils.CLIENT_ID, clientId);
        when(authentication.getDetails()).thenReturn(details);
        when(authentication.getName()).thenReturn("name");
        when(authentication.getCredentials()).thenReturn("password");
        when(clientDetailsService.loadClientByClientId(clientId)).thenReturn(clientDetails);
        when(identityProviderManager.get(anyString())).thenReturn(badCredentialsAuthenticationProvider).thenReturn(authenticationProvider);

        Authentication user = clientBasedAuthenticationProvider.authenticate(authentication);
        Assert.assertNotNull(user);
        Assert.assertEquals(user.getName(), "username");
    }

    @Test(expected = BadCredentialsException.class)
    public void shouldNotAuthenticate_bothAuthenticationProviderBadCredentials() {
        final String identity1 = "identity-1";
        final String identity2 = "identity-2";
        final String clientId = "client-id";

        final Client client = new Client();
        client.setId(clientId);
        client.setClientId(clientId);
        client.setIdentities(new HashSet<>(Arrays.asList(identity1, identity2)));

        final DelegateClientDetails clientDetails = new DelegateClientDetails(client);

        final AuthenticationProvider badCredentialsAuthenticationProvider = new AuthenticationProvider() {
            @Override
            public User loadUserByUsername(io.gravitee.am.identityprovider.api.Authentication authentication) {
                throw new BadCredentialsException("authentication failed");
            }

            @Override
            public User loadUserByUsername(String username) {
                return new DefaultUser("username");
            }
        };

        final AuthenticationProvider authenticationProvider = new AuthenticationProvider() {
            @Override
            public User loadUserByUsername(io.gravitee.am.identityprovider.api.Authentication authentication) {
                throw new BadCredentialsException("authentication failed");
            }

            @Override
            public User loadUserByUsername(String username) {
                return new DefaultUser("username");
            }
        };

        Map<String, String> details = Collections.singletonMap(OAuth2Utils.CLIENT_ID, clientId);
        when(authentication.getDetails()).thenReturn(details);
        when(authentication.getName()).thenReturn("name");
        when(authentication.getCredentials()).thenReturn("password");
        when(clientDetailsService.loadClientByClientId(clientId)).thenReturn(clientDetails);
        when(identityProviderManager.get(anyString())).thenReturn(badCredentialsAuthenticationProvider).thenReturn(authenticationProvider);

        clientBasedAuthenticationProvider.authenticate(authentication);
    }

    @Test(expected = BadCredentialsException.class)
    public void shouldNotAuthenticate_firstAuthenticationProviderNullAndSecondBadCredentials() {
        final String identity1 = "identity-1";
        final String identity2 = "identity-2";
        final String clientId = "client-id";

        final Client client = new Client();
        client.setId(clientId);
        client.setClientId(clientId);
        client.setIdentities(new HashSet<>(Arrays.asList(identity1, identity2)));

        final DelegateClientDetails clientDetails = new DelegateClientDetails(client);

        final AuthenticationProvider badCredentialsAuthenticationProvider = new AuthenticationProvider() {
            @Override
            public User loadUserByUsername(io.gravitee.am.identityprovider.api.Authentication authentication) {
                throw new BadCredentialsException("authentication failed");
            }

            @Override
            public User loadUserByUsername(String username) {
                return new DefaultUser("username");
            }
        };

        Map<String, String> details = Collections.singletonMap(OAuth2Utils.CLIENT_ID, clientId);
        when(authentication.getDetails()).thenReturn(details);
        when(authentication.getName()).thenReturn("name");
        when(authentication.getCredentials()).thenReturn("password");
        when(clientDetailsService.loadClientByClientId(clientId)).thenReturn(clientDetails);
        when(identityProviderManager.get(anyString())).thenReturn(null).thenReturn(badCredentialsAuthenticationProvider);

        clientBasedAuthenticationProvider.authenticate(authentication);
    }
}

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
package io.gravitee.am.management.handlers.management.api.authentication.provider.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Organization;
import io.gravitee.am.service.OrganizationService;
import io.reactivex.Maybe;
import io.reactivex.Single;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ManagementAuthenticationProviderTest {

    @InjectMocks
    private ManagementAuthenticationProvider managementAuthenticationProvider = new ManagementAuthenticationProvider();

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private OrganizationService organizationService;

    @Before
    public void init() {
        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("idp1", "idp2"));
        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
    }

    @Test
    public void shouldAuthenticate_firstAuthProvider() {
        AuthenticationProvider authenticationProvider = mock(AuthenticationProvider.class);

        when(authenticationProvider.loadUserByUsername(any(io.gravitee.am.identityprovider.api.Authentication.class)))
            .thenReturn(Maybe.just(new DefaultUser("username")));
        when(identityProviderManager.getIdentityProvider("idp1")).thenReturn(new IdentityProvider());
        when(identityProviderManager.get("idp1")).thenReturn(authenticationProvider);

        Authentication authentication = managementAuthenticationProvider.authenticate(
            new UsernamePasswordAuthenticationToken("username", "password")
        );

        Assert.assertNotNull(authentication);
        verify(identityProviderManager, times(1)).get("idp1");
        verify(identityProviderManager, never()).get("idp2");
    }

    @Test
    public void shouldAuthenticate_secondAuthProvider() {
        AuthenticationProvider authenticationProvider = mock(AuthenticationProvider.class);
        when(authenticationProvider.loadUserByUsername(any(io.gravitee.am.identityprovider.api.Authentication.class)))
            .thenReturn(Maybe.error(new BadCredentialsException()));
        AuthenticationProvider authenticationProvider2 = mock(AuthenticationProvider.class);
        when(authenticationProvider2.loadUserByUsername(any(io.gravitee.am.identityprovider.api.Authentication.class)))
            .thenReturn(Maybe.just(new DefaultUser("username")));
        when(identityProviderManager.getIdentityProvider("idp1")).thenReturn(new IdentityProvider());
        when(identityProviderManager.getIdentityProvider("idp2")).thenReturn(new IdentityProvider());
        when(identityProviderManager.get("idp1")).thenReturn(authenticationProvider);
        when(identityProviderManager.get("idp2")).thenReturn(authenticationProvider2);

        Authentication authentication = managementAuthenticationProvider.authenticate(
            new UsernamePasswordAuthenticationToken("username", "password")
        );

        Assert.assertNotNull(authentication);
        verify(identityProviderManager, times(1)).get("idp1");
        verify(identityProviderManager, times(1)).get("idp2");
    }

    @Test
    public void shouldAuthenticate_secondAuthProvider_firstNull() {
        AuthenticationProvider authenticationProvider2 = mock(AuthenticationProvider.class);
        when(authenticationProvider2.loadUserByUsername(any(io.gravitee.am.identityprovider.api.Authentication.class)))
            .thenReturn(Maybe.just(new DefaultUser("username")));
        when(identityProviderManager.getIdentityProvider("idp1")).thenReturn(new IdentityProvider());
        when(identityProviderManager.getIdentityProvider("idp2")).thenReturn(new IdentityProvider());
        when(identityProviderManager.get("idp1")).thenReturn(null);
        when(identityProviderManager.get("idp2")).thenReturn(authenticationProvider2);

        Authentication authentication = managementAuthenticationProvider.authenticate(
            new UsernamePasswordAuthenticationToken("username", "password")
        );

        Assert.assertNotNull(authentication);
        verify(identityProviderManager, times(1)).get("idp1");
        verify(identityProviderManager, times(1)).get("idp2");
    }

    @Test(expected = org.springframework.security.authentication.BadCredentialsException.class)
    public void shouldNotAuthenticate_wrongCredentials() {
        AuthenticationProvider authenticationProvider = mock(AuthenticationProvider.class);
        when(authenticationProvider.loadUserByUsername(any(io.gravitee.am.identityprovider.api.Authentication.class)))
            .thenReturn(Maybe.error(new BadCredentialsException()));
        AuthenticationProvider authenticationProvider2 = mock(AuthenticationProvider.class);
        when(authenticationProvider2.loadUserByUsername(any(io.gravitee.am.identityprovider.api.Authentication.class)))
            .thenReturn(Maybe.error(new BadCredentialsException()));
        when(identityProviderManager.getIdentityProvider("idp1")).thenReturn(new IdentityProvider());
        when(identityProviderManager.getIdentityProvider("idp2")).thenReturn(new IdentityProvider());
        when(identityProviderManager.get("idp1")).thenReturn(authenticationProvider);
        when(identityProviderManager.get("idp2")).thenReturn(authenticationProvider2);

        Authentication authentication = managementAuthenticationProvider.authenticate(
            new UsernamePasswordAuthenticationToken("username", "password")
        );

        Assert.assertNull(authentication);
        verify(identityProviderManager, times(1)).get("idp1");
        verify(identityProviderManager, times(1)).get("idp2");
    }

    @Test
    public void shouldAuthenticate_skipExternalProvider() {
        AuthenticationProvider authenticationProvider2 = mock(AuthenticationProvider.class);
        when(authenticationProvider2.loadUserByUsername(any(io.gravitee.am.identityprovider.api.Authentication.class)))
            .thenReturn(Maybe.just(new DefaultUser("username")));
        IdentityProvider externalIdp = new IdentityProvider();
        externalIdp.setExternal(true);
        when(identityProviderManager.getIdentityProvider("idp1")).thenReturn(externalIdp);
        when(identityProviderManager.getIdentityProvider("idp2")).thenReturn(new IdentityProvider());
        when(identityProviderManager.get("idp2")).thenReturn(authenticationProvider2);

        Authentication authentication = managementAuthenticationProvider.authenticate(
            new UsernamePasswordAuthenticationToken("username", "password")
        );

        Assert.assertNotNull(authentication);
        verify(identityProviderManager, times(0)).get("idp1");
        verify(identityProviderManager, times(1)).get("idp2");
    }
}

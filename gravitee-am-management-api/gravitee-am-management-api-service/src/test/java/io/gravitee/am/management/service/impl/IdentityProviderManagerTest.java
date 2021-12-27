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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.management.service.InMemoryIdentityProviderListener;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.service.RoleService;
import io.reactivex.Flowable;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class IdentityProviderManagerTest {
    private static final String ADMIN_USERNAME = "admin";

    @Spy
    private Environment environment = new StandardEnvironment();

    @Mock
    private RoleService roleService;

    @Mock
    private InMemoryIdentityProviderListener listener;

    @Mock
    private IdentityProviderPluginManager idpPluginManager;

    @InjectMocks
    private IdentityProviderManagerImpl cut = new IdentityProviderManagerImpl();

    @Before
    public void init() {
        reset(listener);
        cut.setListener(listener);
    }

    @Test
    public void shouldRegisterProvider() {
        defineDefaultSecurityConfig(true);
        Role role = new Role();
        role.setId("roleid");
        role.setName("ORGANIZATION_PRIMARY_OWNER");

        when(roleService.findRolesByName(any(), any(), any(), any())).thenReturn(Flowable.just(role));
        when(idpPluginManager.create(eq("gravitee-am-idp"), any())).thenReturn(mock(UserProvider.class));

        cut.loadIdentityProviders();

        verify(listener, times(2)).registerAuthenticationProvider(any());

        verify(listener).registerAuthenticationProvider(argThat(idp -> {
            return ReferenceType.ORGANIZATION.equals(idp.getReferenceType()) &&
                    Organization.DEFAULT.equals(idp.getReferenceId()) &&
                    idp.getRoleMapper() != null &&
                    idp.getRoleMapper().containsKey("roleid");
        }));

        verify(listener).registerAuthenticationProvider(argThat(idp -> {
            return ReferenceType.ORGANIZATION.equals(idp.getReferenceType()) &&
                    Organization.DEFAULT.equals(idp.getReferenceId()) &&
                    idp.getType().equals("gravitee-am-idp");
        }));
        verify(idpPluginManager).create(eq("gravitee-am-idp"), any());

    }

    @Test
    public void shouldNotRegisterProvider_Disabled() {
        defineDefaultSecurityConfig(false);
        Role role = new Role();
        role.setId("roleid");
        role.setName("ORGANIZATION_PRIMARY_OWNER");

        when(idpPluginManager.create(eq("gravitee-am-idp"), any())).thenReturn(mock(UserProvider.class));

        cut.loadIdentityProviders();

        verify(listener).registerAuthenticationProvider(argThat(idp -> {
            return ReferenceType.ORGANIZATION.equals(idp.getReferenceType()) &&
                    Organization.DEFAULT.equals(idp.getReferenceId()) &&
                    idp.getType().equals("gravitee-am-idp");
        }));

        verify(listener).registerAuthenticationProvider(any());
        verify(idpPluginManager).create(eq("gravitee-am-idp"), any());
    }

    private void defineDefaultSecurityConfig(boolean enabled) {
        reset(environment);
        doReturn("memory").when(environment).getProperty("security.providers[0].type");
        doReturn("none").when(environment).getProperty(eq("security.providers[0].password-encoding-algo"), any(), any());
        doReturn(ADMIN_USERNAME).when(environment).getProperty("security.providers[0].users[0].username");
        doReturn("adminadmin").when(environment).getProperty("security.providers[0].users[0].password");
        doReturn("ORGANIZATION_PRIMARY_OWNER").when(environment).getProperty("security.providers[0].users[0].role");
        doReturn(enabled).when(environment).getProperty("security.providers[0].enabled", boolean.class, false);
    }

    @Test
    public void shouldGetGraviteeProvider() {
        when(this.idpPluginManager.create(any(), any())).thenReturn(mock(UserProvider.class));
        cut.loadIdentityProviders();

        final TestObserver<UserProvider> observer = this.cut.getUserProvider(IdentityProviderManagerImpl.IDP_GRAVITEE).test();

        observer.awaitTerminalEvent();
        observer.assertNoErrors();
        observer.assertValueCount(1);

        verify(idpPluginManager, never()).findById(any());
    }
}

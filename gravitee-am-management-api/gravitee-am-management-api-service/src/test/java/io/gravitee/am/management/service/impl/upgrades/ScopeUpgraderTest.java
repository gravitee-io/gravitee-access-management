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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.application.ApplicationAdvancedSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.*;
import io.gravitee.am.service.model.NewScope;
import io.reactivex.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ScopeUpgraderTest {

    @InjectMocks
    private ScopeUpgrader scopeUpgrader = new ScopeUpgrader();

    @Mock
    private DomainService domainService;

    @Mock
    private ScopeService scopeService;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private RoleService roleService;

    @Test
    public void shouldCreateScopes_withRoleAndClientScopes() {
        final Scope domainScope = new Scope();
        domainScope.setId("domain-scope-key");
        domainScope.setKey("domain-scope-key");

        final String domainId = "domain-id";
        final String domainName = "domain-name";
        final Domain domain = new Domain();
        domain.setId(domainId);
        domain.setName(domainName);

        final Scope clientScope = new Scope();
        clientScope.setId("client-scope-key");
        clientScope.setKey("client-scope-key");

        final Application app = new Application();
        app.setId("client-id");
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setScopes(Collections.singletonList(clientScope.getKey()));
        settings.setOauth(oauth);
        app.setSettings(settings);

        final Scope roleScope = new Scope();
        roleScope.setId("role-scope-key");
        roleScope.setKey("role-scope-key");

        final Role role = new Role();
        role.setId("role-id");
        role.setOauthScopes(Collections.singletonList(roleScope.getKey()));

        when(domainService.findAll()).thenReturn(Single.just(Collections.singletonList(domain)));
        when(scopeService.findByDomain(domain.getId(), 0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page<>(Collections.emptySet(),0 ,0)))
                .thenReturn(Single.just(new Page<>(Collections.singleton(domainScope),0, 1)));
        when(applicationService.findByDomain(domain.getId())).thenReturn(Single.just(Collections.singleton(app)));
        when(roleService.findByDomain(domain.getId())).thenReturn(Single.just(Collections.singleton(role)));
        when(scopeService.create(any(String.class), any(NewScope.class))).thenReturn(Single.just(new Scope()));

        scopeUpgrader.upgrade();

        verify(domainService, times(1)).findAll();
        verify(scopeService, times(3)).findByDomain(domain.getId(), 0, Integer.MAX_VALUE);
        verify(applicationService, times(1)).findByDomain(domain.getId());
        verify(roleService, times(1)).findByDomain(domain.getId());
        verify(scopeService, times(2)).create(any(String.class), any(NewScope.class));
    }

    @Test
    public void shouldNotCreateScopes_domainHasScopes() {
        final Scope domainScope = new Scope();
        domainScope.setId("domain-scope-key");
        domainScope.setKey("domain-scope-key");

        final String domainId = "domain-id";
        final String domainName = "domain-name";
        final Domain domain = new Domain();
        domain.setId(domainId);
        domain.setName(domainName);

        when(domainService.findAll()).thenReturn(Single.just(Collections.singletonList(domain)));
        when(scopeService.findByDomain(domain.getId(), 0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page<>(Collections.singleton(domainScope), 0, 1)));

        scopeUpgrader.upgrade();

        verify(domainService, times(1)).findAll();
        verify(scopeService, times(1)).findByDomain(domain.getId(), 0, Integer.MAX_VALUE);
        verify(applicationService, never()).findByDomain(domain.getId());
        verify(roleService, never()).findByDomain(domain.getId());
        verify(scopeService, never()).create(any(String.class), any(NewScope.class));

    }


    @Test
    public void shouldNotCreateScopes_noClientAndNoRole() {
        final Scope domainScope = new Scope();
        domainScope.setId("domain-scope-key");
        domainScope.setKey("domain-scope-key");

        final String domainId = "domain-id";
        final String domainName = "domain-name";
        final Domain domain = new Domain();
        domain.setId(domainId);
        domain.setName(domainName);

        when(domainService.findAll()).thenReturn(Single.just(Collections.singletonList(domain)));
        when(scopeService.findByDomain(domain.getId(), 0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page<>(Collections.emptySet(), 0, 0)))
                .thenReturn(Single.just(new Page<>(Collections.singleton(domainScope), 0, 0)));
        when(applicationService.findByDomain(domain.getId())).thenReturn(Single.just(Collections.emptySet()));
        when(roleService.findByDomain(domain.getId())).thenReturn(Single.just(Collections.emptySet()));

        scopeUpgrader.upgrade();

        verify(domainService, times(1)).findAll();
        verify(scopeService, times(1)).findByDomain(domain.getId(), 0, Integer.MAX_VALUE);
        verify(applicationService, times(1)).findByDomain(domain.getId());
        verify(roleService, times(1)).findByDomain(domain.getId());
        verify(scopeService, never()).create(any(String.class), any(NewScope.class));

    }

    @Test
    public void shouldNotCreateScopes_clientsAndRolesHaveNoScopes() {
        final Scope domainScope = new Scope();
        domainScope.setId("domain-scope-key");
        domainScope.setKey("domain-scope-key");

        final String domainId = "domain-id";
        final String domainName = "domain-name";
        final Domain domain = new Domain();
        domain.setId(domainId);
        domain.setName(domainName);

        final Application app = new Application();
        app.setId("client-id");

        final Role role = new Role();
        role.setId("role-id");
        role.setPermissionAcls(null);

        when(domainService.findAll()).thenReturn(Single.just(Collections.singletonList(domain)));
        when(scopeService.findByDomain(domain.getId(), 0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page<>(Collections.emptySet(),0, Integer.MAX_VALUE))).thenReturn(Single.just(new Page<>(Collections.singleton(domainScope), 0, Integer.MAX_VALUE)));
        when(applicationService.findByDomain(domain.getId())).thenReturn(Single.just(Collections.singleton(app)));
        when(roleService.findByDomain(domain.getId())).thenReturn(Single.just(Collections.singleton(role)));

        scopeUpgrader.upgrade();

        verify(domainService, times(1)).findAll();
        verify(scopeService, times(1)).findByDomain(domain.getId(), 0, Integer.MAX_VALUE);
        verify(applicationService, times(1)).findByDomain(domain.getId());
        verify(roleService, times(1)).findByDomain(domain.getId());
        verify(scopeService, never()).create(any(String.class), any(NewScope.class));

    }
}

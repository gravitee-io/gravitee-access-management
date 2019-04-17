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
package io.gravitee.am.management.service;

import io.gravitee.am.management.service.impl.upgrades.InitializeUpgrader;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.UpdateDomain;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class InitializeUpgraderTest {

    private final static String ADMIN_DOMAIN = "admin";
    private final static String ADMIN_CLIENT_ID = "admin";

    @InjectMocks
    private InitializeUpgrader initializeUpgrader = new InitializeUpgrader();

    @Mock
    private DomainService domainService;

    @Mock
    private ClientService clientService;

    @Mock
    private IdentityProviderService identityProviderService;

    @Test
    public void shouldCreateAdminDomain() {
        final Domain adminDomain = new Domain();
        adminDomain.setId(ADMIN_DOMAIN);
        adminDomain.setName("ADMIN");

        when(domainService.findById(ADMIN_DOMAIN)).thenReturn(Maybe.empty());
        when(identityProviderService.create(eq(ADMIN_DOMAIN), any(NewIdentityProvider.class))).thenReturn(Single.just(new IdentityProvider()));
        when(domainService.create(any(NewDomain.class))).thenReturn(Single.just(adminDomain));
        when(domainService.update(eq(ADMIN_DOMAIN), any(UpdateDomain.class))).thenReturn(Single.just(adminDomain));
        when(domainService.setMasterDomain(ADMIN_DOMAIN, true)).thenReturn(Single.just(adminDomain));

        initializeUpgrader.upgrade();

        verify(domainService, times(1)).findById(ADMIN_DOMAIN);
        verify(identityProviderService, times(1)).create(eq(ADMIN_DOMAIN), any(NewIdentityProvider.class));
        verify(domainService, times(1)).create(any(NewDomain.class));
        verify(domainService, times(1)).update(eq(ADMIN_DOMAIN), any(UpdateDomain.class));
        verify(domainService, times(1)).setMasterDomain(ADMIN_DOMAIN, true);
        verify(clientService, never()).findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID);
    }

    @Test
    public void shouldUpdateAdminDomain_adminClientExists() {
        final Domain adminDomain = new Domain();
        adminDomain.setId(ADMIN_DOMAIN);
        adminDomain.setName("ADMIN");
        adminDomain.setMaster(true);

        final Client adminClient = new Client();
        adminClient.setId(ADMIN_CLIENT_ID);
        adminClient.setClientId(ADMIN_CLIENT_ID);

        when(domainService.findById(ADMIN_DOMAIN)).thenReturn(Maybe.just(adminDomain));
        when(domainService.update(eq(ADMIN_DOMAIN), any(UpdateDomain.class))).thenReturn(Single.just(adminDomain));
        when(clientService.findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID)).thenReturn(Maybe.just(adminClient));
        when(clientService.delete(ADMIN_CLIENT_ID)).thenReturn(Completable.complete());

        initializeUpgrader.upgrade();

        verify(domainService, times(1)).findById(ADMIN_DOMAIN);
        verify(clientService, times(1)).findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID);
        verify(domainService, times(1)).update(eq(ADMIN_DOMAIN), any(UpdateDomain.class));
        verify(clientService, times(1)).delete(ADMIN_CLIENT_ID);

        verify(identityProviderService, never()).create(eq(ADMIN_DOMAIN), any(NewIdentityProvider.class));
        verify(domainService, never()).create(any(NewDomain.class));
        verify(domainService, never()).setMasterDomain(ADMIN_DOMAIN, true);
    }

    @Test
    public void shouldUpdateAdminDomain_adminClientNotFound() {
        final Domain adminDomain = new Domain();
        adminDomain.setId(ADMIN_DOMAIN);
        adminDomain.setName("ADMIN");
        adminDomain.setMaster(true);

        final Client adminClient = new Client();
        adminClient.setId(ADMIN_CLIENT_ID);
        adminClient.setClientId(ADMIN_CLIENT_ID);

        when(domainService.findById(ADMIN_DOMAIN)).thenReturn(Maybe.just(adminDomain));
        when(clientService.findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID)).thenReturn(Maybe.empty());

        initializeUpgrader.upgrade();

        verify(domainService, times(1)).findById(ADMIN_DOMAIN);
        verify(clientService, times(1)).findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID);

        verify(domainService, never()).update(eq(ADMIN_DOMAIN), any(UpdateDomain.class));
        verify(clientService, never()).delete(ADMIN_CLIENT_ID);
        verify(identityProviderService, never()).create(eq(ADMIN_DOMAIN), any(NewIdentityProvider.class));
        verify(domainService, never()).create(any(NewDomain.class));
        verify(domainService, never()).setMasterDomain(ADMIN_DOMAIN, true);
    }

    @Test
    public void shouldUpdateMasterFlagAdminDomain_adminClientNotFound() {
        final Domain adminDomain = new Domain();
        adminDomain.setId(ADMIN_DOMAIN);
        adminDomain.setName("ADMIN");
        adminDomain.setMaster(false);

        final Client adminClient = new Client();
        adminClient.setId(ADMIN_CLIENT_ID);
        adminClient.setClientId(ADMIN_CLIENT_ID);

        when(domainService.findById(ADMIN_DOMAIN)).thenReturn(Maybe.just(adminDomain));
        when(clientService.findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID)).thenReturn(Maybe.empty());
        when(domainService.setMasterDomain(ADMIN_DOMAIN, true)).thenReturn(Single.just(adminDomain));

        initializeUpgrader.upgrade();

        verify(domainService, times(1)).findById(ADMIN_DOMAIN);
        verify(clientService, times(1)).findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID);
        verify(domainService, times(1)).setMasterDomain(ADMIN_DOMAIN, true);

        verify(domainService, never()).update(eq(ADMIN_DOMAIN), any(UpdateDomain.class));
        verify(clientService, never()).delete(ADMIN_CLIENT_ID);
        verify(identityProviderService, never()).create(eq(ADMIN_DOMAIN), any(NewIdentityProvider.class));
        verify(domainService, never()).create(any(NewDomain.class));
    }


}

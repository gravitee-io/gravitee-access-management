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
package io.gravitee.am.gateway.service;

import io.gravitee.am.gateway.service.exception.DomainDeleteMasterException;
import io.gravitee.am.gateway.service.exception.DomainNotFoundException;
import io.gravitee.am.gateway.service.impl.DomainServiceImpl;
import io.gravitee.am.model.*;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.DomainRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DomainService_DeleteTest {

    private static final String DOMAIN_ID = "id-domain";
    private static final String IDP_ID = "id-idp";
    private static final String CERTIFICATE_ID = "id-certificate";
    private static final String ROLE_ID = "id-role";
    private static final String USER_ID = "id-user";

    @InjectMocks
    private DomainService domainService = new DomainServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private Certificate certificate;

    @Mock
    private IdentityProvider identityProvider;

    @Mock
    private Role role;

    @Mock
    private User user;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private ClientService clientService;

    @Mock
    private CertificateService certificateService;

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private UserService userService;

    @Mock
    private RoleService roleService;

    @Test(expected = DomainNotFoundException.class)
    public void shouldNotDeleteBecauseDoesntExist() throws TechnicalException {
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Optional.empty());
        domainService.delete(DOMAIN_ID);
    }

    @Test
    public void shouldDelete() throws TechnicalException {
        Client mockClient1 = new Client();
        mockClient1.setId("client-1");
        mockClient1.setClientId("client-1");

        Client mockClient2 = new Client();
        mockClient2.setId("client-2");
        mockClient2.setClientId("client-2");

        Set<Client> mockClients = new HashSet<>();
        mockClients.add(mockClient1);
        mockClients.add(mockClient2);

        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(clientService.findByDomain(DOMAIN_ID)).thenReturn(mockClients);
        when(certificate.getId()).thenReturn(CERTIFICATE_ID);
        when(certificateService.findByDomain(DOMAIN_ID)).thenReturn(Collections.singletonList(certificate));
        when(identityProvider.getId()).thenReturn(IDP_ID);
        when(identityProviderService.findByDomain(DOMAIN_ID)).thenReturn(Collections.singletonList(identityProvider));
        when(role.getId()).thenReturn(ROLE_ID);
        when(roleService.findByDomain(DOMAIN_ID)).thenReturn(Collections.singleton(role));
        when(user.getId()).thenReturn(USER_ID);
        when(userService.findByDomain(DOMAIN_ID)).thenReturn(Collections.singleton(user));

        domainService.delete(DOMAIN_ID);
        verify(clientService, times(2)).delete(anyString());
        verify(certificateService, times(1)).delete(CERTIFICATE_ID);
        verify(identityProviderService, times(1)).delete(IDP_ID);
        verify(roleService, times(1)).delete(ROLE_ID);
        verify(userService, times(1)).delete(USER_ID);
    }

    @Test
    public void shouldDeleteWithoutRelatedData() throws TechnicalException {
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(clientService.findByDomain(DOMAIN_ID)).thenReturn(Collections.emptySet());
        when(certificateService.findByDomain(DOMAIN_ID)).thenReturn(Collections.emptyList());
        when(identityProviderService.findByDomain(DOMAIN_ID)).thenReturn(Collections.emptyList());
        when(roleService.findByDomain(DOMAIN_ID)).thenReturn(Collections.emptySet());
        when(userService.findByDomain(DOMAIN_ID)).thenReturn(Collections.emptySet());

        domainService.delete(DOMAIN_ID);
        verify(clientService, never()).delete(anyString());
        verify(certificateService, never()).delete(anyString());
        verify(identityProviderService, never()).delete(anyString());
        verify(roleService, never()).delete(anyString());
        verify(userService, never()).delete(anyString());
    }

    @Test(expected = DomainDeleteMasterException.class)
    public void shouldNotDeleteMasterDomain() throws TechnicalException {
        when(domain.isMaster()).thenReturn(true);
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Optional.of(domain));

        domainService.delete(DOMAIN_ID);
    }
}

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
package io.gravitee.am.service;

import io.gravitee.am.model.*;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.impl.ClientServiceImpl;
import io.gravitee.am.service.model.NewClient;
import io.gravitee.am.service.model.PatchClient;
import io.gravitee.am.service.model.TotalClient;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientServiceTest {

    @InjectMocks
    private ClientService clientService = new ClientServiceImpl();

    @Mock
    private DomainService domainService;

    @Mock
    private ScopeService scopeService;

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private FormService formService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private AuditService auditService;

    @Mock
    private EventService eventService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(new Client()));
        TestObserver testObserver = clientService.findById("my-client").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingClient() {
        when(clientRepository.findById("my-client")).thenReturn(Maybe.empty());
        TestObserver testObserver = clientService.findById("my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(clientRepository.findById("my-client")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        clientService.findById("my-client").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainAndClientId() {
        when(clientRepository.findByClientIdAndDomain("my-client", DOMAIN)).thenReturn(Maybe.just(new Client()));
        TestObserver testObserver = clientService.findByDomainAndClientId(DOMAIN, "my-client").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void findByClientIdAndDomain() {
        when(clientRepository.findByClientIdAndDomain("my-client", DOMAIN)).thenReturn(Maybe.empty());
        TestObserver testObserver = clientService.findByDomainAndClientId(DOMAIN, "my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindByDomainAndClientId_technicalException() {
        when(clientRepository.findByClientIdAndDomain("my-client", DOMAIN)).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        clientService.findByDomainAndClientId(DOMAIN, "my-client").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(clientRepository.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.singleton(new Client())));
        TestObserver<Set<Client>> testObserver = clientService.findByDomain(DOMAIN).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.size() == 1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(clientRepository.findByDomain(DOMAIN)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findByDomain(DOMAIN).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainPagination() {
        Page pageClients = new Page(Collections.singleton(new Client()), 1 , 1);
        when(clientRepository.findByDomain(DOMAIN, 1 , 1)).thenReturn(Single.just(pageClients));
        TestObserver<Page<Client>> testObserver = clientService.findByDomain(DOMAIN, 1, 1).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.getData().size() == 1);
    }

    @Test
    public void shouldFindByDomainPagination_technicalException() {
        when(clientRepository.findByDomain(DOMAIN, 1 , 1)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findByDomain(DOMAIN, 1 , 1).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByIdentityProvider() {
        when(clientRepository.findByIdentityProvider("client-idp")).thenReturn(Single.just(Collections.singleton(new Client())));
        TestObserver<Set<Client>> testObserver = clientService.findByIdentityProvider("client-idp").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.size() == 1);
    }

    @Test
    public void shouldFindByIdentityProvider_technicalException() {
        when(clientRepository.findByIdentityProvider("client-idp")).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findByIdentityProvider("client-idp").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByCertificate() {
        when(clientRepository.findByCertificate("client-certificate")).thenReturn(Single.just(Collections.singleton(new Client())));
        TestObserver<Set<Client>> testObserver = clientService.findByCertificate("client-certificate").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.size() == 1);
    }

    @Test
    public void shouldFindByCertificate_technicalException() {
        when(clientRepository.findByCertificate("client-certificate")).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findByCertificate("client-certificate").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByExtensionGrant() {
        when(clientRepository.findByDomainAndExtensionGrant(DOMAIN, "client-extension-grant")).thenReturn(Single.just(Collections.singleton(new Client())));
        TestObserver<Set<Client>> testObserver = clientService.findByDomainAndExtensionGrant(DOMAIN, "client-extension-grant").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.size() == 1);
    }

    @Test
    public void shouldFindByExtensionGrant_technicalException() {
        when(clientRepository.findByDomainAndExtensionGrant(DOMAIN, "client-extension-grant")).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findByDomainAndExtensionGrant(DOMAIN, "client-extension-grant").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindAll() {
        when(clientRepository.findAll()).thenReturn(Single.just(Collections.singleton(new Client())));
        TestObserver<Set<Client>> testObserver = clientService.findAll().test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.size() == 1);
    }

    @Test
    public void shouldFindAll_technicalException() {
        when(clientRepository.findAll()).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findAll().subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindAllPagination() {
        Page pageClients = new Page(Collections.singleton(new Client()), 1 , 1);
        when(clientRepository.findAll(1 , 1)).thenReturn(Single.just(pageClients));
        TestObserver<Page<Client>> testObserver = clientService.findAll(1, 1).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.getData().size() == 1);
    }

    @Test
    public void shouldFindAllPagination_technicalException() {
        when(clientRepository.findAll(1 , 1)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findAll(1 , 1).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalClientsByDomain() {
        when(clientRepository.countByDomain(DOMAIN)).thenReturn(Single.just(1l));
        TestObserver<TotalClient> testObserver = clientService.findTotalClientsByDomain(DOMAIN).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(totalClient -> totalClient.getTotalClients() == 1l);
    }

    @Test
    public void shouldFindTotalClientsByDomain_technicalException() {
        when(clientRepository.countByDomain(DOMAIN)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findTotalClientsByDomain(DOMAIN).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalClients() {
        when(clientRepository.count()).thenReturn(Single.just(1l));
        TestObserver<TotalClient> testObserver = clientService.findTotalClients().test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(totalClient -> totalClient.getTotalClients() == 1l);
    }

    @Test
    public void shouldFindTotalClients_technicalException() {
        when(clientRepository.count()).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findTotalClients().subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewClient newClient = Mockito.mock(NewClient.class);
        Client createClient = Mockito.mock(Client.class);

        when(newClient.getClientId()).thenReturn("my-client");
        when(clientRepository.findByClientIdAndDomain("my-client", DOMAIN)).thenReturn(Maybe.empty());
        when(clientRepository.create(any(Client.class))).thenReturn(Single.just(createClient));
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN,null)).thenReturn(Single.just(true));

        TestObserver testObserver = clientService.create(DOMAIN, newClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(clientRepository, times(1)).findByClientIdAndDomain(anyString(), anyString());
        verify(clientRepository, times(1)).create(any(Client.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewClient newClient = Mockito.mock(NewClient.class);
        when(newClient.getClientId()).thenReturn("my-client");
        when(clientRepository.findByClientIdAndDomain( "my-client", DOMAIN)).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver<Client> testObserver = new TestObserver<>();
        clientService.create(DOMAIN, newClient).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, never()).create(any(Client.class));
    }

    @Test
    public void shouldCreate2_technicalException() {
        NewClient newClient = Mockito.mock(NewClient.class);
        when(newClient.getClientId()).thenReturn("my-client");
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(scopeService.validateScope(DOMAIN,null)).thenReturn(Single.just(true));
        when(clientRepository.findByClientIdAndDomain("my-client", DOMAIN)).thenReturn(Maybe.empty());
        when(clientRepository.create(any(Client.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver<Client> testObserver = new TestObserver<>();
        clientService.create(DOMAIN, newClient).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findByClientIdAndDomain(anyString(), anyString());
    }

    @Test
    public void shouldCreate_clientAlreadyExists() {
        NewClient newClient = Mockito.mock(NewClient.class);
        when(newClient.getClientId()).thenReturn("my-client");
        when(clientRepository.findByClientIdAndDomain("my-client", DOMAIN)).thenReturn(Maybe.just(new Client()));

        TestObserver<Client> testObserver = new TestObserver<>();
        clientService.create(DOMAIN, newClient).subscribe(testObserver);

        testObserver.assertError(ClientAlreadyExistsException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findByClientIdAndDomain(anyString(), anyString());
        verify(clientRepository, never()).create(any(Client.class));
    }

    @Test
    public void create_failWithNoDomain() {
        TestObserver testObserver = clientService.create(new Client()).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void create_implicit_invalidRedirectUri() {
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));

        Client toCreate = new Client();
        toCreate.setDomain(DOMAIN);
        toCreate.setAuthorizedGrantTypes(Arrays.asList("implicit"));
        toCreate.setResponseTypes(Arrays.asList("token"));
        TestObserver testObserver = clientService.create(toCreate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRedirectUriException.class);
    }

    @Test
    public void create_generateUuidAsClientId() {
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN,null)).thenReturn(Single.just(true));
        when(clientRepository.create(any(Client.class))).thenReturn(Single.just(new Client()));

        Client toCreate = new Client();
        toCreate.setDomain(DOMAIN);
        toCreate.setRedirectUris(Arrays.asList("https://callback"));
        TestObserver testObserver = clientService.create(toCreate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository, times(1)).create(captor.capture());
        Assert.assertTrue("client_id must be generated",captor.getValue().getClientId()!=null);
        Assert.assertTrue("client_secret must be generated",captor.getValue().getClientSecret()!=null);
    }

    @Test
    public void shouldPatch_keepingClientRedirectUris() {
        PatchClient patchClient = new PatchClient();
        patchClient.setIdentities(Optional.of(new HashSet<>(Arrays.asList("id1", "id2"))));
        patchClient.setAuthorizedGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        Client toPatch = new Client();
        toPatch.setDomain(DOMAIN);
        toPatch.setRedirectUris(Arrays.asList("https://callback"));
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(toPatch));
        when(identityProviderService.findById("id1")).thenReturn(Maybe.just(new IdentityProvider()));
        when(identityProviderService.findById("id2")).thenReturn(Maybe.just(new IdentityProvider()));
        when(clientRepository.update(any(Client.class))).thenReturn(Single.just(new Client()));
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN,null)).thenReturn(Single.just(true));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(clientRepository, times(1)).findById(anyString());
        verify(identityProviderService, times(2)).findById(anyString());
        verify(clientRepository, times(1)).update(any(Client.class));
    }

    @Test
    public void shouldUpdate_implicit_invalidRedirectUri() {
        Client client = new Client();
        client.setDomain(DOMAIN);
        PatchClient patchClient = new PatchClient();
        patchClient.setAuthorizedGrantTypes(Optional.of(Arrays.asList("implicit")));
        patchClient.setResponseTypes(Optional.of(Arrays.asList("token")));

        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(client));
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRedirectUriException.class);

        verify(clientRepository, times(1)).findById(anyString());
    }

    @Test
    public void shouldUpdate_technicalException() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        when(clientRepository.findById("my-client")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, never()).update(any(Client.class));
    }

    @Test
    public void shouldUpdate2_technicalException() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        when(clientRepository.findById("my-client")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, never()).update(any(Client.class));
    }

    @Test
    public void shouldUpdate3_technicalException() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        when(patchClient.getIdentities()).thenReturn(Optional.of(new HashSet<>(Arrays.asList("id1", "id2"))));
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(new Client()));
        when(identityProviderService.findById(anyString())).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, never()).update(any(Client.class));
    }

    @Test
    public void shouldUpdate_clientNotFound() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        when(clientRepository.findById("my-client")).thenReturn(Maybe.empty());

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();

        testObserver.assertError(ClientNotFoundException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, never()).update(any(Client.class));
    }

    @Test
    public void update_failWithNoDomain() {
        TestObserver testObserver = clientService.update(new Client()).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void update_implicitGrant_invalidRedirectUri() {

        when(clientRepository.findById(any())).thenReturn(Maybe.just(new Client()));
        when(domainService.findById(any())).thenReturn(Maybe.just(new Domain()));

        Client toUpdate = new Client();
        toUpdate.setAuthorizedGrantTypes(Arrays.asList("implicit"));
        toUpdate.setResponseTypes(Arrays.asList("token"));
        toUpdate.setDomain(DOMAIN);
        TestObserver testObserver = clientService.update(toUpdate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRedirectUriException.class);

        verify(clientRepository, times(1)).findById(any());
    }

    @Test
    public void update_defaultGrant_ok() {
        when(clientRepository.findById(any())).thenReturn(Maybe.just(new Client()));
        when(clientRepository.update(any(Client.class))).thenReturn(Single.just(new Client()));
        when(domainService.findById(any())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(any(),any())).thenReturn(Single.just(true));

        Client toUpdate = new Client();
        toUpdate.setDomain(DOMAIN);
        toUpdate.setRedirectUris(Arrays.asList("https://callback"));
        TestObserver testObserver = clientService.update(toUpdate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(clientRepository, times(1)).findById(any());
        verify(clientRepository, times(1)).update(any(Client.class));
    }

    @Test
    public void update_clientCredentials_ok() {
        when(clientRepository.findById(any())).thenReturn(Maybe.just(new Client()));
        when(clientRepository.update(any(Client.class))).thenReturn(Single.just(new Client()));
        when(domainService.findById(any())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(any(),any())).thenReturn(Single.just(true));

        Client toUpdate = new Client();
        toUpdate.setDomain(DOMAIN);
        toUpdate.setAuthorizedGrantTypes(Arrays.asList("client_credentials"));
        toUpdate.setResponseTypes(Arrays.asList());
        TestObserver testObserver = clientService.update(toUpdate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(clientRepository, times(1)).findById(any());
        verify(clientRepository, times(1)).update(any(Client.class));
    }

    @Test
    public void shouldPatch() {
        Client client = new Client();
        client.setDomain(DOMAIN);

        PatchClient patchClient = new PatchClient();
        patchClient.setIdentities(Optional.of(new HashSet<>(Arrays.asList("id1", "id2"))));
        patchClient.setAuthorizedGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        patchClient.setRedirectUris(Optional.of(Arrays.asList("https://callback")));

        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(client));
        when(identityProviderService.findById("id1")).thenReturn(Maybe.just(new IdentityProvider()));
        when(identityProviderService.findById("id2")).thenReturn(Maybe.just(new IdentityProvider()));
        when(clientRepository.update(any(Client.class))).thenReturn(Single.just(new Client()));
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN,null)).thenReturn(Single.just(true));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(clientRepository, times(1)).findById(anyString());
        verify(identityProviderService, times(2)).findById(anyString());
        verify(clientRepository, times(1)).update(any(Client.class));
    }

    @Test
    public void shouldPatch_mobileApplication() {
        Client client = new Client();
        client.setDomain(DOMAIN);

        PatchClient patchClient = new PatchClient();
        patchClient.setAuthorizedGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        patchClient.setRedirectUris(Optional.of(Arrays.asList("com.gravitee.app://callback")));

        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(client));
        when(clientRepository.update(any(Client.class))).thenReturn(Single.just(new Client()));
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN,null)).thenReturn(Single.just(true));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, times(1)).update(any(Client.class));
    }

    @Test
    public void shouldPatch_mobileApplication_googleCase() {
        Client client = new Client();
        client.setDomain(DOMAIN);

        PatchClient patchClient = new PatchClient();
        patchClient.setAuthorizedGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        patchClient.setRedirectUris(Optional.of(Arrays.asList("com.google.app:/callback")));

        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(client));
        when(clientRepository.update(any(Client.class))).thenReturn(Single.just(new Client()));
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN,null)).thenReturn(Single.just(true));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, times(1)).update(any(Client.class));
    }

    @Test
    public void shouldDelete() {
        Client existingClient = Mockito.mock(Client.class);
        when(existingClient.getId()).thenReturn("my-client");
        when(existingClient.getDomain()).thenReturn("my-domain");
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(existingClient));
        when(clientRepository.delete("my-client")).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        Form form = new Form();
        form.setId("form-id");
        when(formService.findByDomainAndClient("my-domain", "my-client")).thenReturn(Single.just(Collections.singletonList(form)));
        when(formService.delete(form.getId())).thenReturn(Completable.complete());
        Email email = new Email();
        email.setId("email-id");
        when(emailTemplateService.findByDomainAndClient("my-domain", "my-client"))
                .thenReturn(Single.just(Collections.singletonList(email)));
        when(emailTemplateService.delete(email.getId())).thenReturn(Completable.complete());

        TestObserver testObserver = clientService.delete("my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(clientRepository, times(1)).delete("my-client");
        verify(formService, times(1)).delete(anyString());
        verify(emailTemplateService, times(1)).delete(anyString());
    }

    @Test
    public void shouldDelete_withoutRelatedData() {
        Client existingClient = Mockito.mock(Client.class);
        when(existingClient.getDomain()).thenReturn("my-domain");
        when(existingClient.getId()).thenReturn("my-client");
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(existingClient));
        when(clientRepository.delete("my-client")).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(formService.findByDomainAndClient("my-domain", "my-client")).thenReturn(Single.just(Collections.emptyList()));
        when(emailTemplateService.findByDomainAndClient("my-domain", "my-client")).thenReturn(Single.just(Collections.emptyList()));

        TestObserver testObserver = clientService.delete("my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(clientRepository, times(1)).delete("my-client");
        verify(formService, never()).delete(anyString());
        verify(emailTemplateService, never()).delete(anyString());
    }

    @Test
    public void shouldDelete_technicalException() {
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(new Client()));
        when(clientRepository.delete(anyString())).thenReturn(Completable.error(TechnicalException::new));

        TestObserver testObserver = clientService.delete("my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete2_technicalException() {
        when(clientRepository.findById("my-client")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = clientService.delete("my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_clientNotFound() {
        when(clientRepository.findById("my-client")).thenReturn(Maybe.empty());

        TestObserver testObserver = clientService.delete("my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(ClientNotFoundException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, never()).delete("my-client");
    }

    @Test
    public void validateClientMetadata_invalidRedirectUriException_noSchemeUri() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        Client client = new Client();
        client.setDomain(DOMAIN);
        client.setRedirectUris(Arrays.asList("noscheme"));
        when(patchClient.patch(any(), anyBoolean())).thenReturn(client);
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(new Client()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, never()).update(any(Client.class));
    }

    @Test
    public void validateClientMetadata_invalidRedirectUriException_malformedUri() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        Client client = new Client();
        client.setDomain(DOMAIN);
        client.setRedirectUris(Arrays.asList("malformed:uri:exception"));
        when(patchClient.patch(any(), anyBoolean())).thenReturn(client);
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(new Client()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, never()).update(any(Client.class));
    }

    @Test
    public void validateClientMetadata_invalidRedirectUriException_forbidLocalhost() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        Client client = new Client();
        client.setDomain(DOMAIN);
        client.setRedirectUris(Arrays.asList("http://localhost/callback"));
        when(patchClient.patch(any(), anyBoolean())).thenReturn(client);
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(new Client()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, never()).update(any(Client.class));
    }

    @Test
    public void validateClientMetadata_invalidRedirectUriException_forbidHttp() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        Client client = new Client();
        client.setDomain(DOMAIN);
        client.setRedirectUris(Arrays.asList("http://gravitee.io/callback"));
        when(patchClient.patch(any(), anyBoolean())).thenReturn(client);
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(new Client()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, never()).update(any(Client.class));
    }

    @Test
    public void validateClientMetadata_invalidRedirectUriException_forbidWildcard() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        Client client = new Client();
        client.setDomain(DOMAIN);
        client.setRedirectUris(Arrays.asList("https://gravitee.io/*"));
        when(patchClient.patch(any(), anyBoolean())).thenReturn(client);
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(new Client()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, never()).update(any(Client.class));
    }

    @Test
    public void validateClientMetadata_invalidRedirectUriException_fragment() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        Client client = new Client();
        client.setDomain(DOMAIN);
        client.setRedirectUris(Arrays.asList("https://gravitee.io#fragment"));
        when(patchClient.patch(any(), anyBoolean())).thenReturn(client);
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(new Client()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, never()).update(any(Client.class));
    }

    @Test
    public void validateClientMetadata_invalidClientMetadataException_unknownScope() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        Client client = new Client();
        client.setDomain(DOMAIN);
        client.setRedirectUris(Arrays.asList("https://callback"));
        client.setScopes(Collections.emptyList());
        when(patchClient.patch(any(), anyBoolean())).thenReturn(client);
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(new Client()));
        when(scopeService.validateScope(DOMAIN, Collections.emptyList())).thenReturn(Single.just(false));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, never()).update(any(Client.class));
    }

    @Test
    public void validateClientMetadata_validMetadata() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        Client client = new Client();
        client.setDomain(DOMAIN);
        client.setRedirectUris(Arrays.asList("https://gravitee.io/callback"));
        client.setScopes(Collections.emptyList());
        when(patchClient.patch(any(), anyBoolean())).thenReturn(client);
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(new Client()));
        when(clientRepository.update(any(Client.class))).thenReturn(Single.just(new Client()));
        when(scopeService.validateScope(DOMAIN,Collections.emptyList())).thenReturn(Single.just(true));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, times(1)).update(any(Client.class));
    }

    @Test
    public void shouldRenewSecret() {
        Client client = new Client();
        client.setDomain(DOMAIN);

        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(clientRepository.findById("my-client")).thenReturn(Maybe.just(client));
        when(clientRepository.update(any(Client.class))).thenReturn(Single.just(new Client()));

        TestObserver testObserver = clientService.renewClientSecret(DOMAIN, "my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(clientRepository, times(1)).findById(anyString());
        verify(clientRepository, times(1)).update(any(Client.class));
    }

    @Test
    public void shouldRenewSecret_clientNotFound() {
        when(clientRepository.findById("my-client")).thenReturn(Maybe.empty());

        TestObserver testObserver = clientService.renewClientSecret(DOMAIN, "my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(ClientNotFoundException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, never()).update(any());
    }

    @Test
    public void shouldRenewSecret_technicalException() {
        when(clientRepository.findById("my-client")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = clientService.renewClientSecret(DOMAIN, "my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(clientRepository, never()).update(any());
    }
}

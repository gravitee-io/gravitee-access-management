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

import static io.gravitee.am.service.impl.ClientServiceImpl.DEFAULT_CLIENT_NAME;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.impl.ClientServiceImpl;
import io.gravitee.am.service.model.NewClient;
import io.gravitee.am.service.model.PatchClient;
import io.gravitee.am.service.model.TotalClient;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.util.*;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

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
    private ApplicationService applicationService;

    private static final String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(applicationService.findById("my-client")).thenReturn(Maybe.just(new Application()));
        TestObserver testObserver = clientService.findById("my-client").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingClient() {
        when(applicationService.findById("my-client")).thenReturn(Maybe.empty());
        TestObserver testObserver = clientService.findById("my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(applicationService.findById("my-client")).thenReturn(Maybe.error(TechnicalManagementException::new));
        TestObserver testObserver = new TestObserver();
        clientService.findById("my-client").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainAndClientId() {
        when(applicationService.findByDomainAndClientId(DOMAIN, "my-client")).thenReturn(Maybe.just(new Application()));
        TestObserver testObserver = clientService.findByDomainAndClientId(DOMAIN, "my-client").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void findByClientIdAndDomain() {
        when(applicationService.findByDomainAndClientId(DOMAIN, "my-client")).thenReturn(Maybe.empty());
        TestObserver testObserver = clientService.findByDomainAndClientId(DOMAIN, "my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindByDomainAndClientId_technicalException() {
        when(applicationService.findByDomainAndClientId(DOMAIN, "my-client")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        clientService.findByDomainAndClientId(DOMAIN, "my-client").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(applicationService.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.singleton(new Application())));
        TestObserver<Set<Client>> testObserver = clientService.findByDomain(DOMAIN).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.size() == 1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(applicationService.findByDomain(DOMAIN)).thenReturn(Single.error(TechnicalManagementException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findByDomain(DOMAIN).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainPagination() {
        Page pageClients = new Page(Collections.singleton(new Application()), 1, 1);
        when(applicationService.findByDomain(DOMAIN, 1, 1)).thenReturn(Single.just(pageClients));
        TestObserver<Page<Client>> testObserver = clientService.findByDomain(DOMAIN, 1, 1).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.getData().size() == 1);
    }

    @Test
    public void shouldFindByDomainPagination_technicalException() {
        when(applicationService.findByDomain(DOMAIN, 1, 1)).thenReturn(Single.error(TechnicalManagementException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findByDomain(DOMAIN, 1, 1).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindAll() {
        when(applicationService.findAll()).thenReturn(Single.just(Collections.singleton(new Application())));
        TestObserver<Set<Client>> testObserver = clientService.findAll().test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.size() == 1);
    }

    @Test
    public void shouldFindAll_technicalException() {
        when(applicationService.findAll()).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findAll().subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindAllPagination() {
        Page pageClients = new Page(Collections.singleton(new Application()), 1, 1);
        when(applicationService.findAll(1, 1)).thenReturn(Single.just(pageClients));
        TestObserver<Page<Client>> testObserver = clientService.findAll(1, 1).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.getData().size() == 1);
    }

    @Test
    public void shouldFindAllPagination_technicalException() {
        when(applicationService.findAll(1, 1)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findAll(1, 1).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalClientsByDomain() {
        when(applicationService.countByDomain(DOMAIN)).thenReturn(Single.just(1l));
        TestObserver<TotalClient> testObserver = clientService.findTotalClientsByDomain(DOMAIN).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(totalClient -> totalClient.getTotalClients() == 1l);
    }

    @Test
    public void shouldFindTotalClientsByDomain_technicalException() {
        when(applicationService.countByDomain(DOMAIN)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findTotalClientsByDomain(DOMAIN).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalClients() {
        when(applicationService.count()).thenReturn(Single.just(1l));
        TestObserver<TotalClient> testObserver = clientService.findTotalClients().test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(totalClient -> totalClient.getTotalClients() == 1l);
    }

    @Test
    public void shouldFindTotalClients_technicalException() {
        when(applicationService.count()).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        clientService.findTotalClients().subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewClient newClient = Mockito.mock(NewClient.class);
        Application createApplication = Mockito.mock(Application.class);

        when(newClient.getClientId()).thenReturn("my-client");
        when(applicationService.create(any(Application.class))).thenReturn(Single.just(createApplication));

        TestObserver<Client> testObserver = clientService.create(DOMAIN, newClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).create(argThat(application -> application.getName().equals(newClient.getClientId())));
    }

    @Test
    public void shouldCreate_withoutClientId() {
        NewClient newClient = Mockito.mock(NewClient.class);
        Application createApplication = Mockito.mock(Application.class);

        when(newClient.getClientId()).thenReturn(null);
        when(applicationService.create(any(Application.class))).thenReturn(Single.just(createApplication));

        TestObserver<Client> testObserver = clientService.create(DOMAIN, newClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Check the application name is equals to 'Unknown Client' (to avoid having generated uuid as application name).
        verify(applicationService, times(1)).create(argThat(application -> application.getName().equals(DEFAULT_CLIENT_NAME)));
    }

    @Test
    public void shouldCreate_withClientName() {
        String customClientName = "My custom client name";

        NewClient newClient = Mockito.mock(NewClient.class);
        Application createApplication = Mockito.mock(Application.class);

        when(newClient.getClientId()).thenReturn(null);
        when(newClient.getClientName()).thenReturn(customClientName);
        when(applicationService.create(any(Application.class))).thenReturn(Single.just(createApplication));

        TestObserver<Client> testObserver = clientService.create(DOMAIN, newClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Check the application name is customized when client name has been specified.
        verify(applicationService, times(1)).create(argThat(application -> application.getName().equals(customClientName)));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewClient newClient = Mockito.mock(NewClient.class);
        when(newClient.getClientId()).thenReturn("my-client");
        when(applicationService.create(any())).thenReturn(Single.error(TechnicalManagementException::new));

        TestObserver<Client> testObserver = new TestObserver<>();
        clientService.create(DOMAIN, newClient).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(applicationService, times(1)).create(any(Application.class));
    }

    @Test
    public void shouldCreate_clientAlreadyExists() {
        NewClient newClient = Mockito.mock(NewClient.class);
        when(newClient.getClientId()).thenReturn("my-client");
        when(applicationService.create(any())).thenReturn(Single.error(new ClientAlreadyExistsException("my-client", DOMAIN)));

        TestObserver<Client> testObserver = new TestObserver<>();
        clientService.create(DOMAIN, newClient).subscribe(testObserver);

        testObserver.assertError(ClientAlreadyExistsException.class);
        testObserver.assertNotComplete();

        verify(applicationService, times(1)).create(any(Application.class));
    }

    @Test
    public void create_failWithNoDomain() {
        TestObserver testObserver = clientService.create(new Client()).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void create_implicit_invalidRedirectUri() {
        Client toCreate = new Client();
        toCreate.setDomain(DOMAIN);
        toCreate.setAuthorizedGrantTypes(Arrays.asList("implicit"));
        toCreate.setResponseTypes(Arrays.asList("token"));
        when(applicationService.create(any())).thenReturn(Single.error(new InvalidRedirectUriException()));
        TestObserver testObserver = clientService.create(toCreate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRedirectUriException.class);
    }

    @Test
    public void create_generateUuidAsClientId() {
        when(applicationService.create(any(Application.class))).thenReturn(Single.just(new Application()));

        Client toCreate = new Client();
        toCreate.setDomain(DOMAIN);
        toCreate.setRedirectUris(Arrays.asList("https://callback"));
        TestObserver testObserver = clientService.create(toCreate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationService, times(1)).create(captor.capture());
        Assert.assertTrue("client_id must be generated", captor.getValue().getSettings().getOauth().getClientId() != null);
        Assert.assertTrue("client_secret must be generated", captor.getValue().getSettings().getOauth().getClientSecret() != null);
    }

    @Test
    public void shouldPatch_keepingClientRedirectUris() {
        PatchClient patchClient = new PatchClient();
        patchClient.setIdentities(Optional.of(new HashSet<>(Arrays.asList("id1", "id2"))));
        patchClient.setAuthorizedGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        Application toPatch = new Application();
        ApplicationSettings applicationSettings = new ApplicationSettings();
        ApplicationOAuthSettings applicationOAuthSettings = new ApplicationOAuthSettings();
        applicationOAuthSettings.setRedirectUris(Arrays.asList("https://callback"));
        applicationSettings.setOauth(applicationOAuthSettings);
        toPatch.setDomain(DOMAIN);
        toPatch.setSettings(applicationSettings);
        when(applicationService.findById("my-client")).thenReturn(Maybe.just(toPatch));
        when(applicationService.update(any(Application.class))).thenReturn(Single.just(new Application()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).findById(anyString());
        verify(applicationService, times(1)).update(any(Application.class));
    }

    @Test
    public void shouldUpdate_implicit_invalidRedirectUri() {
        Application client = new Application();
        client.setDomain(DOMAIN);
        PatchClient patchClient = new PatchClient();
        patchClient.setAuthorizedGrantTypes(Optional.of(Arrays.asList("implicit")));
        patchClient.setResponseTypes(Optional.of(Arrays.asList("token")));

        when(applicationService.findById(any())).thenReturn(Maybe.just(client));
        when(applicationService.update(any(Application.class))).thenReturn(Single.error(new InvalidRedirectUriException()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRedirectUriException.class);

        verify(applicationService, times(1)).findById(anyString());
    }

    @Test
    public void shouldUpdate_technicalException() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        when(applicationService.findById("my-client")).thenReturn(Maybe.error(TechnicalManagementException::new));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(applicationService, times(1)).findById(anyString());
        verify(applicationService, never()).update(any(Application.class));
    }

    @Test
    public void shouldUpdate_clientNotFound() {
        PatchClient patchClient = Mockito.mock(PatchClient.class);
        when(applicationService.findById("my-client")).thenReturn(Maybe.empty());

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();

        testObserver.assertError(ClientNotFoundException.class);
        testObserver.assertNotComplete();

        verify(applicationService, times(1)).findById(anyString());
        verify(applicationService, never()).update(any(Application.class));
    }

    @Test
    public void update_failWithNoDomain() {
        TestObserver testObserver = clientService.update(new Client()).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void update_implicitGrant_invalidRedirectUri() {
        when(applicationService.update(any(Application.class))).thenReturn(Single.error(new InvalidRedirectUriException()));

        Client toUpdate = new Client();
        toUpdate.setAuthorizedGrantTypes(Arrays.asList("implicit"));
        toUpdate.setResponseTypes(Arrays.asList("token"));
        toUpdate.setDomain(DOMAIN);
        TestObserver testObserver = clientService.update(toUpdate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRedirectUriException.class);
    }

    @Test
    public void update_defaultGrant_ok() {
        when(applicationService.update(any(Application.class))).thenReturn(Single.just(new Application()));

        Client toUpdate = new Client();
        toUpdate.setDomain(DOMAIN);
        toUpdate.setRedirectUris(Arrays.asList("https://callback"));
        TestObserver testObserver = clientService.update(toUpdate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).update(any(Application.class));
    }

    @Test
    public void update_clientCredentials_ok() {
        when(applicationService.update(any(Application.class))).thenReturn(Single.just(new Application()));

        Client toUpdate = new Client();
        toUpdate.setDomain(DOMAIN);
        toUpdate.setAuthorizedGrantTypes(Arrays.asList("client_credentials"));
        toUpdate.setResponseTypes(Arrays.asList());
        TestObserver testObserver = clientService.update(toUpdate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).update(any(Application.class));
    }

    @Test
    public void shouldPatch() {
        Application client = new Application();
        client.setDomain(DOMAIN);

        PatchClient patchClient = new PatchClient();
        patchClient.setIdentities(Optional.of(new HashSet<>(Arrays.asList("id1", "id2"))));
        patchClient.setAuthorizedGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        patchClient.setRedirectUris(Optional.of(Arrays.asList("https://callback")));

        when(applicationService.findById("my-client")).thenReturn(Maybe.just(client));
        when(applicationService.update(any(Application.class))).thenReturn(Single.just(new Application()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).findById(anyString());
        verify(applicationService, times(1)).update(any(Application.class));
    }

    @Test
    public void shouldPatch_mobileApplication() {
        Application client = new Application();
        client.setDomain(DOMAIN);

        PatchClient patchClient = new PatchClient();
        patchClient.setAuthorizedGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        patchClient.setRedirectUris(Optional.of(Arrays.asList("com.gravitee.app://callback")));

        when(applicationService.findById("my-client")).thenReturn(Maybe.just(client));
        when(applicationService.update(any(Application.class))).thenReturn(Single.just(new Application()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).findById(anyString());
        verify(applicationService, times(1)).update(any(Application.class));
    }

    @Test
    public void shouldPatch_mobileApplication_googleCase() {
        Application client = new Application();
        client.setDomain(DOMAIN);

        PatchClient patchClient = new PatchClient();
        patchClient.setAuthorizedGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        patchClient.setRedirectUris(Optional.of(Arrays.asList("com.google.app:/callback")));

        when(applicationService.findById("my-client")).thenReturn(Maybe.just(client));
        when(applicationService.update(any(Application.class))).thenReturn(Single.just(new Application()));

        TestObserver testObserver = clientService.patch(DOMAIN, "my-client", patchClient).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).findById(anyString());
        verify(applicationService, times(1)).update(any(Application.class));
    }

    @Test
    public void shouldDelete() {
        Application existingClient = Mockito.mock(Application.class);
        when(applicationService.delete("my-client", null)).thenReturn(Completable.complete());
        Form form = new Form();
        form.setId("form-id");
        Email email = new Email();
        email.setId("email-id");

        TestObserver testObserver = clientService.delete("my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).delete("my-client", null);
    }

    @Test
    public void shouldDelete_withoutRelatedData() {
        Application existingClient = Mockito.mock(Application.class);
        when(applicationService.delete("my-client", null)).thenReturn(Completable.complete());

        TestObserver testObserver = clientService.delete("my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).delete("my-client", null);
    }

    @Test
    public void shouldDelete_technicalException() {
        when(applicationService.delete("my-client", null)).thenReturn(Completable.error(TechnicalManagementException::new));

        TestObserver testObserver = clientService.delete("my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_clientNotFound() {
        when(applicationService.delete("my-client", null)).thenReturn(Completable.error(new ClientNotFoundException("my-client")));

        TestObserver testObserver = clientService.delete("my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(ClientNotFoundException.class);
        testObserver.assertNotComplete();

        verify(applicationService, times(1)).delete("my-client", null);
    }

    @Test
    public void shouldRenewSecret() {
        Application client = new Application();
        client.setDomain(DOMAIN);

        when(applicationService.renewClientSecret(DOMAIN, "my-client", null)).thenReturn(Single.just(new Application()));

        TestObserver testObserver = clientService.renewClientSecret(DOMAIN, "my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).renewClientSecret(DOMAIN, "my-client", null);
    }

    @Test
    public void shouldRenewSecret_clientNotFound() {
        when(applicationService.renewClientSecret(DOMAIN, "my-client", null))
            .thenReturn(Single.error(new ClientNotFoundException("my-client")));

        TestObserver testObserver = clientService.renewClientSecret(DOMAIN, "my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(ClientNotFoundException.class);
        testObserver.assertNotComplete();

        verify(applicationService, never()).update(any());
    }

    @Test
    public void shouldRenewSecret_technicalException() {
        when(applicationService.renewClientSecret(DOMAIN, "my-client", null)).thenReturn(Single.error(TechnicalManagementException::new));

        TestObserver testObserver = clientService.renewClientSecret(DOMAIN, "my-client").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(applicationService, never()).update(any());
    }
}

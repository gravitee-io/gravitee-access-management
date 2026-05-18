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
package io.gravitee.am.gateway.handler.common.client;

import io.gravitee.am.gateway.handler.common.client.impl.ClientSyncServiceImpl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for ClientSyncServiceImpl repository fallback behavior.
 * Covers cache misses that trigger repository queries and error handling.
 *
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientSyncServiceRepositoryFallbackTest {

    @InjectMocks
    private ClientSyncService clientSyncService = new ClientSyncServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private ClientManager clientManager;

    @Mock
    private ApplicationRepository applicationRepository;

    @Before
    public void setUp() {
        lenient().when(domain.getId()).thenReturn("domainA");
        // Use lenient stub for entities since only findByDomainAndClientId tests use it
        lenient().when(clientManager.entities()).thenReturn(new HashSet<>());
    }

    // ===== findByDomainAndClientId repository fallback tests =====

    @Test
    public void findByDomainAndClientId_cacheMiss_repoFound_deploysAndReturnsClient() {
        Application app = buildEnabledApp("new-id", "domainA", "newClient");
        when(applicationRepository.findByDomainAndClientId("domainA", "newClient")).thenReturn(Maybe.just(app));

        TestObserver<Client> test = clientSyncService.findByDomainAndClientId("domainA", "newClient").test();

        test.assertComplete().assertNoErrors();
        test.assertValue(c -> "newClient".equals(c.getClientId()));
        verify(clientManager, times(1)).deploy(any(Client.class));
    }

    @Test
    public void findByDomainAndClientId_cacheMiss_repoNotFound_returnsEmpty() {
        when(applicationRepository.findByDomainAndClientId("domainA", "unknown")).thenReturn(Maybe.empty());

        TestObserver<Client> test = clientSyncService.findByDomainAndClientId("domainA", "unknown").test();

        test.assertComplete().assertNoErrors().assertNoValues();
        verify(clientManager, never()).deploy(any());
    }

    @Test
    public void findByDomainAndClientId_cacheMiss_repoFindsDisabledClient_returnsEmpty() {
        Application app = buildDisabledApp("dis-id", "domainA", "disabledClient");
        when(applicationRepository.findByDomainAndClientId("domainA", "disabledClient")).thenReturn(Maybe.just(app));

        TestObserver<Client> test = clientSyncService.findByDomainAndClientId("domainA", "disabledClient").test();

        test.assertComplete().assertNoErrors().assertNoValues();
        verify(clientManager, never()).deploy(any());
    }

    @Test
    public void findByDomainAndClientId_cacheMiss_repoThrowsException_returnsEmptyAndLogsError() {
        when(applicationRepository.findByDomainAndClientId("domainA", "errorClient"))
                .thenReturn(Maybe.error(new RuntimeException("Database connection failed")));

        TestObserver<Client> test = clientSyncService.findByDomainAndClientId("domainA", "errorClient").test();

        test.assertComplete().assertNoErrors().assertNoValues();
        verify(clientManager, never()).deploy(any());
    }

    @Test
    public void findByDomainAndClientId_cacheMiss_repoThrowsCheckedException_returnsEmptyAndLogsError() {
        when(applicationRepository.findByDomainAndClientId("domainA", "checkedErrorClient"))
                .thenReturn(Maybe.error(new Exception("Checked exception from repository")));

        TestObserver<Client> test = clientSyncService.findByDomainAndClientId("domainA", "checkedErrorClient").test();

        test.assertComplete().assertNoErrors().assertNoValues();
        verify(clientManager, never()).deploy(any());
    }

    // ===== findById repository fallback tests =====

    @Test
    public void findById_cacheMiss_repoFound_deploysAndReturnsClient() {
        when(clientManager.get(anyString())).thenReturn(null);
        Application app = buildEnabledApp("fresh-id", "domainA", "freshClient");
        when(applicationRepository.findById("fresh-id")).thenReturn(Maybe.just(app));

        TestObserver<Client> test = clientSyncService.findById("fresh-id").test();

        test.assertComplete().assertNoErrors();
        test.assertValue(c -> "freshClient".equals(c.getClientId()));
        verify(clientManager, times(1)).deploy(any(Client.class));
    }

    @Test
    public void findById_cacheMiss_repoNotFound_returnsEmpty() {
        when(clientManager.get("missing")).thenReturn(null);
        when(applicationRepository.findById("missing")).thenReturn(Maybe.empty());

        TestObserver<Client> test = clientSyncService.findById("missing").test();

        test.assertComplete().assertNoErrors().assertNoValues();
        verify(clientManager, never()).deploy(any());
    }

    @Test
    public void findById_cacheMiss_repoFindsDisabledClient_returnsEmpty() {
        when(clientManager.get("disabled-id")).thenReturn(null);
        Application app = buildDisabledApp("disabled-id", "domainA", "disabledClient");
        when(applicationRepository.findById("disabled-id")).thenReturn(Maybe.just(app));

        TestObserver<Client> test = clientSyncService.findById("disabled-id").test();

        test.assertComplete().assertNoErrors().assertNoValues();
        verify(clientManager, never()).deploy(any());
    }

    @Test
    public void findById_cacheMiss_repoThrowsException_returnsEmptyAndLogsError() {
        when(clientManager.get("error-id")).thenReturn(null);
        when(applicationRepository.findById("error-id"))
                .thenReturn(Maybe.error(new RuntimeException("Database error")));

        TestObserver<Client> test = clientSyncService.findById("error-id").test();

        test.assertComplete().assertNoErrors().assertNoValues();
        verify(clientManager, never()).deploy(any());
    }

    @Test
    public void findById_cacheMiss_repoThrowsIOException_returnsEmptyAndLogsError() {
        when(clientManager.get("io-error")).thenReturn(null);
        when(applicationRepository.findById("io-error"))
                .thenReturn(Maybe.error(new RuntimeException("I/O error communicating with database")));

        TestObserver<Client> test = clientSyncService.findById("io-error").test();

        test.assertComplete().assertNoErrors().assertNoValues();
        verify(clientManager, never()).deploy(any());
    }

    // ===== Helper methods =====

    private Application buildEnabledApp(String id, String domain, String clientId) {
        Application app = new Application();
        app.setId(id);
        app.setDomain(domain);
        app.setEnabled(true);
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientId(clientId);
        settings.setOauth(oauth);
        app.setSettings(settings);
        return app;
    }

    private Application buildDisabledApp(String id, String domain, String clientId) {
        Application app = buildEnabledApp(id, domain, clientId);
        app.setEnabled(false);
        return app;
    }
}

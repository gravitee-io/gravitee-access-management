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
package io.gravitee.am.gateway.handler.aauth.service.registry;

import io.gravitee.am.gateway.handler.aauth.service.AgentMetadata;
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.aauth.AAuthSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;

import java.util.List;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AAuthAgentRegistryImplTest {

    private static final String DOMAIN_ID = "test-domain";
    private static final String AGENT_URL = "http://agent.example";

    @Mock
    private ApplicationService applicationService;

    @Mock
    private AgentMetadataFetcher metadataFetcher;

    private AAuthAgentRegistryImpl registry;

    @Before
    public void setUp() {
        var domain = new Domain();
        domain.setId(DOMAIN_ID);
        registry = new AAuthAgentRegistryImpl(applicationService, metadataFetcher, domain);
    }

    @Test
    public void shouldReturnExistingApplication() {
        var existing = new Application();
        existing.setId("app-123");
        existing.setType(ApplicationType.AAUTH_AGENT);

        when(applicationService.findByDomainAndClientId(DOMAIN_ID, AGENT_URL))
                .thenReturn(Maybe.just(existing));

        var result = registry.resolveOrCreate(
                verificationWith("jwks_uri", AGENT_URL), DOMAIN_ID).blockingGet();

        assertNotNull(result);
        assertEquals("app-123", result.getId());
        verify(applicationService, never()).create(any(Domain.class), any(Application.class));
    }

    @Test
    public void shouldAutoCreateApplication_onFirstContact() throws Exception {
        when(applicationService.findByDomainAndClientId(DOMAIN_ID, AGENT_URL))
                .thenReturn(Maybe.empty());

        var metadata = new AgentMetadata(AGENT_URL, AGENT_URL + "/jwks.json",
                "Test Agent", null, null, null, null, false, null, null);
        when(metadataFetcher.fetchMetadata(AGENT_URL)).thenReturn(metadata);

        var created = new Application();
        created.setId("new-app");
        when(applicationService.create(any(Domain.class), any(Application.class)))
                .thenReturn(Single.just(created));

        var result = registry.resolveOrCreate(
                verificationWith("jwks_uri", AGENT_URL), DOMAIN_ID).blockingGet();

        assertNotNull(result);
        assertEquals("new-app", result.getId());

        var captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationService).create(any(Domain.class), captor.capture());

        var app = captor.getValue();
        assertEquals(ApplicationType.AAUTH_AGENT, app.getType());
        assertEquals(DOMAIN_ID, app.getDomain());
        assertEquals("Test Agent", app.getName());
        assertEquals(AGENT_URL, app.getSettings().getOauth().getClientId());
        assertNotNull(app.getMetadata().get("aauth.metadataUrl"));
    }

    @Test
    public void shouldReturnEmpty_forPseudonymousRequest() {
        var result = registry.resolveOrCreate(
                verificationWith("hwk", null), DOMAIN_ID).blockingGet();

        assertNull(result);
        verify(applicationService, never()).findByDomainAndClientId(anyString(), anyString());
    }

    @Test
    public void shouldUseMetadataUrlAsFallbackName_whenClientNameAbsent() throws Exception {
        when(applicationService.findByDomainAndClientId(DOMAIN_ID, AGENT_URL))
                .thenReturn(Maybe.empty());

        var metadata = new AgentMetadata(AGENT_URL, AGENT_URL + "/jwks.json",
                null, null, null, null, null, false, null, null);
        when(metadataFetcher.fetchMetadata(AGENT_URL)).thenReturn(metadata);

        var created = new Application();
        when(applicationService.create(any(Domain.class), any(Application.class)))
                .thenReturn(Single.just(created));

        registry.resolveOrCreate(verificationWith("jwks_uri", AGENT_URL), DOMAIN_ID).blockingGet();

        var captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationService).create(any(Domain.class), captor.capture());
        assertEquals(AGENT_URL, captor.getValue().getName());
    }

    @Test
    public void shouldAssignDefaultIdPs_whenAutoCreating() throws Exception {
        // Set up domain with default IdPs
        var domain = new Domain();
        domain.setId(DOMAIN_ID);
        var aauth = new AAuthSettings();
        aauth.setDefaultIdentityProviders(List.of("idp-1", "idp-2"));
        domain.setAauth(aauth);
        registry = new AAuthAgentRegistryImpl(applicationService, metadataFetcher, domain);

        when(applicationService.findByDomainAndClientId(DOMAIN_ID, AGENT_URL))
                .thenReturn(Maybe.empty());

        var metadata = new AgentMetadata(AGENT_URL, AGENT_URL + "/jwks.json",
                "Test Agent", null, null, null, null, false, null, null);
        when(metadataFetcher.fetchMetadata(AGENT_URL)).thenReturn(metadata);

        var created = new Application();
        created.setId("new-app");
        when(applicationService.create(any(Domain.class), any(Application.class)))
                .thenReturn(Single.just(created));

        registry.resolveOrCreate(verificationWith("jwks_uri", AGENT_URL), DOMAIN_ID).blockingGet();

        var captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationService).create(any(Domain.class), captor.capture());

        var app = captor.getValue();
        assertNotNull(app.getIdentityProviders());
        assertEquals(2, app.getIdentityProviders().size());

        var idps = app.getIdentityProviders().iterator();
        ApplicationIdentityProvider first = idps.next();
        ApplicationIdentityProvider second = idps.next();
        assertEquals("idp-1", first.getIdentity());
        assertEquals(0, first.getPriority());
        assertEquals("idp-2", second.getIdentity());
        assertEquals(1, second.getPriority());
    }

    @Test
    public void shouldCreateWithNoIdPs_whenNoDefaultsConfigured() throws Exception {
        when(applicationService.findByDomainAndClientId(DOMAIN_ID, AGENT_URL))
                .thenReturn(Maybe.empty());

        var metadata = new AgentMetadata(AGENT_URL, AGENT_URL + "/jwks.json",
                "Test Agent", null, null, null, null, false, null, null);
        when(metadataFetcher.fetchMetadata(AGENT_URL)).thenReturn(metadata);

        var created = new Application();
        when(applicationService.create(any(Domain.class), any(Application.class)))
                .thenReturn(Single.just(created));

        registry.resolveOrCreate(verificationWith("jwks_uri", AGENT_URL), DOMAIN_ID).blockingGet();

        var captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationService).create(any(Domain.class), captor.capture());

        assertNull(captor.getValue().getIdentityProviders());
    }

    private VerificationResult verificationWith(String scheme, String agentServerUrl) {
        return new VerificationResult(scheme, "sig", null, "thumbprint", agentServerUrl, null);
    }
}

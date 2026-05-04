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
package io.gravitee.am.gateway.handler.aauth.service.bootstrap;

import io.gravitee.am.gateway.handler.aauth.service.AgentMetadata;
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;
import io.gravitee.am.repository.oidc.api.AAuthBootstrapBindingRepository;
import io.gravitee.am.repository.oidc.api.AAuthBootstrapRequestRepository;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapBinding;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused tests for {@link AAuthBootstrapService} covering:
 * <ul>
 *   <li>The four-branch announce decision tree (draft-hardt-aauth-bootstrap §6.7).</li>
 *   <li>The fail-closed metadata fetch on create (§6.3 / §11.2).</li>
 * </ul>
 */
public class AAuthBootstrapServiceAnnounceTest {

    private static final String DOMAIN = "domain-1";
    private static final String THUMBPRINT = "ephemeral-jkt";
    private static final String AGENT_SERVER_URL = "https://agent-server.example";
    private static final String USER_ID = "user-1";
    private static final String AGENT_IDENTIFIER = "aauth:assistant-v2@agent-server.example";

    private AAuthBootstrapRequestRepository requestRepository;
    private AAuthBootstrapBindingRepository bindingRepository;
    private AgentMetadataFetcher metadataFetcher;
    private AAuthBootstrapService service;

    @Before
    public void setUp() {
        requestRepository = mock(AAuthBootstrapRequestRepository.class);
        bindingRepository = mock(AAuthBootstrapBindingRepository.class);
        metadataFetcher = mock(AgentMetadataFetcher.class);
        service = new AAuthBootstrapService(
                requestRepository,
                bindingRepository,
                mock(BootstrapTokenMinter.class),
                mock(PairwiseSubjectGenerator.class),
                metadataFetcher
        );
    }

    @Test
    public void shouldEmitNotFoundWhenNoBootstrapRequestMatchesThumbprint() {
        when(requestRepository.findByEphemeralKeyThumbprint(THUMBPRINT)).thenReturn(Maybe.empty());

        TestObserver<Void> observer = service.announce(DOMAIN, THUMBPRINT, AGENT_IDENTIFIER, AGENT_SERVER_URL).test();

        observer.assertError(BootstrapRequestNotFoundException.class);
        verify(bindingRepository, never()).findByDomainAndAgentServerUrlAndUserId(any(), any(), any());
        verify(bindingRepository, never()).create(any());
        verify(bindingRepository, never()).update(any());
    }

    @Test
    public void shouldCreateNewBindingWhenNoneExistsForUserAndAgentServer() {
        AAuthBootstrapRequest request = bootstrapRequest();
        when(requestRepository.findByEphemeralKeyThumbprint(THUMBPRINT)).thenReturn(Maybe.just(request));
        when(bindingRepository.findByDomainAndAgentServerUrlAndUserId(DOMAIN, AGENT_SERVER_URL, USER_ID))
                .thenReturn(Maybe.empty());
        when(bindingRepository.create(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        service.announce(DOMAIN, THUMBPRINT, AGENT_IDENTIFIER, AGENT_SERVER_URL)
                .test()
                .assertComplete();

        ArgumentCaptor<AAuthBootstrapBinding> captor = ArgumentCaptor.forClass(AAuthBootstrapBinding.class);
        verify(bindingRepository).create(captor.capture());
        AAuthBootstrapBinding written = captor.getValue();
        assertNotNull(written.getId());
        assertEquals(DOMAIN, written.getDomain());
        assertEquals(USER_ID, written.getUserId());
        assertEquals(AGENT_SERVER_URL, written.getAgentServerUrl());
        assertEquals(AGENT_IDENTIFIER, written.getAgentIdentifier());
        assertEquals("pairwise-sub", written.getPairwiseSub());
        assertNotNull(written.getCreatedAt());
        assertEquals(written.getCreatedAt(), written.getUpdatedAt());
        verify(bindingRepository, never()).update(any());
    }

    @Test
    public void shouldRefreshExistingBindingIdempotentlyWhenAgentIdentifierMatches() {
        AAuthBootstrapRequest request = bootstrapRequest();
        AAuthBootstrapBinding existing = existingBinding(AGENT_IDENTIFIER);
        when(requestRepository.findByEphemeralKeyThumbprint(THUMBPRINT)).thenReturn(Maybe.just(request));
        when(bindingRepository.findByDomainAndAgentServerUrlAndUserId(DOMAIN, AGENT_SERVER_URL, USER_ID))
                .thenReturn(Maybe.just(existing));
        when(bindingRepository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        long beforeUpdate = existing.getUpdatedAt().getTime();

        service.announce(DOMAIN, THUMBPRINT, AGENT_IDENTIFIER, AGENT_SERVER_URL)
                .test()
                .assertComplete();

        verify(bindingRepository, never()).create(any());
        ArgumentCaptor<AAuthBootstrapBinding> captor = ArgumentCaptor.forClass(AAuthBootstrapBinding.class);
        verify(bindingRepository).update(captor.capture());
        assertEquals(AGENT_IDENTIFIER, captor.getValue().getAgentIdentifier());
        assertTrue("updatedAt should advance on idempotent announce",
                captor.getValue().getUpdatedAt().getTime() >= beforeUpdate);
    }

    @Test
    public void shouldEmitConflictWhenExistingBindingHasDifferentAgentIdentifier() {
        AAuthBootstrapRequest request = bootstrapRequest();
        AAuthBootstrapBinding existing = existingBinding("aauth:other-agent@agent-server.example");
        when(requestRepository.findByEphemeralKeyThumbprint(THUMBPRINT)).thenReturn(Maybe.just(request));
        when(bindingRepository.findByDomainAndAgentServerUrlAndUserId(DOMAIN, AGENT_SERVER_URL, USER_ID))
                .thenReturn(Maybe.just(existing));

        TestObserver<Void> observer = service.announce(DOMAIN, THUMBPRINT, AGENT_IDENTIFIER, AGENT_SERVER_URL).test();

        observer.assertError(BootstrapBindingConflictException.class);
        observer.assertError(err -> {
            BootstrapBindingConflictException conflict = (BootstrapBindingConflictException) err;
            return USER_ID.equals(conflict.getUserId())
                    && AGENT_SERVER_URL.equals(conflict.getAgentServerUrl())
                    && "aauth:other-agent@agent-server.example".equals(conflict.getExistingAgentIdentifier())
                    && AGENT_IDENTIFIER.equals(conflict.getAnnouncedAgentIdentifier());
        });
        verify(bindingRepository, never()).create(any());
        verify(bindingRepository, never()).update(any());
    }

    // ---- create() — fail-closed metadata fetch (§6.3 / §11.2) ----

    @Test
    public void createShouldRejectWhenMetadataFetchFails() throws Exception {
        when(metadataFetcher.fetchMetadata(AGENT_SERVER_URL))
                .thenThrow(new SignatureVerificationException("invalid_key"));

        TestObserver<AAuthBootstrapRequest> observer = service.create(
                DOMAIN, AGENT_SERVER_URL, "{\"kty\":\"OKP\"}", THUMBPRINT,
                null, null, null, 600).test();

        observer.assertError(BootstrapMetadataException.class);
        observer.assertError(err ->
                BootstrapMetadataException.ERR_UNREACHABLE.equals(((BootstrapMetadataException) err).getErrorCode()));
        verify(requestRepository, never()).create(any());
    }

    @Test
    public void createShouldRejectWhenMetadataIssuerDoesNotMatchAgentServer() throws Exception {
        AgentMetadata mismatched = new AgentMetadata(
                "https://impostor.example", AGENT_SERVER_URL + "/jwks.json",
                "Imposter", null, null, null, null, false, null, null);
        when(metadataFetcher.fetchMetadata(AGENT_SERVER_URL)).thenReturn(mismatched);

        TestObserver<AAuthBootstrapRequest> observer = service.create(
                DOMAIN, AGENT_SERVER_URL, "{\"kty\":\"OKP\"}", THUMBPRINT,
                null, null, null, 600).test();

        observer.assertError(BootstrapMetadataException.class);
        observer.assertError(err ->
                BootstrapMetadataException.ERR_ISSUER_MISMATCH.equals(((BootstrapMetadataException) err).getErrorCode()));
        verify(requestRepository, never()).create(any());
    }

    @Test
    public void createShouldFallBackToHostnameWhenClientNameMissing() throws Exception {
        AgentMetadata noName = new AgentMetadata(
                AGENT_SERVER_URL, AGENT_SERVER_URL + "/jwks.json",
                null, null, null, null, null, false, null, null);
        when(metadataFetcher.fetchMetadata(AGENT_SERVER_URL)).thenReturn(noName);
        when(requestRepository.create(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        AAuthBootstrapRequest result = service.create(
                DOMAIN, AGENT_SERVER_URL, "{\"kty\":\"OKP\"}", THUMBPRINT,
                null, null, null, 600).blockingGet();

        // host of "https://agent-server.example" is "agent-server.example"
        assertEquals("agent-server.example", result.getAgentServerName());
        // logoUri stays null (no generic placeholder per the plan)
        assertEquals(null, result.getAgentServerLogoUri());
    }

    @Test
    public void createShouldUseClientNameAndLogoWhenPresent() throws Exception {
        AgentMetadata complete = new AgentMetadata(
                AGENT_SERVER_URL, AGENT_SERVER_URL + "/jwks.json",
                "Acme Assistant", "https://logo.example/logo.png",
                null, null, null, false, null, null);
        when(metadataFetcher.fetchMetadata(AGENT_SERVER_URL)).thenReturn(complete);
        when(requestRepository.create(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        AAuthBootstrapRequest result = service.create(
                DOMAIN, AGENT_SERVER_URL, "{\"kty\":\"OKP\"}", THUMBPRINT,
                null, null, null, 600).blockingGet();

        assertEquals("Acme Assistant", result.getAgentServerName());
        assertEquals("https://logo.example/logo.png", result.getAgentServerLogoUri());
    }

    private AAuthBootstrapRequest bootstrapRequest() {
        AAuthBootstrapRequest request = new AAuthBootstrapRequest();
        request.setId("req-1");
        request.setDomain(DOMAIN);
        request.setUserId(USER_ID);
        request.setAgentServerUrl(AGENT_SERVER_URL);
        request.setEphemeralKeyThumbprint(THUMBPRINT);
        request.setPairwiseSub("pairwise-sub");
        return request;
    }

    private AAuthBootstrapBinding existingBinding(String agentIdentifier) {
        AAuthBootstrapBinding binding = new AAuthBootstrapBinding();
        binding.setId("binding-1");
        binding.setDomain(DOMAIN);
        binding.setUserId(USER_ID);
        binding.setAgentServerUrl(AGENT_SERVER_URL);
        binding.setAgentIdentifier(agentIdentifier);
        binding.setPairwiseSub("pairwise-sub");
        binding.setCreatedAt(new java.util.Date(System.currentTimeMillis() - 60_000));
        binding.setUpdatedAt(new java.util.Date(System.currentTimeMillis() - 60_000));
        return binding;
    }
}

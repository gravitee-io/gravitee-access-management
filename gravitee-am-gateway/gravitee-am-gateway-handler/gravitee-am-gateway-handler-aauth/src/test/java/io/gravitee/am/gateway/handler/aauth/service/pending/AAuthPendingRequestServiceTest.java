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
package io.gravitee.am.gateway.handler.aauth.service.pending;

import io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import io.gravitee.am.repository.oidc.api.AAuthPendingRequestRepository;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.security.KeyPair;
import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AAuthPendingRequestService}.
 */
public class AAuthPendingRequestServiceTest {

    private AAuthPendingRequestRepository repository;
    private AAuthPendingRequestService service;
    private KeyPair agentKeyPair;

    @Before
    public void setUp() {
        repository = mock(AAuthPendingRequestRepository.class);
        service = new AAuthPendingRequestService(repository);
        agentKeyPair = TestAgentKeyPairFactory.ed25519();
    }

    @Test
    public void shouldCreatePendingRequest_withGeneratedIdAndInteractionCode() {
        when(repository.create(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        var result = service.create("domain-1", "https://agent.example", "aauth:bot@agent.example",
                "thumbprint", agentKeyPair.getPublic(), "app-1", "https://resource.example",
                "read write", "I need this", null, null, null, false, "https://ps.example/aauth", 600)
                .blockingGet();

        assertNotNull(result.getId());
        assertNotNull(result.getInteractionCode());
        assertEquals(PendingRequestStatus.PENDING.name(), result.getStatus());
        assertEquals("domain-1", result.getDomain());
        assertEquals("https://agent.example", result.getAgentServerUrl());
        assertEquals("aauth:bot@agent.example", result.getAgentIdentifier());
        assertEquals("thumbprint", result.getAgentJkt());
        assertEquals("read write", result.getScope());
        assertEquals("I need this", result.getJustification());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getExpireAt());
        assertTrue(result.getExpireAt().after(result.getCreatedAt()));
    }

    @Test
    public void shouldGenerateHumanReadableInteractionCode() {
        when(repository.create(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        var result = service.create("domain-1", "agent", "agent", "jkt",
                agentKeyPair.getPublic(), null, "resource", "read", null, null, null, null, false,
                "https://ps.example/aauth", 600)
                .blockingGet();

        // Format: XXXX-NNNN (4 letters + dash + 4 digits)
        assertTrue("Interaction code should match XXXX-NNNN pattern: " + result.getInteractionCode(),
                result.getInteractionCode().matches("[A-Z]{4}-[0-9]{4}"));
    }

    @Test
    public void shouldStoreSerializedPublicKey() {
        when(repository.create(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        var result = service.create("domain-1", "agent", "agent", "jkt",
                agentKeyPair.getPublic(), null, "resource", "read", null, null, null, null, false,
                "https://ps.example/aauth", 600)
                .blockingGet();

        assertNotNull(result.getAgentPublicKey());
        assertFalse(result.getAgentPublicKey().isEmpty());
    }

    @Test
    public void shouldPollSuccessfully_whenAgentJktMatches() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.PENDING);
        // Set lastAccessAt far enough in the past
        pending.setLastAccessAt(new Date(System.currentTimeMillis() - 10_000));

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        var result = service.poll("pending-1", "thumbprint").blockingGet();

        assertEquals(PendingRequestStatus.PENDING.name(), result.getStatus());
        verify(repository).update(any());
    }

    @Test
    public void shouldDeleteAndReturnCompleted_onConsumeOnce() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.COMPLETED);
        pending.setAuthToken("signed.token");
        pending.setLastAccessAt(new Date(System.currentTimeMillis() - 10_000));

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));
        when(repository.delete("pending-1")).thenReturn(Completable.complete());

        var result = service.poll("pending-1", "thumbprint").blockingGet();

        assertEquals(PendingRequestStatus.COMPLETED.name(), result.getStatus());
        assertEquals("signed.token", result.getAuthToken());
        verify(repository).delete("pending-1");
        verify(repository, never()).update(any());
    }

    @Test
    public void shouldRejectPoll_whenAgentJktMismatch() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.PENDING);
        pending.setLastAccessAt(new Date(System.currentTimeMillis() - 10_000));

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));

        service.poll("pending-1", "wrong-thumbprint")
                .test()
                .assertError(SecurityException.class);
    }

    @Test
    public void shouldRejectPoll_whenTooFast() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.PENDING);
        // Last access just now
        pending.setLastAccessAt(new Date());

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));

        service.poll("pending-1", "thumbprint")
                .test()
                .assertError(AAuthPendingRequestService.TooFastException.class);
    }

    @Test
    public void shouldReturnEmpty_whenIdUnknown() {
        when(repository.findById("unknown")).thenReturn(Maybe.empty());

        service.poll("unknown", "thumbprint")
                .test()
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void shouldMarkInteracting_andSetUserId() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.PENDING);

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        service.markInteracting("pending-1", "user-1")
                .test()
                .assertComplete()
                .assertNoErrors();

        ArgumentCaptor<AAuthPendingRequest> captor = ArgumentCaptor.forClass(AAuthPendingRequest.class);
        verify(repository).update(captor.capture());
        assertEquals(PendingRequestStatus.INTERACTING.name(), captor.getValue().getStatus());
        assertEquals("user-1", captor.getValue().getUserId());
    }

    @Test
    public void shouldThrowPendingRequestNotFoundException_whenMarkInteractingWithUnknownId() {
        when(repository.findById("unknown")).thenReturn(Maybe.empty());

        service.markInteracting("unknown", "user-1")
                .test()
                .assertError(PendingRequestNotFoundException.class);
    }

    @Test
    public void shouldThrowPendingRequestNotFoundException_whenApproveWithUnknownId() {
        when(repository.findById("unknown")).thenReturn(Maybe.empty());

        service.approve("unknown", "token", 300, "user-1")
                .test()
                .assertError(PendingRequestNotFoundException.class);
    }

    @Test
    public void shouldApproveAndSetAuthToken() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.PENDING);

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        var result = service.approve("pending-1", "signed.jwt.token", 300, "user-1")
                .blockingGet();

        assertEquals(PendingRequestStatus.COMPLETED.name(), result.getStatus());
        assertEquals("signed.jwt.token", result.getAuthToken());
        assertEquals(300L, result.getAuthTokenExpiresIn());
        assertEquals("user-1", result.getUserId());
    }

    @Test
    public void shouldDenyPendingRequest() {
        AAuthPendingRequest denied = createPendingRequest("thumbprint", PendingRequestStatus.DENIED);
        when(repository.updateStatus("pending-1", PendingRequestStatus.DENIED.name()))
                .thenReturn(Single.just(denied));

        var result = service.deny("pending-1").blockingGet();

        assertEquals(PendingRequestStatus.DENIED.name(), result.getStatus());
    }

    @Test
    public void shouldFindByInteractionCode() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.PENDING);
        when(repository.findByInteractionCode("ABCD-1234")).thenReturn(Maybe.just(pending));

        var result = service.findByInteractionCode("ABCD-1234").blockingGet();

        assertNotNull(result);
        assertEquals("pending-1", result.getId());
    }

    // --- Clarification tests ---

    @Test
    public void shouldAskClarification_andTransitionToAwaitingClarification() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.INTERACTING);
        pending.setClarificationSupported(true);

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        var result = service.askClarification("pending-1", "Why do you need write access?").blockingGet();

        assertEquals(PendingRequestStatus.AWAITING_CLARIFICATION.name(), result.getStatus());
        assertEquals("Why do you need write access?", result.getClarification());
        assertNull(result.getClarificationResponse());
        assertEquals(1, result.getClarificationRoundCount());
    }

    @Test
    public void shouldRejectClarification_whenNotSupported() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.INTERACTING);
        pending.setClarificationSupported(false);

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));

        service.askClarification("pending-1", "question")
                .test()
                .assertError(AAuthPendingRequestService.ClarificationNotSupportedException.class);
    }

    @Test
    public void shouldRejectClarification_whenMaxRoundsExceeded() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.INTERACTING);
        pending.setClarificationSupported(true);
        pending.setClarificationRoundCount(5);

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));

        service.askClarification("pending-1", "question")
                .test()
                .assertError(AAuthPendingRequestService.MaxClarificationRoundsException.class);
    }

    @Test
    public void shouldRespondClarification_andTransitionBackToInteracting() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.AWAITING_CLARIFICATION);
        pending.setClarification("Why do you need this?");

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        var result = service.respondClarification("pending-1", "thumbprint", "I need it for scheduling.").blockingGet();

        assertEquals(PendingRequestStatus.INTERACTING.name(), result.getStatus());
        assertEquals("I need it for scheduling.", result.getClarificationResponse());
    }

    @Test
    public void shouldRejectClarificationResponse_whenAgentKeyMismatch() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.AWAITING_CLARIFICATION);

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));

        service.respondClarification("pending-1", "wrong-thumbprint", "answer")
                .test()
                .assertError(SecurityException.class);
    }

    @Test
    public void shouldRejectClarificationResponse_whenNotAwaitingClarification() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.PENDING);

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));

        service.respondClarification("pending-1", "thumbprint", "answer")
                .test()
                .assertError(IllegalStateException.class);
    }

    @Test
    public void shouldCancelPendingRequest() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.PENDING);

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        service.cancel("pending-1", "thumbprint")
                .test()
                .assertComplete()
                .assertNoErrors();

        ArgumentCaptor<AAuthPendingRequest> captor = ArgumentCaptor.forClass(AAuthPendingRequest.class);
        verify(repository).update(captor.capture());
        assertEquals(PendingRequestStatus.CANCELLED.name(), captor.getValue().getStatus());
    }

    @Test
    public void shouldRejectCancel_whenAgentKeyMismatch() {
        AAuthPendingRequest pending = createPendingRequest("thumbprint", PendingRequestStatus.PENDING);

        when(repository.findById("pending-1")).thenReturn(Maybe.just(pending));

        service.cancel("pending-1", "wrong-thumbprint")
                .test()
                .assertError(SecurityException.class);
    }

    // --- End clarification tests ---

    private AAuthPendingRequest createPendingRequest(String agentJkt, PendingRequestStatus status) {
        AAuthPendingRequest req = new AAuthPendingRequest();
        req.setId("pending-1");
        req.setStatus(status.name());
        req.setDomain("domain-1");
        req.setAgentServerUrl("https://agent.example");
        req.setAgentJkt(agentJkt);
        req.setInteractionCode("ABCD-1234");
        req.setScope("read write");
        req.setCreatedAt(new Date());
        req.setLastAccessAt(new Date(System.currentTimeMillis() - 10_000));
        req.setExpireAt(new Date(System.currentTimeMillis() + 600_000));
        return req;
    }
}

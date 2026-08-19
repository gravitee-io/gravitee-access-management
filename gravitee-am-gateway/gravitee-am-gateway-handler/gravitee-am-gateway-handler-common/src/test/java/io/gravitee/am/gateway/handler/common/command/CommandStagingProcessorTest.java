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
package io.gravitee.am.gateway.handler.common.command;

import io.gravitee.am.common.oidc.command.CommandConstants;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.command.CommandStaging;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.CommandAuditBuilder;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CommandStagingProcessorTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private CommandStagingService commandStagingService;

    @Mock
    private CommandTokenService commandTokenService;

    @Mock
    private CommandTargetResolver commandTargetResolver;

    @Mock
    private WebClient webClient;

    @Mock
    private AuditService auditService;

    @Mock
    private HttpRequest<Buffer> httpRequest;

    @Mock
    private HttpResponse<Buffer> httpResponse;

    private Domain domain;
    private CommandStagingProcessor processor;
    private CommandStaging staging;
    private Client client;

    @BeforeEach
    public void setUp() {
        domain = new Domain("domain-id");
        domain.setName("domain-name");
        // enabled=false: no scheduled job, batches are triggered manually by the test
        processor = new CommandStagingProcessor(commandStagingService, commandTokenService, commandTargetResolver,
                webClient, auditService, domain, 10, 60, MAX_ATTEMPTS, false);

        staging = new CommandStaging();
        staging.setId("command-1");
        staging.setCommand("invalidate");
        staging.setUserId("user-1");
        staging.setReferenceType(ReferenceType.DOMAIN);
        staging.setReferenceId(domain.getId());

        client = target("app-1", "client-1", "https://rp.example.com/commands");

        when(commandStagingService.acquireLeaseAndFetch(any(), anyInt())).thenReturn(Flowable.just(staging));
        when(commandStagingService.manageAfterProcessing(any())).thenAnswer(invocation -> Single.just(invocation.getArguments()[0]));
        when(commandTargetResolver.resolveTargets()).thenReturn(Flowable.just(client));
        when(commandTokenService.mintToken(any(), any())).thenReturn(Single.just("signed-command-token"));
        when(webClient.postAbs(anyString())).thenReturn(httpRequest);
        when(httpRequest.rxSendForm(any(MultiMap.class))).thenReturn(Single.just(httpResponse));
    }

    @Test
    public void shouldDeliverCommandTokenAndMarkProcessed() {
        when(httpResponse.statusCode()).thenReturn(204);

        processor.processBatchOfStagingCommands();

        assertTrue(staging.isProcessed());
        assertEquals(1, staging.getAttempts());
        assertTrue(staging.isTerminal(client.getClientId()));
        verify(auditService, times(1)).report(any(CommandAuditBuilder.class));
        verify(commandStagingService).manageAfterProcessing(staging);

        var formCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpRequest).rxSendForm(formCaptor.capture());
        assertEquals("signed-command-token", formCaptor.getValue().get(CommandConstants.COMMAND_TOKEN_PARAM));
    }

    @Test
    public void benignUnknownAccountConflictIsTerminal() {
        when(httpResponse.statusCode()).thenReturn(409);
        when(httpResponse.bodyAsString()).thenReturn("{\"account_state\":\"unknown\",\"error\":\"incompatible_state\"}");

        processor.processBatchOfStagingCommands();

        assertTrue(staging.isProcessed());
        assertTrue(staging.isTerminal(client.getClientId()));
        verify(auditService, times(1)).report(any(CommandAuditBuilder.class));
    }

    @Test
    public void failedDeliveryIsRetriedOnNextBatch() {
        when(httpResponse.statusCode()).thenReturn(500);

        processor.processBatchOfStagingCommands();

        assertFalse(staging.isProcessed());
        assertEquals(1, staging.getAttempts());
        assertFalse(staging.isTerminal(client.getClientId()));
        // non-final failed attempts are not audited
        verify(auditService, never()).report(any());
        verify(commandStagingService).manageAfterProcessing(staging);
    }

    @Test
    public void failedDeliveryIsAbandonedAtAttemptsCap() {
        when(httpResponse.statusCode()).thenReturn(500);
        staging.setAttempts(MAX_ATTEMPTS - 1);

        processor.processBatchOfStagingCommands();

        assertTrue(staging.isProcessed());
        assertEquals(MAX_ATTEMPTS, staging.getAttempts());
        assertFalse(staging.isTerminal(client.getClientId()));
        verify(auditService, times(1)).report(any(CommandAuditBuilder.class));
    }

    @Test
    public void clientsAlreadyTerminalAreSkippedOnRetry() {
        staging.markClientTerminal(client.getClientId());

        processor.processBatchOfStagingCommands();

        assertTrue(staging.isProcessed());
        verify(webClient, never()).postAbs(anyString());
        verify(commandTokenService, never()).mintToken(any(), any());
    }

    @Test
    public void networkErrorCountsAsFailedAttempt() {
        when(httpRequest.rxSendForm(any(MultiMap.class))).thenReturn(Single.error(new RuntimeException("connection refused")));

        processor.processBatchOfStagingCommands();

        assertFalse(staging.isProcessed());
        assertEquals(1, staging.getAttempts());
        assertFalse(staging.isTerminal(client.getClientId()));
    }

    @Test
    public void targetResolutionErrorFailsTheJobForRetry() {
        when(commandTargetResolver.resolveTargets()).thenReturn(Flowable.error(new RuntimeException("db unavailable")));

        processor.processBatchOfStagingCommands();

        // a failed enumeration must not silently drop targets: nothing is delivered
        // or persisted, the job is picked up again on the next batch
        assertFalse(staging.isProcessed());
        verify(webClient, never()).postAbs(anyString());
        verify(commandStagingService, never()).manageAfterProcessing(any());
    }

    private Client target(String id, String clientId, String commandEndpoint) {
        Client target = new Client();
        target.setId(id);
        target.setClientId(clientId);
        target.setCommandEndpoint(commandEndpoint);
        return target;
    }
}

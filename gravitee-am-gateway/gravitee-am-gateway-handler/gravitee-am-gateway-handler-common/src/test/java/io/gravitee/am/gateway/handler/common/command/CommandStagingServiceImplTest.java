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

import io.gravitee.am.common.exception.ActionLeaseException;
import io.gravitee.am.gateway.handler.common.command.impl.CommandStagingServiceImpl;
import io.gravitee.am.model.ActionLease;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.command.CommandRequest;
import io.gravitee.am.model.command.CommandStaging;
import io.gravitee.am.repository.gateway.api.ActionLeaseRepository;
import io.gravitee.am.repository.gateway.api.CommandStagingRepository;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CommandStagingServiceImplTest {

    @InjectMocks
    private CommandStagingServiceImpl commandStagingService = new CommandStagingServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private CommandStagingRepository commandStagingRepository;

    @Mock
    private ActionLeaseRepository actionLeaseRepository;

    @Mock
    private Node node;

    private final String domainId = UUID.randomUUID().toString();

    @BeforeEach
    public void setUp() throws Exception {
        when(domain.getId()).thenReturn(domainId);
        when(node.id()).thenReturn("node-1");
        commandStagingService.afterPropertiesSet();
    }

    @Test
    public void stageShouldCreateEntryKeyedByCommandId() {
        var stagingCaptor = ArgumentCaptor.forClass(CommandStaging.class);
        when(commandStagingRepository.createIfAbsent(stagingCaptor.capture()))
                .thenAnswer(invocation -> Maybe.just(invocation.getArguments()[0]));

        var request = CommandRequest.builder().id("command-1").command("invalidate").userId("user-1").domainId(domainId).build();
        commandStagingService.stage(request).test().assertComplete();

        var staging = stagingCaptor.getValue();
        assertEquals("command-1", staging.getId());
        assertEquals("invalidate", staging.getCommand());
        assertEquals("user-1", staging.getUserId());
        assertEquals(ReferenceType.DOMAIN, staging.getReferenceType());
        assertEquals(domainId, staging.getReferenceId());
    }

    @Test
    public void stageShouldCompleteWhenAnotherNodeStagedFirst() {
        when(commandStagingRepository.createIfAbsent(any())).thenReturn(Maybe.empty());

        var request = CommandRequest.builder().id("command-1").command("invalidate").userId("user-1").domainId(domainId).build();
        commandStagingService.stage(request).test().assertComplete().assertNoErrors();
    }

    @Test
    public void acquireLeaseAndFetchShouldErrorWhenLeaseRejected() {
        when(actionLeaseRepository.acquireLease(anyString(), anyString(), any())).thenReturn(Maybe.empty());

        commandStagingService.acquireLeaseAndFetch(Reference.domain(domainId), 10)
                .test()
                .assertError(ActionLeaseException.class);

        verify(commandStagingRepository, never()).findOldestByUpdateDate(any(), anyInt());
    }

    @Test
    public void acquireLeaseAndFetchShouldFetchWhenLeaseAcquired() {
        when(actionLeaseRepository.acquireLease(anyString(), anyString(), any())).thenReturn(Maybe.just(new ActionLease()));
        var staging = new CommandStaging();
        when(commandStagingRepository.findOldestByUpdateDate(any(), anyInt())).thenReturn(Flowable.just(staging));

        commandStagingService.acquireLeaseAndFetch(Reference.domain(domainId), 10)
                .test()
                .assertComplete()
                .assertValueCount(1);
    }

    @Test
    public void manageAfterProcessingShouldDeleteProcessedEntry() {
        when(commandStagingRepository.delete("command-1")).thenReturn(Completable.complete());

        var staging = new CommandStaging();
        staging.setId("command-1");
        staging.markAsProcessed();

        commandStagingService.manageAfterProcessing(staging).test().assertComplete();

        verify(commandStagingRepository).delete("command-1");
        verify(commandStagingRepository, never()).update(any());
    }

    @Test
    public void manageAfterProcessingShouldUpdateUnprocessedEntry() {
        when(commandStagingRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArguments()[0]));

        var staging = new CommandStaging();
        staging.setId("command-1");
        staging.incrementAttempts();

        commandStagingService.manageAfterProcessing(staging).test().assertComplete();

        verify(commandStagingRepository).update(staging);
        verify(commandStagingRepository, never()).delete(anyString());
    }
}

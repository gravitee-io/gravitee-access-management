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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.oidc.command.Command;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.service.impl.CommandManagementServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.reporter.builder.CommandAuditBuilder;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class CommandManagementServiceTest {

    @InjectMocks
    private CommandManagementService commandService = new CommandManagementServiceImpl();

    @Mock
    private EventService eventService;

    @Mock
    private io.gravitee.am.service.AuditService auditService;

    @Test
    public void shouldPersistCommandEventAndAudit() {
        var eventCaptor = ArgumentCaptor.forClass(Event.class);
        var auditCaptor = ArgumentCaptor.forClass(CommandAuditBuilder.class);
        when(eventService.create(eventCaptor.capture(), any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));

        var domain = new Domain(UUID.randomUUID().toString());
        var principal = new DefaultUser("admin-name");
        principal.setId("adminId");

        commandService.sendCommand(domain, Command.INVALIDATE, "userId", principal)
                .test()
                .assertComplete()
                .assertNoErrors();

        var event = eventCaptor.getValue();
        Assertions.assertEquals(Type.COMMAND, event.getType());
        Assertions.assertEquals(ReferenceType.DOMAIN, event.getPayload().getReferenceType());
        Assertions.assertEquals(domain.getId(), event.getPayload().getReferenceId());
        // the payload id is the command id: it keys the gateway staging dedup and
        // prevents distinct commands from being coalesced by the sync process
        Assertions.assertNotNull(event.getPayload().getId());

        var commandRequest = event.getPayload().getCommandRequest();
        Assertions.assertEquals(event.getPayload().getId(), commandRequest.getId());
        Assertions.assertEquals("invalidate", commandRequest.getCommand());
        Assertions.assertEquals("userId", commandRequest.getUserId());
        Assertions.assertEquals(domain.getId(), commandRequest.getDomainId());
        Assertions.assertEquals("adminId", commandRequest.getPrincipalId());
        Assertions.assertEquals("admin-name", commandRequest.getPrincipalUsername());

        verify(auditService, times(1)).report(auditCaptor.capture());
        var audit = auditCaptor.getValue().build(new ObjectMapper());
        Assertions.assertEquals(EventType.COMMAND_SCHEDULED, audit.getType());
        Assertions.assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        Assertions.assertEquals(domain.getId(), audit.getReferenceId());
        Assertions.assertEquals("adminId", audit.getActor().getId());
        Assertions.assertEquals("userId", audit.getTarget().getId());
    }

    @Test
    public void shouldAuditFailureWhenEventCannotBePersisted() {
        var auditCaptor = ArgumentCaptor.forClass(CommandAuditBuilder.class);
        when(eventService.create(any(), any())).thenReturn(Single.error(new RuntimeException("db down")));

        var domain = new Domain(UUID.randomUUID().toString());
        commandService.sendCommand(domain, Command.SUSPEND, "userId", null)
                .test()
                .assertError(RuntimeException.class);

        verify(auditService, times(1)).report(auditCaptor.capture());
        var audit = auditCaptor.getValue().build(new ObjectMapper());
        Assertions.assertEquals(EventType.COMMAND_SCHEDULED, audit.getType());
        Assertions.assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
    }
}

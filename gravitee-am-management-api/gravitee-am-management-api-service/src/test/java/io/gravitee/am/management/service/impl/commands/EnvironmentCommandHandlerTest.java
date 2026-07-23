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
package io.gravitee.am.management.service.impl.commands;

import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.Environment;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.model.NewEntrypoint;
import io.gravitee.am.service.model.NewEnvironment;
import io.gravitee.cockpit.api.command.model.accesspoint.AccessPoint;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.environment.EnvironmentCommand;
import io.gravitee.cockpit.api.command.v1.environment.EnvironmentCommandPayload;
import io.gravitee.cockpit.api.command.v1.environment.EnvironmentReply;
import io.gravitee.exchange.api.command.CommandStatus;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentCommandHandlerTest {

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private EntrypointService entrypointService;

    private MockEnvironment springEnvironment;

    public EnvironmentCommandHandler cut;

    @BeforeEach
    void before() {
        springEnvironment = new MockEnvironment();
        cut = new EnvironmentCommandHandler(environmentService, entrypointService, springEnvironment);
    }

    @Test
    void supportType() {
        assertEquals(CockpitCommandType.ENVIRONMENT.name(), cut.supportType());
    }

    @Test
    void handle() {

        EnvironmentCommandPayload environmentPayload = EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .organizationId("orga#1")
                .description("Environment description")
                .name("Environment name")
                .accessPoints(List.of(AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction1.io").build(), AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction2.io").build()))
                .build();
        EnvironmentCommand command = new EnvironmentCommand(environmentPayload);
        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"),
                argThat(newEnvironment -> newEnvironment.getHrids().equals(environmentPayload.hrids())
                        && newEnvironment.getDescription().equals(environmentPayload.description())
                        && newEnvironment.getName().equals(environmentPayload.name())
                        && newEnvironment.getDomainRestrictions().equals(environmentPayload.accessPoints().stream().map(AccessPoint::getHost).toList())),
                isNull())).thenReturn(Single.just(new Environment()));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));

        // non-cloud mode: entrypoint sync must be entirely skipped
        verifyNoInteractions(entrypointService);
    }

    @Test
    void handleWithException() {
        EnvironmentCommandPayload environmentPayload = EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .organizationId("orga#1")
                .description("Environment description")
                .name("Environment name")
                .accessPoints(List.of(AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction1.io").build(), AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction2.io").build()))
                .build();
        EnvironmentCommand command = new EnvironmentCommand(environmentPayload);
        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"), any(NewEnvironment.class), isNull())).thenReturn(Single.error(new TechnicalException()));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
    }

    @Test
    void shouldRejectEmptyPayload() {
        EnvironmentCommand command = new EnvironmentCommand(EnvironmentCommandPayload.builder().build());

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
        verifyNoInteractions(environmentService, entrypointService);
    }

    @Test
    void shouldRejectMissingOrganizationId() {
        EnvironmentCommandPayload environmentPayload = EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .name("Environment name")
                .build();
        EnvironmentCommand command = new EnvironmentCommand(environmentPayload);

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
        verifyNoInteractions(environmentService, entrypointService);
    }

    @Test
    void shouldRejectMissingHrids() {
        EnvironmentCommandPayload environmentPayload = EnvironmentCommandPayload.builder()
                .id("env#1")
                .organizationId("orga#1")
                .name("Environment name")
                .build();
        EnvironmentCommand command = new EnvironmentCommand(environmentPayload);

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
        verifyNoInteractions(environmentService, entrypointService);
    }

    private void enableCloudMode() {
        springEnvironment.setProperty("cloud.enabled", "true");
        springEnvironment.setProperty("installation.type", "managed");
    }

    @Test
    void handleCloudMode_syncsEntrypoints_deleteThenRecreate() {
        enableCloudMode();

        EnvironmentCommandPayload environmentPayload = EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .organizationId("orga#1")
                .description("Environment description")
                .name("Environment name")
                .accessPoints(List.of(
                        AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction1.io").build(),
                        AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction2.io").build(),
                        AccessPoint.builder().target(AccessPoint.Target.CONSOLE).host("console.io").build()))
                .build();
        EnvironmentCommand command = new EnvironmentCommand(environmentPayload);

        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"), any(NewEnvironment.class), isNull())).thenReturn(Single.just(new Environment()));

        Entrypoint existing1 = new Entrypoint();
        existing1.setId("entrypoint#1");
        Entrypoint existing2 = new Entrypoint();
        existing2.setId("entrypoint#2");
        when(entrypointService.findByEnvironment("orga#1", "env#1")).thenReturn(Flowable.just(existing1, existing2));
        when(entrypointService.delete(eq("entrypoint#1"), eq("orga#1"), isNull())).thenReturn(Completable.complete());
        when(entrypointService.delete(eq("entrypoint#2"), eq("orga#1"), isNull())).thenReturn(Completable.complete());
        when(entrypointService.create(eq("orga#1"), any(NewEntrypoint.class), isNull())).thenAnswer(i -> Single.just(new Entrypoint()));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));

        verify(entrypointService, times(1)).delete("entrypoint#1", "orga#1", null);
        verify(entrypointService, times(1)).delete("entrypoint#2", "orga#1", null);
        verify(entrypointService, times(1)).create(eq("orga#1"), argThat(newEntrypoint ->
                newEntrypoint.getUrl().equals("https://domain.restriction1.io")
                        && newEntrypoint.getName().equals("domain.restriction1.io")
                        && "env#1".equals(newEntrypoint.getEnvironmentId())), isNull());
        verify(entrypointService, times(1)).create(eq("orga#1"), argThat(newEntrypoint ->
                newEntrypoint.getUrl().equals("https://domain.restriction2.io")
                        && newEntrypoint.getName().equals("domain.restriction2.io")
                        && "env#1".equals(newEntrypoint.getEnvironmentId())), isNull());
        // CONSOLE access point must not generate an entrypoint
        verify(entrypointService, times(2)).create(eq("orga#1"), any(NewEntrypoint.class), isNull());
    }

    @Test
    void handleCloudMode_noPriorEntrypoints_createsNewOnes() {
        enableCloudMode();

        EnvironmentCommandPayload environmentPayload = EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .organizationId("orga#1")
                .description("Environment description")
                .name("Environment name")
                .accessPoints(List.of(AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction1.io").build()))
                .build();
        EnvironmentCommand command = new EnvironmentCommand(environmentPayload);

        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"), any(NewEnvironment.class), isNull())).thenReturn(Single.just(new Environment()));
        when(entrypointService.findByEnvironment("orga#1", "env#1")).thenReturn(Flowable.empty());
        when(entrypointService.create(eq("orga#1"), any(NewEntrypoint.class), isNull())).thenAnswer(i -> Single.just(new Entrypoint()));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));

        verify(entrypointService, never()).delete(any(), any(), any());
        verify(entrypointService, times(1)).create(eq("orga#1"), any(NewEntrypoint.class), isNull());
    }

    @Test
    void handleNonCloudMode_doesNotCallEntrypointService() {

        EnvironmentCommandPayload environmentPayload = EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .organizationId("orga#1")
                .description("Environment description")
                .name("Environment name")
                .accessPoints(List.of(AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction1.io").build()))
                .build();
        EnvironmentCommand command = new EnvironmentCommand(environmentPayload);

        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"), any(NewEnvironment.class), isNull())).thenReturn(Single.just(new Environment()));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));

        verifyNoInteractions(entrypointService);
    }

    @Test
    void handleCloudMode_entrypointSyncFailure_propagatesAsErrorReply() {
        enableCloudMode();

        EnvironmentCommandPayload environmentPayload = EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .organizationId("orga#1")
                .description("Environment description")
                .name("Environment name")
                .accessPoints(List.of(AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction1.io").build()))
                .build();
        EnvironmentCommand command = new EnvironmentCommand(environmentPayload);

        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"), any(NewEnvironment.class), isNull())).thenReturn(Single.just(new Environment()));
        when(entrypointService.findByEnvironment("orga#1", "env#1")).thenReturn(Flowable.error(new TechnicalException("boom")));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
    }
}

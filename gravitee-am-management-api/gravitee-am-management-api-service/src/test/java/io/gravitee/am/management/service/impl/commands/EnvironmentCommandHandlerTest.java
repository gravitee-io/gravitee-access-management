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

import io.gravitee.am.common.env.CloudProperties;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
        springEnvironment.setProperty("installation.type", CloudProperties.INSTALLATION_TYPE_MANAGED);
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
        when(entrypointService.create(eq("orga#1"), any(NewEntrypoint.class), anyBoolean(), isNull())).thenAnswer(i -> Single.just(new Entrypoint()));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));

        verify(entrypointService, times(1)).delete("entrypoint#1", "orga#1", null);
        verify(entrypointService, times(1)).delete("entrypoint#2", "orga#1", null);
        verify(entrypointService, times(1)).create(eq("orga#1"), argThat(newEntrypoint ->
                newEntrypoint.getUrl().equals("https://domain.restriction1.io")
                        && newEntrypoint.getName().equals("domain.restriction1.io")
                        && "env#1".equals(newEntrypoint.getEnvironmentId())), eq(true), isNull());
        verify(entrypointService, times(1)).create(eq("orga#1"), argThat(newEntrypoint ->
                newEntrypoint.getUrl().equals("https://domain.restriction2.io")
                        && newEntrypoint.getName().equals("domain.restriction2.io")
                        && "env#1".equals(newEntrypoint.getEnvironmentId())), eq(true), isNull());
        // CONSOLE access point must not generate an entrypoint
        verify(entrypointService, times(2)).create(eq("orga#1"), any(NewEntrypoint.class), anyBoolean(), isNull());
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
        when(entrypointService.create(eq("orga#1"), any(NewEntrypoint.class), anyBoolean(), isNull())).thenAnswer(i -> Single.just(new Entrypoint()));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));

        verify(entrypointService, never()).delete(any(), any(), any());
        verify(entrypointService, times(1)).create(eq("orga#1"), any(NewEntrypoint.class), anyBoolean(), isNull());
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

    /**
     * The access point Cockpit generates itself becomes the environment's default entrypoint; the
     * customer's overriding one does not. Resolution later drops the default whenever an override
     * exists, so getting this inversion right is what makes the override win.
     */
    private void assertPersistedDefaultFlag(List<AccessPoint> accessPoints, String host, boolean expectedDefaultFlag) {
        EnvironmentCommandPayload environmentPayload = EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .organizationId("orga#1")
                .name("Environment name")
                .accessPoints(accessPoints)
                .build();

        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"), any(NewEnvironment.class), isNull())).thenReturn(Single.just(new Environment()));
        when(entrypointService.findByEnvironment("orga#1", "env#1")).thenReturn(Flowable.empty());
        when(entrypointService.create(eq("orga#1"), any(NewEntrypoint.class), anyBoolean(), isNull())).thenAnswer(i -> Single.just(new Entrypoint()));

        TestObserver<EnvironmentReply> obs = cut.handle(new EnvironmentCommand(environmentPayload)).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));
        verify(entrypointService, times(1)).create(eq("orga#1"),
                argThat(newEntrypoint -> newEntrypoint.getName().equals(host)), eq(expectedDefaultFlag), isNull());
    }

    @Test
    void handleCloudMode_overridingAccessPoint_isNotTheDefaultEntrypoint() {
        enableCloudMode();

        // The overriding access point needs a non-overriding companion, otherwise the payload is rejected
        // for carrying no default GATEWAY access point.
        assertPersistedDefaultFlag(List.of(
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("auth.acme.com").overriding(true).build(),
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("env-acme.gravitee.io").build()),
                "auth.acme.com", false);
    }

    @Test
    void handleCloudMode_nonOverridingAccessPoint_becomesTheDefaultEntrypoint() {
        enableCloudMode();

        assertPersistedDefaultFlag(List.of(AccessPoint.builder()
                .target(AccessPoint.Target.GATEWAY)
                .host("env-acme.gravitee.io")
                .overriding(false)
                .build()), "env-acme.gravitee.io", true);
    }

    @Test
    void handleCloudMode_accessPointWithoutOverridingFlag_becomesTheDefaultEntrypoint() {
        enableCloudMode();

        // `overriding` is a primitive boolean, so a payload omitting it deserializes to false and every
        // entrypoint ends up flagged default. Resolution copes by returning them all rather than none.
        assertPersistedDefaultFlag(List.of(AccessPoint.builder()
                .target(AccessPoint.Target.GATEWAY)
                .host("env-acme.gravitee.io")
                .build()), "env-acme.gravitee.io", true);
    }

    private EnvironmentCommand commandWithAccessPoints(AccessPoint... accessPoints) {
        return new EnvironmentCommand(EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .organizationId("orga#1")
                .description("Environment description")
                .name("Environment name")
                // Arrays.asList, not List.of: one case deliberately passes a null entry.
                .accessPoints(Arrays.asList(accessPoints))
                .build());
    }

    private void assertRejectedForMissingHost(EnvironmentCommand command) {
        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
        verifyNoInteractions(environmentService, entrypointService);
    }

    /**
     * Rejecting here is what stops a host-less access point becoming an entrypoint with url
     * "https://null" and no name, which used to break the organization-wide entrypoint listing.
     * Cockpit is told the command failed rather than left believing the gateway URL was provisioned.
     */
    @Test
    void handleCloudMode_nullHostAccessPoint_isRejected() {
        enableCloudMode();

        assertRejectedForMissingHost(commandWithAccessPoints(
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host(null).secured(true).build()));
    }

    @Test
    void handleNonCloudMode_nullHostAccessPoint_isRejected() {
        // A null reaching domainRestrictions blows up InternetDomainName.from during host validation.
        assertRejectedForMissingHost(commandWithAccessPoints(
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host(null).build()));
    }

    @Test
    void handleCloudMode_blankHostAccessPoint_isRejected() {
        enableCloudMode();

        assertRejectedForMissingHost(commandWithAccessPoints(
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("  ").build()));
    }

    @Test
    void handleCloudMode_oneValidAndOneHostlessAccessPoint_rejectsTheWholeCommand() {
        enableCloudMode();

        assertRejectedForMissingHost(commandWithAccessPoints(
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction1.io").build(),
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host(null).build()));
    }

    @Test
    void handleCloudMode_nullAccessPointEntry_repliesWithoutThrowing() {
        enableCloudMode();

        // The guard runs before the reactive chain is built, so a null entry would escape handle() as a
        // synchronous throw rather than reaching onErrorReturn. A list holding only a null entry carries
        // no GATEWAY access point, so the reply is an ERROR.
        assertRejectedInCloudMode(commandWithAccessPoints(new AccessPoint[]{null}));
    }

    private void assertRejectedInCloudMode(EnvironmentCommand command) {
        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
        // the environment is neither created nor updated when the payload is rejected
        verifyNoInteractions(environmentService, entrypointService);
    }

    @Test
    void handleCloudMode_nullAccessPoints_isRejected() {
        enableCloudMode();

        assertRejectedInCloudMode(new EnvironmentCommand(EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .organizationId("orga#1")
                .name("Environment name")
                .build()));
    }

    @Test
    void handleCloudMode_emptyAccessPoints_isRejected() {
        enableCloudMode();

        assertRejectedInCloudMode(commandWithAccessPoints());
    }

    @Test
    void handleCloudMode_consoleAccessPointOnly_isRejected() {
        enableCloudMode();

        assertRejectedInCloudMode(commandWithAccessPoints(
                AccessPoint.builder().target(AccessPoint.Target.CONSOLE).host("console.acme.com").build()));
    }

    @Test
    void handleCloudMode_onlyOverridingGatewayAccessPoints_isRejected() {
        enableCloudMode();

        // An environment must always carry the non-overriding access point Cockpit generates; without it
        // resolution drops the override and the environment has no entrypoint left.
        assertRejectedInCloudMode(commandWithAccessPoints(
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("auth.acme.com").overriding(true).build(),
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("login.acme.com").overriding(true).build()));
    }

    @Test
    void handleNonCloudMode_nullAccessPoints_isAccepted() {
        EnvironmentCommand command = new EnvironmentCommand(EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .organizationId("orga#1")
                .name("Environment name")
                .build());

        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"), any(NewEnvironment.class), isNull())).thenReturn(Single.just(new Environment()));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));
        verifyNoInteractions(entrypointService);
    }

    @Test
    void handleNonCloudMode_onlyOverridingGatewayAccessPoints_isAccepted() {
        EnvironmentCommand command = commandWithAccessPoints(
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("auth.acme.com").overriding(true).build());

        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"), any(NewEnvironment.class), isNull())).thenReturn(Single.just(new Environment()));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));
        verifyNoInteractions(entrypointService);
    }

    @Test
    void handleNullHostConsoleAccessPoint_isAccepted() {
        // Only GATEWAY access points become entrypoints, so a host-less CONSOLE one must not block the command.
        EnvironmentCommand command = commandWithAccessPoints(
                AccessPoint.builder().target(AccessPoint.Target.CONSOLE).host(null).build());

        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"), any(NewEnvironment.class), isNull())).thenReturn(Single.just(new Environment()));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));
        verify(environmentService).createOrUpdate(eq("orga#1"), eq("env#1"),
                argThat(newEnvironment -> newEnvironment.getDomainRestrictions().isEmpty()), isNull());
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

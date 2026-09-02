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
import io.gravitee.am.model.Environment;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.model.DesiredEntrypoint;
import io.gravitee.am.service.model.NewEnvironment;
import io.gravitee.cockpit.api.command.model.accesspoint.AccessPoint;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.environment.EnvironmentCommand;
import io.gravitee.cockpit.api.command.v1.environment.EnvironmentCommandPayload;
import io.gravitee.cockpit.api.command.v1.environment.EnvironmentReply;
import io.gravitee.exchange.api.command.CommandStatus;
import io.reactivex.rxjava3.core.Completable;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentCommandHandlerTest {

    private static final long HANDLE_TIMEOUT_SECONDS = 10;

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

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
    }

    @Test
    void shouldRejectEmptyPayload() {
        EnvironmentCommand command = new EnvironmentCommand(EnvironmentCommandPayload.builder().build());

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
        verifyNoInteractions(environmentService, entrypointService);
    }

    @Test
    void handleCloudMode_statesTheGatewayAccessPointsAsTheEnvironmentEntrypoints() {
        enableCloudMode();

        handleSuccessfully(environmentCommand(List.of(
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction1.io").build(),
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction2.io").build(),
                // a CONSOLE access point must not become an entrypoint
                AccessPoint.builder().target(AccessPoint.Target.CONSOLE).host("console.io").build())));

        verify(entrypointService).syncForEnvironment("orga#1", "env#1", List.of(
                new DesiredEntrypoint("https://domain.restriction1.io", "domain.restriction1.io", true),
                new DesiredEntrypoint("https://domain.restriction2.io", "domain.restriction2.io", true)), null);
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

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));

        verifyNoInteractions(entrypointService);
    }

    @Test
    void handleCloudMode_overridingAccessPoint_isNotTheDefaultEntrypoint() {
        enableCloudMode();

        // The non-overriding companion is what keeps the payload valid.
        assertStatedDefaultFlag(List.of(
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("auth.acme.com").overriding(true).build(),
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("env-acme.gravitee.io").build()),
                "auth.acme.com", false);
    }

    @Test
    void handleCloudMode_nonOverridingAccessPoint_becomesTheDefaultEntrypoint() {
        enableCloudMode();

        assertStatedDefaultFlag(List.of(AccessPoint.builder()
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
        assertStatedDefaultFlag(List.of(AccessPoint.builder()
                .target(AccessPoint.Target.GATEWAY)
                .host("env-acme.gravitee.io")
                .build()), "env-acme.gravitee.io", true);
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
        // synchronous throw rather than reaching onErrorReturn.
        assertRejectedInCloudMode(commandWithAccessPoints(new AccessPoint[]{null}));
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

        // Resolution drops the default whenever an override exists, so overriding-only would leave nothing.
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

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));
        verifyNoInteractions(entrypointService);
    }

    @Test
    void handleNonCloudMode_onlyOverridingGatewayAccessPoints_isAccepted() {
        EnvironmentCommand command = commandWithAccessPoints(
                AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("auth.acme.com").overriding(true).build());

        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"), any(NewEnvironment.class), isNull())).thenReturn(Single.just(new Environment()));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
        when(entrypointService.syncForEnvironment(any(), any(), anyList(), any())).thenReturn(Completable.error(new TechnicalException("boom")));

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
    }

    private void enableCloudMode() {
        springEnvironment.setProperty("cloud.enabled", "true");
        springEnvironment.setProperty("installation.type", CloudProperties.INSTALLATION_TYPE_MANAGED);
    }

    private void handleSuccessfully(EnvironmentCommand command) {
        when(environmentService.createOrUpdate(eq("orga#1"), eq("env#1"), any(NewEnvironment.class), isNull())).thenReturn(Single.just(new Environment()));
        when(entrypointService.syncForEnvironment(any(), any(), anyList(), any())).thenReturn(Completable.complete());

        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));
    }

    private EnvironmentCommand environmentCommand(List<AccessPoint> accessPoints) {
        return new EnvironmentCommand(EnvironmentCommandPayload.builder()
                .id("env#1")
                .hrids(Collections.singletonList("env-1"))
                .organizationId("orga#1")
                .name("Environment name")
                .accessPoints(accessPoints)
                .build());
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

    private void assertStatedDefaultFlag(List<AccessPoint> accessPoints, String host, boolean expectedDefaultFlag) {
        handleSuccessfully(environmentCommand(accessPoints));

        verify(entrypointService).syncForEnvironment(eq("orga#1"), eq("env#1"),
                argThat(desired -> desired.contains(new DesiredEntrypoint("https://" + host, host, expectedDefaultFlag))), isNull());
    }

    private void assertRejectedForMissingHost(EnvironmentCommand command) {
        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
        verifyNoInteractions(environmentService, entrypointService);
    }

    private void assertRejectedInCloudMode(EnvironmentCommand command) {
        TestObserver<EnvironmentReply> obs = cut.handle(command).test();

        obs.awaitDone(HANDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
        verifyNoInteractions(environmentService, entrypointService);
    }
}

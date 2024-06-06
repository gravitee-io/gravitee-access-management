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

import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.model.NewOrganization;
import io.gravitee.cockpit.api.command.model.accesspoint.AccessPoint;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.organization.OrganizationCommand;
import io.gravitee.cockpit.api.command.v1.organization.OrganizationCommandPayload;
import io.gravitee.cockpit.api.command.v1.organization.OrganizationReply;
import io.gravitee.exchange.api.command.CommandStatus;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class OrganizationCommandHandlerTest {

    @Mock
    private OrganizationService organizationService;

    public OrganizationCommandHandler cut;

    @BeforeEach
    void before() {
        cut = new OrganizationCommandHandler(organizationService);
    }

    @Test
    void supportType() {
        assertEquals(CockpitCommandType.ORGANIZATION.name(), cut.supportType());
    }

    @Test
    void handle() {

        OrganizationCommandPayload organizationPayload = OrganizationCommandPayload.builder()
                .id("orga#1")
                .hrids(Collections.singletonList("orga-1"))
                .description("Organization description")
                .name("Organization name")
                .accessPoints(List.of(AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction1.io").build(),
                        AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction2.io").build()))
                .build();
        OrganizationCommand command = new OrganizationCommand(organizationPayload);

        when(organizationService.createOrUpdate(eq("orga#1"),
                argThat(newOrganization -> newOrganization.getHrids().equals(organizationPayload.hrids())
                        && newOrganization.getDescription().equals(organizationPayload.description())
                        && newOrganization.getName().equals(organizationPayload.name())
                        && newOrganization.getDomainRestrictions().equals(organizationPayload.accessPoints().stream()
                        .map(AccessPoint::getHost).collect(Collectors.toList()))),
                isNull())).thenReturn(Single.just(new Organization()));

        TestObserver<OrganizationReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));
    }

    @Test
    void handleWithException() {

        OrganizationCommandPayload organizationPayload = OrganizationCommandPayload.builder()
                .id("orga#1")
                .description("Organization description")
                .name("Organization name")
                .accessPoints(List.of(AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction1.io").build(),
                        AccessPoint.builder().target(AccessPoint.Target.GATEWAY).host("domain.restriction2.io").build()))
                .build();
        OrganizationCommand command = new OrganizationCommand(organizationPayload);

        when(organizationService.createOrUpdate(eq("orga#1"), any(NewOrganization.class), isNull())).thenReturn(Single.error(new TechnicalException()));

        TestObserver<OrganizationReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
    }
}

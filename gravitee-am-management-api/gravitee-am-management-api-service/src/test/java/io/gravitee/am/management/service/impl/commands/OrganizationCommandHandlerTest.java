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
import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.CommandStatus;
import io.gravitee.cockpit.api.command.organization.OrganizationCommand;
import io.gravitee.cockpit.api.command.organization.OrganizationPayload;
import io.gravitee.cockpit.api.command.organization.OrganizationReply;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class OrganizationCommandHandlerTest {

    @Mock
    private OrganizationService organizationService;

    public OrganizationCommandHandler cut;

    @Before
    public void before() {
        cut = new OrganizationCommandHandler(organizationService);
    }

    @Test
    public void handleType() {
        assertEquals(Command.Type.ORGANIZATION_COMMAND, cut.handleType());
    }

    @Test
    public void handle() {

        OrganizationPayload organizationPayload = new OrganizationPayload();
        OrganizationCommand command = new OrganizationCommand(organizationPayload);

        organizationPayload.setId("orga#1");
        organizationPayload.setHrids(Collections.singletonList("orga-1"));
        organizationPayload.setDescription("Organization description");
        organizationPayload.setName("Organization name");
        organizationPayload.setDomainRestrictions(Arrays.asList("domain.restriction1.io", "domain.restriction2.io"));

        when(organizationService.createOrUpdate(eq("orga#1"),
                argThat(newOrganization -> newOrganization.getHrids().equals(organizationPayload.getHrids())
                        && newOrganization.getDescription().equals(organizationPayload.getDescription())
                        && newOrganization.getName().equals(organizationPayload.getName())
                        && newOrganization.getDomainRestrictions().equals(organizationPayload.getDomainRestrictions())),
                isNull())).thenReturn(Single.just(new Organization()));

        TestObserver<OrganizationReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));
    }

    @Test
    public void handleWithException() {

        OrganizationPayload organizationPayload = new OrganizationPayload();
        OrganizationCommand command = new OrganizationCommand(organizationPayload);

        organizationPayload.setId("orga#1");
        organizationPayload.setDescription("Organization description");
        organizationPayload.setName("Organization name");
        organizationPayload.setDomainRestrictions(Arrays.asList("domain.restriction1.io", "domain.restriction2.io"));

        when(organizationService.createOrUpdate(eq("orga#1"), any(NewOrganization.class), isNull())).thenReturn(Single.error(new TechnicalException()));

        TestObserver<OrganizationReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
    }
}
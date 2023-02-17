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

import io.gravitee.am.model.Installation;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.InstallationService;
import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.CommandStatus;
import io.gravitee.cockpit.api.command.installation.InstallationCommand;
import io.gravitee.cockpit.api.command.installation.InstallationPayload;
import io.gravitee.cockpit.api.command.installation.InstallationReply;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class InstallationCommandHandlerTest extends TestCase {

    private static final String CUSTOM_VALUE = "customValue";
    private static final String CUSTOM_KEY = "customKey";
    private static final String INSTALLATION_ID = "installation#1";

    @Mock
    private InstallationService installationService;

    public InstallationCommandHandler cut;

    @Before
    public void before() {
        cut = new InstallationCommandHandler(installationService);
    }

    @Test
    public void handleType() {
        assertEquals(Command.Type.INSTALLATION_COMMAND, cut.handleType());
    }

    @Test
    public void handle() {
        final Installation installation = new Installation();
        installation.setId(INSTALLATION_ID);
        installation.getAdditionalInformation().put(CUSTOM_KEY, CUSTOM_VALUE);

        InstallationPayload installationPayload = new InstallationPayload();
        InstallationCommand command = new InstallationCommand(installationPayload);
        installationPayload.setId(INSTALLATION_ID);
        installationPayload.setStatus("ACCEPTED");

        when(installationService.getOrInitialize()).thenReturn(Single.just(installation));
        when(installationService.setAdditionalInformation(anyMap())).thenAnswer(i -> Single.just(installation));

        TestObserver<InstallationReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));

        final HashMap<String, String> expectedAdditionalInfos = new HashMap<>();
        expectedAdditionalInfos.put(CUSTOM_KEY, CUSTOM_VALUE);
        expectedAdditionalInfos.put(Installation.COCKPIT_INSTALLATION_STATUS, "ACCEPTED");
        verify(installationService, times(1)).setAdditionalInformation(expectedAdditionalInfos);
    }

    @Test
    public void handleWithException() {

        final Installation installation = new Installation();
        installation.setId(INSTALLATION_ID);
        installation.getAdditionalInformation().put(CUSTOM_KEY, CUSTOM_VALUE);

        InstallationPayload installationPayload = new InstallationPayload();
        InstallationCommand command = new InstallationCommand(installationPayload);
        installationPayload.setId(INSTALLATION_ID);
        installationPayload.setStatus("ACCEPTED");

        when(installationService.getOrInitialize()).thenReturn(Single.just(installation));
        when(installationService.setAdditionalInformation(anyMap())).thenReturn(Single.error(new TechnicalException()));

        TestObserver<InstallationReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
    }
}
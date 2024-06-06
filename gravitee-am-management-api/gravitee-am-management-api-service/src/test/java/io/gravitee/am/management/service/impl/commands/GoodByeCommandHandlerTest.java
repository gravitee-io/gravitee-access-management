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
import io.gravitee.am.service.InstallationService;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommandPayload;
import io.gravitee.exchange.api.command.goodbye.GoodByeReply;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.management.service.impl.commands.GoodbyeCommandHandler.DELETED_STATUS;
import static io.gravitee.am.model.Installation.COCKPIT_INSTALLATION_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class GoodByeCommandHandlerTest {

    @Mock
    private InstallationService installationService;

    public GoodbyeCommandHandler cut;

    @BeforeEach
    void before() {
        cut = new GoodbyeCommandHandler(installationService);
    }

    @Test
    void supportType() {
        assertEquals(GoodByeCommand.COMMAND_TYPE, cut.supportType());
    }

    @Test
    void handle() {
        GoodByeCommand command = new GoodByeCommand(new GoodByeCommandPayload());
        final Installation installation = new Installation();
        when(installationService.addAdditionalInformation(any(Map.class))).thenReturn(Single.just(installation));

        TestObserver<GoodByeReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));

        final ArgumentCaptor<Map<String, String>> expectedAdditionalInfos = ArgumentCaptor.forClass(Map.class);
        verify(installationService, times(1)).addAdditionalInformation(expectedAdditionalInfos.capture());

        assertEquals(DELETED_STATUS, expectedAdditionalInfos.getValue().get(COCKPIT_INSTALLATION_STATUS));
    }

    @Test
    void handleWithException() {
        GoodByeCommand command = new GoodByeCommand(new GoodByeCommandPayload());

        when(installationService.addAdditionalInformation(any(Map.class))).thenReturn(Single.error(new RuntimeException("Unexpected error")));

        TestObserver<GoodByeReply> obs = cut.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
    }
}

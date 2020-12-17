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

import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Installation;
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.InstallationService;
import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.hello.HelloCommand;
import io.gravitee.cockpit.api.command.hello.HelloPayload;
import io.gravitee.node.api.Node;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class HelloCommandProducerTest {

    private static final String HOSTNAME = "test.gravitee.io";
    private static final String CUSTOM_VALUE = "customValue";
    private static final String CUSTOM_KEY = "customKey";
    private static final String INSTALLATION_ID = "installation#1";
    @Mock
    private InstallationService installationService;

    @Mock
    private Node node;

    private HelloCommandProducer cut;


    @Before
    public void before() {
        cut = new HelloCommandProducer(node, installationService);
    }

    @Test
    public void produceType() {
        assertEquals(Command.Type.HELLO_COMMAND, cut.produceType());
    }

    @Test
    public void produce() {

        final Installation installation = new Installation();
        installation.setId(INSTALLATION_ID);
        installation.getAdditionalInformation().put(CUSTOM_KEY, CUSTOM_VALUE);

        when(node.hostname()).thenReturn(HOSTNAME);
        when(installationService.getOrInitialize()).thenReturn(Single.just(installation));

        final HelloCommand command = new HelloCommand();
        final HelloPayload payload = new HelloPayload();
        payload.setNode(new io.gravitee.cockpit.api.command.Node());
        command.setPayload(payload);
        final TestObserver<HelloCommand> obs = cut.prepare(command).test();

        obs.awaitTerminalEvent();
        obs.assertValue(helloCommand -> {
            assertEquals(CUSTOM_VALUE, helloCommand.getPayload().getAdditionalInformation().get(CUSTOM_KEY));
            assertEquals(HOSTNAME, helloCommand.getPayload().getNode().getHostname());
            assertEquals(Organization.DEFAULT, helloCommand.getPayload().getDefaultOrganizationId());
            assertEquals(Environment.DEFAULT, helloCommand.getPayload().getDefaultEnvironmentId());
            assertEquals(INSTALLATION_ID, helloCommand.getPayload().getNode().getInstallationId());
            assertEquals(HOSTNAME, helloCommand.getPayload().getNode().getHostname());

            return true;
        });
    }

    @Test
    public void produceWithException() {

        when(installationService.getOrInitialize()).thenReturn(Single.error(new TechnicalException()));
        final TestObserver<HelloCommand> obs = cut.prepare(new HelloCommand()).test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalException.class);
    }
}
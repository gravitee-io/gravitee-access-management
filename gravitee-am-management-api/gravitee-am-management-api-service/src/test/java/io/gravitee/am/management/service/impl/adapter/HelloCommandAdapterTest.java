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
package io.gravitee.am.management.service.impl.adapter;


import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Installation;
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.InstallationService;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.hello.HelloCommand;
import io.gravitee.cockpit.api.command.v1.hello.HelloCommandPayload;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class HelloCommandAdapterTest {

    private static final String HOSTNAME = "test.gravitee.io";
    private static final String CUSTOM_VALUE = "customValue";
    private static final String CUSTOM_KEY = "customKey";
    private static final String INSTALLATION_ID = "installation#1";

    @Mock
    private InstallationService installationService;

    @Mock
    private Node node;

    private HelloCommandAdapter cut;

    @BeforeEach
    public void beforeEach() {
        cut = new HelloCommandAdapter(node, installationService);
    }

    @Test
    void produceType() {
        assertEquals(CockpitCommandType.HELLO.name(), cut.supportType());
    }

    @Test
    void adapt() {

        final Installation installation = new Installation();
        installation.setId(INSTALLATION_ID);
        installation.getAdditionalInformation().put(CUSTOM_KEY, CUSTOM_VALUE);

        when(node.hostname()).thenReturn(HOSTNAME);
        when(installationService.getOrInitialize()).thenReturn(Single.just(installation));

        final TestObserver<HelloCommand> obs = cut
                .adapt(INSTALLATION_ID, new io.gravitee.exchange.api.command.hello.HelloCommand(new HelloCommandPayload()))
                .test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(helloCommand -> {
            assertEquals(CUSTOM_VALUE, helloCommand.getPayload().getAdditionalInformation().get(CUSTOM_KEY));
            assertTrue(helloCommand.getPayload().getAdditionalInformation().containsKey("API_URL"));
            assertTrue(helloCommand.getPayload().getAdditionalInformation().containsKey("UI_URL"));
            assertEquals(HOSTNAME, helloCommand.getPayload().getNode().hostname());
            assertEquals(Organization.DEFAULT, helloCommand.getPayload().getDefaultOrganizationId());
            assertEquals(Environment.DEFAULT, helloCommand.getPayload().getDefaultEnvironmentId());
            assertEquals(INSTALLATION_ID, helloCommand.getPayload().getNode().installationId());
            assertEquals(HOSTNAME, helloCommand.getPayload().getNode().hostname());

            return true;
        });
    }

    @Test
    void adaptWithException() {
        when(installationService.getOrInitialize()).thenReturn(Single.error(new TechnicalException()));
        cut
                .adapt(INSTALLATION_ID, new io.gravitee.exchange.api.command.hello.HelloCommand(new HelloCommandPayload()))
                .test()
                .assertError(TechnicalException.class);
    }

    @Test
    void adaptWithTrailingSlashUrls() {
        // Set URLs with trailing slashes using reflection
        ReflectionTestUtils.setField(cut, "apiURL", "http://localhost:8093/management/");
        ReflectionTestUtils.setField(cut, "uiURL", "http://localhost:4200/");
        
        final Installation installation = new Installation();
        installation.setId(INSTALLATION_ID);
        installation.getAdditionalInformation().put(CUSTOM_KEY, CUSTOM_VALUE);

        when(node.hostname()).thenReturn(HOSTNAME);
        when(installationService.getOrInitialize()).thenReturn(Single.just(installation));

        final TestObserver<HelloCommand> obs = cut
                .adapt(INSTALLATION_ID, new io.gravitee.exchange.api.command.hello.HelloCommand(new HelloCommandPayload()))
                .test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(helloCommand -> {
            // Verify that trailing slashes are removed
            assertEquals("http://localhost:8093/management", helloCommand.getPayload().getAdditionalInformation().get("API_URL"));
            assertEquals("http://localhost:4200", helloCommand.getPayload().getAdditionalInformation().get("UI_URL"));

            return true;
        });
    }
}

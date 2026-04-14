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
package io.gravitee.am.service.application;

import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.service.impl.application.ApplicationAAuthAgentTemplate;
import org.junit.Test;

import static org.junit.Assert.*;

public class ApplicationAAuthAgentTemplateTest {

    private final ApplicationAAuthAgentTemplate template = new ApplicationAAuthAgentTemplate();

    @Test
    public void shouldHandle_aauth_agent_type() {
        Application app = new Application();
        app.setType(ApplicationType.AAUTH_AGENT);
        assertTrue(template.canHandle(app));
    }

    @Test
    public void shouldNotHandle_service_type() {
        Application app = new Application();
        app.setType(ApplicationType.SERVICE);
        assertFalse(template.canHandle(app));
    }

    @Test
    public void shouldNotHandle_web_type() {
        Application app = new Application();
        app.setType(ApplicationType.WEB);
        assertFalse(template.canHandle(app));
    }

    @Test
    public void shouldSetDefaults_noExistingSettings() {
        Application app = new Application();
        app.setType(ApplicationType.AAUTH_AGENT);
        app.setName("Test Agent");

        template.handle(app);

        assertNotNull(app.getSettings());
        assertNotNull(app.getSettings().getOauth());

        ApplicationOAuthSettings oauth = app.getSettings().getOauth();
        assertNotNull("clientId should be generated", oauth.getClientId());
        assertEquals("Test Agent", oauth.getClientName());
        assertTrue("grant types should be empty", oauth.getGrantTypes().isEmpty());
        assertTrue("response types should be empty", oauth.getResponseTypes().isEmpty());
        assertEquals(ClientAuthenticationMethod.NONE, oauth.getTokenEndpointAuthMethod());
    }

    @Test
    public void shouldPreserveExistingClientId() {
        Application app = new Application();
        app.setType(ApplicationType.AAUTH_AGENT);
        app.setName("Test Agent");

        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientId("https://agent.example/.well-known/aauth-agent.json");
        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oauth);
        app.setSettings(settings);

        template.handle(app);

        assertEquals("https://agent.example/.well-known/aauth-agent.json",
                app.getSettings().getOauth().getClientId());
    }

    @Test
    public void shouldNotSetClientSecret() {
        Application app = new Application();
        app.setType(ApplicationType.AAUTH_AGENT);
        app.setName("Test Agent");

        template.handle(app);

        assertNull("AAUTH agents should not have a client secret",
                app.getSettings().getOauth().getClientSecret());
    }
}

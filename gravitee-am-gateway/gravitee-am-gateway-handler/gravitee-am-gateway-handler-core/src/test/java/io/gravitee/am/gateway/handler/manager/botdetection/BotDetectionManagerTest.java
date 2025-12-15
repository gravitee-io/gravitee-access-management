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
package io.gravitee.am.gateway.handler.manager.botdetection;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.manager.botdetection.impl.BotDetectionManagerImpl;
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.service.BotDetectionService;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION;
import static io.gravitee.am.common.utils.ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_PLUGIN;
import static io.gravitee.am.gateway.handler.manager.botdetection.impl.BotDetectionManagerImpl.TEMPLATE_KEY_BOT_DETECTION_ENABLED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class BotDetectionManagerTest {

    public static final String BOT_DETECTION_PLUGIN = "PLUGIN_ID";
    public static final String BOT_DETECTION_PLUGIN_TYPE = "PLUGIN_TYPE";

    @InjectMocks
    private BotDetectionManagerImpl cut = new BotDetectionManagerImpl();

    @Spy
    private ObjectMapper mapper = new ObjectMapper();

    @Mock
    private DomainReadinessService domainReadinessService;

    @Mock
    private BotDetectionService botDetectionService;

    @Mock
    private Domain domain;

    private BotDetection botDetection = new BotDetection();

    @Before
    public void setUp() {
        botDetection.setConfiguration("{\"key\": \"value\"}");
        botDetection.setType(BOT_DETECTION_PLUGIN_TYPE);
        cut.getBotDetections().put(BOT_DETECTION_PLUGIN, botDetection);
    }

    @Test
    public void shouldHandleInitializationError() {
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("domain-name");
        when(botDetectionService.findByDomain("domain-id")).thenReturn(io.reactivex.rxjava3.core.Flowable.error(new RuntimeException("DB Error")));

        cut.afterPropertiesSet();

        org.mockito.Mockito.verify(domainReadinessService).pluginInitFailed("domain-id", io.gravitee.am.common.event.Type.BOT_DETECTION.name(), "DB Error");
    }

    @Test
    public void shouldGetTemplateVariables_Domain() {
        final Domain domain = new Domain();

        final AccountSettings accountSettings = new AccountSettings();
        accountSettings.setUseBotDetection(true);
        accountSettings.setBotDetectionPlugin(BOT_DETECTION_PLUGIN);
        domain.setAccountSettings(accountSettings);

        final Client client = new Client();

        Map<String, Object> variables = cut.getTemplateVariables(domain, client);
        assertNotNull(variables);
        assertFalse(variables.isEmpty());
        assertEquals(variables.get(TEMPLATE_KEY_BOT_DETECTION_ENABLED), true);
        assertEquals(variables.get(TEMPLATE_KEY_BOT_DETECTION_PLUGIN), BOT_DETECTION_PLUGIN_TYPE);
        assertTrue(variables.get(TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION) instanceof Map);
        assertEquals(((Map<?, ?>) variables.get(TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION)).size(), 1);
        assertEquals(((Map<?, ?>) variables.get(TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION)).get("key"), "value");
    }

    @Test
    public void shouldGetTemplateVariables_App() {
        final Domain domain = new Domain();

        final AccountSettings domainAccountSettings = new AccountSettings();
        domainAccountSettings.setUseBotDetection(false);
        domain.setAccountSettings(domainAccountSettings);

        final Client client = new Client();
        final AccountSettings appAccountSettings = new AccountSettings();
        appAccountSettings.setInherited(false);
        appAccountSettings.setUseBotDetection(true);
        appAccountSettings.setBotDetectionPlugin(BOT_DETECTION_PLUGIN);
        client.setAccountSettings(appAccountSettings);

        Map<String, Object> variables = cut.getTemplateVariables(domain, client);
        assertNotNull(variables);
        assertFalse(variables.isEmpty());
        assertEquals(variables.get(TEMPLATE_KEY_BOT_DETECTION_ENABLED), true);
        assertEquals(variables.get(TEMPLATE_KEY_BOT_DETECTION_PLUGIN), BOT_DETECTION_PLUGIN_TYPE);
        assertTrue(variables.get(TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION) instanceof Map);
        assertEquals(((Map<?, ?>) variables.get(TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION)).size(), 1);
        assertEquals(((Map<?, ?>) variables.get(TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION)).get("key"), "value");
    }


    @Test
    public void shouldNotGetTemplateVariables_AppDisabled() {
        final Domain domain = new Domain();

        final AccountSettings accountSettings = new AccountSettings();
        accountSettings.setUseBotDetection(true);
        accountSettings.setBotDetectionPlugin(BOT_DETECTION_PLUGIN);
        domain.setAccountSettings(accountSettings);

        final Client client = new Client();
        final AccountSettings appAccountSettings = new AccountSettings();
        appAccountSettings.setUseBotDetection(false);
        appAccountSettings.setInherited(false);
        client.setAccountSettings(appAccountSettings);

        Map<String, Object> variables = cut.getTemplateVariables(domain, client);
        assertNotNull(variables);
        assertFalse(variables.isEmpty());
        assertEquals(variables.size(), 1);
        assertEquals(variables.get(TEMPLATE_KEY_BOT_DETECTION_ENABLED), false);
    }

}

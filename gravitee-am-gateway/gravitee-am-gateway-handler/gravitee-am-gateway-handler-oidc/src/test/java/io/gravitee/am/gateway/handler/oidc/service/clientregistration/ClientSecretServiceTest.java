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
package io.gravitee.am.gateway.handler.oidc.service.clientregistration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.ClientSecretServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.impl.SecretService;
import io.gravitee.am.service.spring.application.SecretHashAlgorithm;

@RunWith(MockitoJUnitRunner.class)
public class ClientSecretServiceTest {

    @Mock
    private SecretService secretService;

    @Mock
    private Domain domain;

    @InjectMocks
    private ClientSecretServiceImpl clientSecretService;

    @Before
    public void setUp() {
    }

    @Test
    public void determineClientSecret_noSecrets() {
        Client client = new Client();
        client.setClientSecrets(null);

        Optional<ClientSecret> result = clientSecretService.determineClientSecret(client);
        assertFalse(result.isPresent());
    }

    @Test
    public void determineClientSecret_matchByValue() {
        String secretValue = "s3cr3t";
        ClientSecret secret1 = new ClientSecret();
        secret1.setSecret("other");
        ClientSecret secret2 = new ClientSecret();
        secret2.setSecret(secretValue);
        secret2.setId("id-2");

        Client client = new Client();
        client.setClientSecret(secretValue);
        client.setClientSecrets(List.of(secret1, secret2));

        Optional<ClientSecret> result = clientSecretService.determineClientSecret(client);
        assertTrue(result.isPresent());
        assertEquals("id-2", result.get().getId());
    }

    @Test
    public void determineClientSecret_matchBySettings() {
        String secretValue = "value-not-in-list";
        String settingsId = "settings-id";

        ApplicationSecretSettings settings = new ApplicationSecretSettings();
        settings.setId(settingsId);
        settings.setAlgorithm(SecretHashAlgorithm.NONE.name());

        ClientSecret secret1 = new ClientSecret();
        secret1.setSecret("other");
        secret1.setSettingsId("other-id");
        ClientSecret secret2 = new ClientSecret();
        secret2.setSecret("some-hashed-value");
        secret2.setSettingsId(settingsId);

        Client client = new Client();
        client.setClientSecret(secretValue);
        client.setClientSecrets(List.of(secret1, secret2));
        client.setSecretSettings(List.of(settings));

        Optional<ClientSecret> result = clientSecretService.determineClientSecret(client);
        assertTrue(result.isPresent());
        assertEquals(secret2, result.get());
    }

    @Test
    public void determineClientSecret_noMatch() {
        ClientSecret secret1 = new ClientSecret();
        secret1.setSecret("other");
        
        Client client = new Client();
        client.setClientSecret("value");
        client.setClientSecrets(List.of(secret1));
        client.setSecretSettings(Collections.emptyList());

        Optional<ClientSecret> result = clientSecretService.determineClientSecret(client);
        assertFalse(result.isPresent());
    }

    @Test
    public void getSecretId_renewPresent() {
        ClientSecret secret = new ClientSecret();
        secret.setId("id-123");

        String result = clientSecretService.getSecretId(new Client(), Optional.of(secret), domain);
        assertEquals("id-123", result);
    }

    @Test
    public void getSecretId_renewEmpty_noSettings() {
        Client client = new Client();
        client.setSecretSettings(null);
        client.setClientSecret(null); // Will generate random

        ClientSecret generatedSecret = new ClientSecret();
        generatedSecret.setId("generated-id");
        
        when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(generatedSecret);

        String result = clientSecretService.getSecretId(client, Optional.empty(), domain);

        assertEquals("generated-id", result);
        verify(secretService).generateClientSecret(eq("Default"), anyString(), any(ApplicationSecretSettings.class), any(), any());
        
        // ensure settings were created and added
        assertFalse(client.getSecretSettings().isEmpty());
        // ensure created settings list is mutable (ArrayList) not ImmutableCollections$ListN
        assertTrue(client.getSecretSettings() instanceof ArrayList);
    }

    @Test
    public void getSecretId_renewEmpty_hasNoneSettings() {
        ApplicationSecretSettings settings = new ApplicationSecretSettings();
        settings.setAlgorithm(SecretHashAlgorithm.NONE.name());
        settings.setId("settings-id");

        Client client = new Client();
        client.setSecretSettings(List.of(settings)); // passed as immutable
        
        ClientSecret generatedSecret = new ClientSecret();
        generatedSecret.setId("generated-id");

        when(secretService.generateClientSecret(eq("Default"), any(), eq(settings), any(), any())).thenReturn(generatedSecret);

        String result = clientSecretService.getSecretId(client, Optional.empty(), domain);

        assertEquals("generated-id", result);
        // Should use existing settings
        assertEquals(settings, client.getSecretSettings().get(0));
    }
    @Test
    public void getSecretId_ensureSettingsListIsMutable() {
        Client client = new Client();
        // Force creation of new list by having no matching NONE settings
        client.setSecretSettings(null);

        ClientSecret generatedSecret = new ClientSecret();
        generatedSecret.setId("generated-id");

        when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(generatedSecret);

        clientSecretService.getSecretId(client, Optional.empty(), domain);

        // Verify we can add to the list without UnsupportedOperationException
        client.getSecretSettings().add(new ApplicationSecretSettings());
        assertEquals(2, client.getSecretSettings().size());
    }

    @Test
    public void getSecretId_shouldPreserveExistingData() {
        // Setup initial client state with existing (but different) settings and secrets
        ApplicationSecretSettings existingSetting = new ApplicationSecretSettings();
        existingSetting.setId("existing-setting-id");
        existingSetting.setAlgorithm("HMAC");

        ClientSecret existingSecret = new ClientSecret();
        existingSecret.setId("existing-secret-id");
        existingSecret.setSecret("old-secret");

        Client client = new Client();
        // Use mutable lists to start with, imitating a loaded client
        client.setSecretSettings(new ArrayList<>(List.of(existingSetting)));
        client.setClientSecrets(new ArrayList<>(List.of(existingSecret)));

        // Setup mocks
        ClientSecret generatedSecret = new ClientSecret();
        generatedSecret.setId("new-secret-id");
        when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(generatedSecret);

        // Execute
        String result = clientSecretService.getSecretId(client, Optional.empty(), domain);

        // Assertions
        assertEquals("new-secret-id", result);

        // Verify Settings were preserved + new one added
        List<ApplicationSecretSettings> settings = client.getSecretSettings();
        assertEquals("Should have 2 settings (old + new)", 2, settings.size());
        boolean hasExisting = settings.stream().anyMatch(s -> "existing-setting-id".equals(s.getId()));
        boolean hasNone = settings.stream().anyMatch(s -> SecretHashAlgorithm.NONE.name().equals(s.getAlgorithm()));
        assertTrue("Should still have existing setting", hasExisting);
        assertTrue("Should have new NONE setting", hasNone);

        // Verify Secrets were preserved + new one added
        List<ClientSecret> secrets = client.getClientSecrets();
        assertEquals("Should have 2 secrets (old + new)", 2, secrets.size());
        boolean hasOldSecret = secrets.stream().anyMatch(s -> "existing-secret-id".equals(s.getId()));
        boolean hasNewSecret = secrets.stream().anyMatch(s -> "new-secret-id".equals(s.getId()));
        assertTrue("Should still have existing secret", hasOldSecret);
        assertTrue("Should have new secret", hasNewSecret);
    }
}

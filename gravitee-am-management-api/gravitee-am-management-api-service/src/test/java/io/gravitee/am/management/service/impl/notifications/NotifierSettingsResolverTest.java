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
package io.gravitee.am.management.service.impl.notifications;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NotifierSettingsResolverTest {

    @Test
    public void shouldResolveCertificateSettingsWithBackwardsCompatibility() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("services.certificate.enabled", "false");
        env.setProperty("services.certificate.cronExpression", "* * * * *");
        env.setProperty("services.certificate.expiryThresholds", "20,15");
        env.setProperty("services.certificate.expiryEmailSubject", "Subject");

        NotifierSettingsResolver resolver = new NotifierSettingsResolver();
        NotifierSettings certificateNotifierSettings = resolver.certificateNotifierSettings(env);

        assertFalse(certificateNotifierSettings.enabled());
        assertEquals("* * * * *", certificateNotifierSettings.cronExpression());
        assertEquals(List.of(20, 15), certificateNotifierSettings.expiryThresholds());
        assertEquals("Subject", certificateNotifierSettings.emailSubject());
    }

    @Test
    public void shouldResolveCertificateSettings() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("services.certificate.enabled", "false");
        env.setProperty("services.certificate.cronExpression", "* * * * *");
        env.setProperty("services.certificate.expiryThresholds", "20,15");
        env.setProperty("services.certificate.expiryEmailSubject", "Subject");

        env.setProperty("services.notifier.certificate.enabled", "true");
        env.setProperty("services.notifier.certificate.cronExpression", "30 * * * *");
        env.setProperty("services.notifier.certificate.expiryThresholds", "25,20,15");
        env.setProperty("services.notifier.certificate.expiryEmailSubject", "Subject2");

        NotifierSettingsResolver resolver = new NotifierSettingsResolver();
        NotifierSettings certificateNotifierSettings = resolver.certificateNotifierSettings(env);

        assertTrue(certificateNotifierSettings.enabled());
        assertEquals("30 * * * *", certificateNotifierSettings.cronExpression());
        assertEquals(List.of(25, 20, 15), certificateNotifierSettings.expiryThresholds());
        assertEquals("Subject2", certificateNotifierSettings.emailSubject());
    }


    @Test
    public void shouldResolveClientSecretSettingsWithBackwardsCompatibility() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("services.client-secret.enabled", "false");
        env.setProperty("services.client-secret.cronExpression", "* * * * *");
        env.setProperty("services.client-secret.expiryThresholds", "20,15");
        env.setProperty("services.client-secret.expiryEmailSubject", "Subject");

        NotifierSettingsResolver resolver = new NotifierSettingsResolver();
        NotifierSettings clientSecretSettings = resolver.clientSecretNotifierSettings(env);

        assertFalse(clientSecretSettings.enabled());
        assertEquals("* * * * *", clientSecretSettings.cronExpression());
        assertEquals(List.of(20, 15), clientSecretSettings.expiryThresholds());
        assertEquals("Subject", clientSecretSettings.emailSubject());
    }

    @Test
    public void shouldResolveClientSecretSettings() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("services.client-secret.enabled", "false");
        env.setProperty("services.client-secret.cronExpression", "* * * * *");
        env.setProperty("services.client-secret.expiryThresholds", "20,15");
        env.setProperty("services.client-secret.expiryEmailSubject", "Subject");

        env.setProperty("services.notifier.client-secret.enabled", "true");
        env.setProperty("services.notifier.client-secret.cronExpression", "30 * * * *");
        env.setProperty("services.notifier.client-secret.expiryThresholds", "25,20,15");
        env.setProperty("services.notifier.client-secret.expiryEmailSubject", "Subject2");

        NotifierSettingsResolver resolver = new NotifierSettingsResolver();
        NotifierSettings settings = resolver.clientSecretNotifierSettings(env);

        assertTrue(settings.enabled());
        assertEquals("30 * * * *", settings.cronExpression());
        assertEquals(List.of(25, 20, 15), settings.expiryThresholds());
        assertEquals("Subject2", settings.emailSubject());
    }

    
}
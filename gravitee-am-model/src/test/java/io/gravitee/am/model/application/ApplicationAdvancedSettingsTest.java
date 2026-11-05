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
package io.gravitee.am.model.application;

import io.gravitee.am.model.oidc.Client;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationAdvancedSettingsTest {

    @Test
    void shouldCopySkipConsentToTheClient() {
        ApplicationAdvancedSettings settings = new ApplicationAdvancedSettings();
        settings.setSkipConsent(true);
        Client client = new Client();

        settings.copyTo(client);

        assertThat(client.isSkipConsent()).isTrue();
        assertThat(client.getAutoApproveScopes()).isEqualTo(List.of("true"));
    }

    @Test
    void shouldNotSkipConsentByDefault() {
        Client client = new Client();

        new ApplicationAdvancedSettings().copyTo(client);

        assertThat(client.isSkipConsent()).isFalse();
        assertThat(client.getAutoApproveScopes()).isNull();
    }

    @Test
    void shouldKeepSkipConsentWhenTheClientIsCopied() throws Exception {
        Client client = new Client();
        client.setSkipConsent(true);

        assertThat(new Client(client).isSkipConsent()).isTrue();
        assertThat(client.clone().isSkipConsent()).isTrue();
    }
}

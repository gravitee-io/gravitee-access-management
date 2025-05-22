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

package io.gravitee.am.service;

import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.authentication.crypto.password.NoOpPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import io.gravitee.am.service.impl.SecretService;
import io.gravitee.am.service.spring.application.SecretHashAlgorithm;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.service.spring.application.SecretHashAlgorithm.PropertyKeys.BCRYPT_ROUNDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SecretServiceTest {

    private final SecretService cut = new SecretService();

    @Test
    void should_generate_secret_none_algo() {
        var secret = cut.generateClientSecret("Toto", "africa", new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.NONE.name(), Map.of()), new SecretExpirationSettings(), null);
        assertThat(secret)
                .isNotNull()
                .hasFieldOrPropertyWithValue("secret", "africa")
                .hasFieldOrPropertyWithValue("settingsId", "settingsIdValue");
    }

    @Test
    void should_generate_secret_bcrypt_algo() {
        var secret = cut.generateClientSecret("Toto","africa", new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.BCRYPT.name(), Map.of(BCRYPT_ROUNDS.getKey(), BCRYPT_ROUNDS.getValue())), new SecretExpirationSettings(), null);
        assertThat(secret)
                .isNotNull()
                .hasFieldOrPropertyWithValue("settingsId", "settingsIdValue");
        assertThat(secret.getSecret()).startsWith("$2a$10");
    }

    @Test
    void should_cache_the_encoder() {
        final var bcryptEncoder = cut.getOrCreatePasswordEncoder(new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.BCRYPT.name(), Map.of(BCRYPT_ROUNDS.getKey(), BCRYPT_ROUNDS.getValue())));
        assertThat(bcryptEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        // provide settings with SHA512 but with the same ID, to test the cache
        final var cachedBcrypt =  cut.getOrCreatePasswordEncoder(new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.SHA_512.name(), Map.of()));
        assertThat(cachedBcrypt).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(bcryptEncoder).isEqualTo(cachedBcrypt);
    }

    @Test
    void should_provide_noop_encoder_with_null_settings() {
        final var noOpEncoder = cut.getOrCreatePasswordEncoder(null);
        assertThat(noOpEncoder).isInstanceOf(NoOpPasswordEncoder.class);
    }

    @Test
    void should_set_expires_at_from_domain_settings(){
        SecretExpirationSettings domainSecretExpirationSettings = new SecretExpirationSettings();
        domainSecretExpirationSettings.setEnabled(Boolean.TRUE);
        domainSecretExpirationSettings.setExpiryTimeSeconds(10000L);
        var secret = cut.generateClientSecret("Toto","africa", new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.NONE.name(), Map.of()), domainSecretExpirationSettings, null);
        compareDates(secret.getExpiresAt(), new Date(secret.getCreatedAt().getTime() + 10000L * 1000L));
    }

    @Test
    void should_not_set_expires_at_when_disabled_in_domain_settings(){
        SecretExpirationSettings domainSecretExpirationSettings = new SecretExpirationSettings();
        domainSecretExpirationSettings.setEnabled(Boolean.FALSE);
        domainSecretExpirationSettings.setExpiryTimeSeconds(10000L);
        var secret = cut.generateClientSecret("Toto","africa", new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.NONE.name(), Map.of()), domainSecretExpirationSettings, null);
        assertThat(secret)
                .isNotNull()
                .hasFieldOrPropertyWithValue("expiresAt", null);
    }

    @Test
    void should_not_set_expires_at_when_expiry_time_is_zero_in_domain_settings(){
        SecretExpirationSettings domainSecretExpirationSettings = new SecretExpirationSettings();
        domainSecretExpirationSettings.setEnabled(Boolean.FALSE);
        domainSecretExpirationSettings.setExpiryTimeSeconds(0L);
        var secret = cut.generateClientSecret("Toto","africa", new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.NONE.name(), Map.of()), domainSecretExpirationSettings, null);
        assertThat(secret)
                .isNotNull()
                .hasFieldOrPropertyWithValue("expiresAt", null);
    }

    @Test
    void should_set_expires_from_application_settings(){
        SecretExpirationSettings domainSecretExpirationSettings = new SecretExpirationSettings();
        domainSecretExpirationSettings.setEnabled(Boolean.TRUE);
        domainSecretExpirationSettings.setExpiryTimeSeconds(10000L);
        SecretExpirationSettings applicationSecretExpirationSettings = new SecretExpirationSettings();
        applicationSecretExpirationSettings.setEnabled(Boolean.TRUE);
        applicationSecretExpirationSettings.setExpiryTimeSeconds(20000L);
        var secret = cut.generateClientSecret("Toto","africa", new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.NONE.name(), Map.of()), domainSecretExpirationSettings, applicationSecretExpirationSettings);
        compareDates(secret.getExpiresAt(), new Date(secret.getCreatedAt().getTime() + 20000L * 1000L));
    }

    @Test
    void should_not_set_expires_when_application_settings_0(){
        SecretExpirationSettings domainSecretExpirationSettings = new SecretExpirationSettings();
        domainSecretExpirationSettings.setEnabled(Boolean.TRUE);
        domainSecretExpirationSettings.setExpiryTimeSeconds(10000L);
        SecretExpirationSettings applicationSecretExpirationSettings = new SecretExpirationSettings();
        applicationSecretExpirationSettings.setEnabled(Boolean.TRUE);
        applicationSecretExpirationSettings.setExpiryTimeSeconds(0L);
        var secret = cut.generateClientSecret("Toto","africa", new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.NONE.name(), Map.of()), domainSecretExpirationSettings, applicationSecretExpirationSettings);
        assertThat(secret)
                .isNotNull()
                .hasFieldOrPropertyWithValue("expiresAt", null);
    }

    @Test
    void should_set_expiration_from_domain_settings_when_application_settings_disabled(){
        SecretExpirationSettings domainSecretExpirationSettings = new SecretExpirationSettings();
        domainSecretExpirationSettings.setEnabled(Boolean.TRUE);
        domainSecretExpirationSettings.setExpiryTimeSeconds(10000L);
        SecretExpirationSettings applicationSecretExpirationSettings = new SecretExpirationSettings();
        applicationSecretExpirationSettings.setEnabled(Boolean.FALSE);
        applicationSecretExpirationSettings.setExpiryTimeSeconds(20000L);
        var secret = cut.generateClientSecret("Toto","africa", new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.NONE.name(), Map.of()), domainSecretExpirationSettings, applicationSecretExpirationSettings);
        compareDates(secret.getExpiresAt(), new Date(secret.getCreatedAt().getTime() + 10000L * 1000L));
    }

    @Test
    void should_validate_secret(){
        Client client = new Client();
        client.setClientId("client-id");
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setSecret("secret");
        clientSecret.setExpiresAt(Date.from(new Date().toInstant().plusSeconds(10000L)));
        clientSecret.setSettingsId("settingsId");
        ApplicationSecretSettings settings = new ApplicationSecretSettings("settingsId", SecretHashAlgorithm.NONE.name(), Map.of());
        client.setClientSecrets(List.of(clientSecret));
        client.setSecretSettings(List.of(settings));
        boolean isValid = cut.validateSecret(client, "secret" );
        assertThat(isValid).isTrue();
    }

    @Test
    void should_not_validate_secret_expired(){
        Client client = new Client();
        client.setClientId("client-id");
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setSecret("secret");
        clientSecret.setExpiresAt(Date.from(new Date().toInstant().minusSeconds(10000L)));
        clientSecret.setSettingsId("settingsId");
        ApplicationSecretSettings settings = new ApplicationSecretSettings("settingsId", SecretHashAlgorithm.NONE.name(), Map.of());
        client.setClientSecrets(List.of(clientSecret));
        client.setSecretSettings(List.of(settings));
        boolean isValid = cut.validateSecret(client, "secret" );
        assertThat(isValid).isFalse();
    }

    private void compareDates(Date date, Date date2){
        assertEquals(date.getTime() / 100, date2.getTime()/100);
    }
}

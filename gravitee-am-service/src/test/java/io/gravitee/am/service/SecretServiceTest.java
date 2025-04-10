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

import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.service.authentication.crypto.password.NoOpPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import io.gravitee.am.service.impl.SecretService;
import io.gravitee.am.service.spring.application.SecretHashAlgorithm;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.gravitee.am.service.spring.application.SecretHashAlgorithm.PropertyKeys.BCRYPT_ROUNDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SecretServiceTest {

    private final SecretService cut = new SecretService();

    @Test
    void should_generate_secret_none_algo() {
        var secret = cut.generateClientSecret("Toto", "africa", new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.NONE.name(), Map.of()));
        assertThat(secret)
                .isNotNull()
                .hasFieldOrPropertyWithValue("secret", "africa")
                .hasFieldOrPropertyWithValue("settingsId", "settingsIdValue");
    }

    @Test
    void should_generate_secret_bcrypt_algo() {
        var secret = cut.generateClientSecret("Toto","africa", new ApplicationSecretSettings("settingsIdValue", SecretHashAlgorithm.BCRYPT.name(), Map.of(BCRYPT_ROUNDS.getKey(), BCRYPT_ROUNDS.getValue())));
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
}

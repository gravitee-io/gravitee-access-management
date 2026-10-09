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
package io.gravitee.am.service.validators.lightweightjwt;

import io.gravitee.am.model.application.ApplicationLightweightJwtSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationLightweightJwtValidatorTest {

    private final ApplicationLightweightJwtValidator validator = new ApplicationLightweightJwtValidator();

    @Test
    void shouldAcceptMissingSettings() {
        assertThat(validator.validate(null)).isEmpty();
        assertThat(validator.validate(new ApplicationOAuthSettings())).isEmpty();
        assertThat(validator.validate(oauthSettings(null, null))).isEmpty();
    }

    @Test
    void shouldAcceptClaimNamesAndDuplicates() {
        assertThat(validator.validate(oauthSettings(List.of("tenant", "tenant"), List.of("email", "x".repeat(128))))).isEmpty();
    }

    @Test
    void shouldRejectBlankAccessTokenClaimName() {
        assertThat(validator.validate(oauthSettings(List.of("tenant", " "), List.of())))
                .hasValue("lightweightJwtSettings.accessTokenAllowlist must not contain a blank claim name");
    }

    @Test
    void shouldRejectNullIdTokenClaimName() {
        assertThat(validator.validate(oauthSettings(List.of(), Arrays.asList("email", null))))
                .hasValue("lightweightJwtSettings.idTokenAllowlist must not contain a blank claim name");
    }

    @Test
    void shouldRejectTooLongClaimName() {
        assertThat(validator.validate(oauthSettings(List.of(), List.of("x".repeat(129)))))
                .hasValue("lightweightJwtSettings.idTokenAllowlist claim names must be at most 128 characters");
    }

    @Test
    void shouldValidateAllowlistsWhenDisabled() {
        ApplicationOAuthSettings settings = oauthSettings(List.of(""), List.of());
        settings.getLightweightJwtSettings().setEnabled(false);

        assertThat(validator.validate(settings)).isPresent();
    }

    private static ApplicationOAuthSettings oauthSettings(List<String> accessTokenAllowlist, List<String> idTokenAllowlist) {
        ApplicationOAuthSettings settings = new ApplicationOAuthSettings();
        settings.setLightweightJwtSettings(ApplicationLightweightJwtSettings.builder()
                .enabled(true)
                .accessTokenAllowlist(accessTokenAllowlist)
                .idTokenAllowlist(idTokenAllowlist)
                .build());
        return settings;
    }
}

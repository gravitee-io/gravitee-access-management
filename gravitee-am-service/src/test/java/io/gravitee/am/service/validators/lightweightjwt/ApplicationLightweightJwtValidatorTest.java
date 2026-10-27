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
    void shouldAcceptClaimNames() {
        assertThat(validator.validate(oauthSettings(List.of("tenant", "https://acme.com/roles", "my claim"), List.of("email", "x".repeat(128))))).isEmpty();
    }

    @Test
    void shouldAcceptSameClaimNameInBothAllowlists() {
        assertThat(validator.validate(oauthSettings(List.of("tenant"), List.of("tenant")))).isEmpty();
    }

    @Test
    void shouldRejectBlankAccessTokenClaimName() {
        assertThat(validator.validate(oauthSettings(List.of("tenant", " "), List.of())))
                .hasValue("lightweightJwtSettings.allowedAccessTokenClaims must not contain a blank claim name");
    }

    @Test
    void shouldRejectNullIdTokenClaimName() {
        assertThat(validator.validate(oauthSettings(List.of(), Arrays.asList("email", null))))
                .hasValue("lightweightJwtSettings.allowedIdTokenClaims must not contain a blank claim name");
    }

    @Test
    void shouldRejectTooLongClaimName() {
        assertThat(validator.validate(oauthSettings(List.of(), List.of("x".repeat(129)))))
                .hasValue("lightweightJwtSettings.allowedIdTokenClaims claim names must be at most 128 characters");
    }

    @Test
    void shouldRejectAccessTokenClaimNameWithSurroundingWhitespace() {
        assertThat(validator.validate(oauthSettings(List.of(" tenant "), List.of())))
                .hasValue("lightweightJwtSettings.allowedAccessTokenClaims claim names must not have leading or trailing whitespace");
    }

    @Test
    void shouldRejectIdTokenClaimNameWithTrailingUnicodeWhitespace() {
        assertThat(validator.validate(oauthSettings(List.of(), List.of("email\u2003"))))
                .hasValue("lightweightJwtSettings.allowedIdTokenClaims claim names must not have leading or trailing whitespace");
    }

    @Test
    void shouldRejectClaimNameWithControlCharacter() {
        assertThat(validator.validate(oauthSettings(List.of("ten\nant"), List.of())))
                .hasValue("lightweightJwtSettings.allowedAccessTokenClaims claim names must not contain control characters");
    }

    @Test
    void shouldRejectDuplicateClaimName() {
        assertThat(validator.validate(oauthSettings(List.of(), List.of("email", "tenant", "email"))))
                .hasValue("lightweightJwtSettings.allowedIdTokenClaims must not contain duplicate claim names");
    }

    @Test
    void shouldValidateAllowlistsWhenDisabled() {
        ApplicationOAuthSettings settings = oauthSettings(List.of(""), List.of());
        settings.getLightweightJwtSettings().setEnabled(false);

        assertThat(validator.validate(settings)).isPresent();
    }

    private static ApplicationOAuthSettings oauthSettings(List<String> allowedAccessTokenClaims, List<String> allowedIdTokenClaims) {
        ApplicationOAuthSettings settings = new ApplicationOAuthSettings();
        settings.setLightweightJwtSettings(ApplicationLightweightJwtSettings.builder()
                .enabled(true)
                .allowedAccessTokenClaims(allowedAccessTokenClaims)
                .allowedIdTokenClaims(allowedIdTokenClaims)
                .build());
        return settings;
    }
}

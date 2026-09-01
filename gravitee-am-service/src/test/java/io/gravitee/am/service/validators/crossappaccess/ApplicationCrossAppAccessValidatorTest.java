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
package io.gravitee.am.service.validators.crossappaccess;

import io.gravitee.am.model.application.ApplicationCrossAppAccessResourceServer;
import io.gravitee.am.model.application.ApplicationCrossAppAccessSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationCrossAppAccessValidatorTest {

    private final ApplicationCrossAppAccessValidator validator = new ApplicationCrossAppAccessValidator();

    @Test
    void shouldAcceptNullSettings() {
        assertThat(validator.validate(null)).isEmpty();
        assertThat(validator.validate(new ApplicationOAuthSettings())).isEmpty();
    }

    @Test
    void shouldIgnoreEverythingWhenDisabled() {
        ApplicationOAuthSettings settings = oauthSettings(false, 0, mapping("rs-1", ""), mapping("rs-1", ""));

        assertThat(validator.validate(settings)).isEmpty();
    }

    @Test
    void shouldAcceptEnabledWithoutResourceServers() {
        assertThat(validator.validate(oauthSettings(true, 300))).isEmpty();
    }

    @Test
    void shouldAcceptResourceServerThatNoLongerExists() {
        assertThat(validator.validate(oauthSettings(true, 300, mapping("rs-gone", "client-1")))).isEmpty();
    }

    @Test
    void shouldRejectIdJagValidityBelowOne() {
        assertThat(validator.validate(oauthSettings(true, 0)))
                .hasValue("idJagValiditySeconds must be at least 1");
    }

    @Test
    void shouldAcceptIdJagValidityOfOne() {
        assertThat(validator.validate(oauthSettings(true, 1))).isEmpty();
    }

    @Test
    void shouldRejectNullResourceServerEntry() {
        ApplicationOAuthSettings settings = oauthSettings(true, 300);
        settings.getCrossAppAccessSettings().setResourceServers(new ArrayList<>(java.util.Collections.singletonList(null)));

        assertThat(validator.validate(settings))
                .hasValue("crossAppAccessSettings.resourceServers must not contain a null entry");
    }

    @Test
    void shouldRejectMissingResourceServerId() {
        assertThat(validator.validate(oauthSettings(true, 300, mapping("  ", "client-1"))))
                .hasValue("crossAppAccessSettings.resourceServers entries must reference a resource server");
    }

    @Test
    void shouldRejectBlankClientId() {
        assertThat(validator.validate(oauthSettings(true, 300, mapping("rs-1", "  "))))
                .hasValue("crossAppAccessSettings.resourceServers entries must have a non-blank clientId");
    }

    @Test
    void shouldRejectClientIdAboveMaxLength() {
        String tooLong = "c".repeat(ApplicationCrossAppAccessResourceServer.CLIENT_ID_MAX_LENGTH + 1);

        assertThat(validator.validate(oauthSettings(true, 300, mapping("rs-1", tooLong))))
                .hasValue("crossAppAccessSettings.resourceServers clientId must be at most 512 characters");
    }

    @Test
    void shouldRejectRepeatedResourceServer() {
        Optional<String> error = validator.validate(
                oauthSettings(true, 300, mapping("rs-1", "client-1"), mapping("rs-1", "client-2")));

        assertThat(error).hasValue("crossAppAccessSettings.resourceServers must not repeat resource server rs-1");
    }

    @Test
    void shouldAcceptDistinctResourceServers() {
        assertThat(validator.validate(
                oauthSettings(true, 300, mapping("rs-1", "client-1"), mapping("rs-2", "client-2")))).isEmpty();
    }

    private static ApplicationCrossAppAccessResourceServer mapping(String resourceServerId, String clientId) {
        return ApplicationCrossAppAccessResourceServer.builder()
                .trustDomainId("td-1")
                .resourceServerId(resourceServerId)
                .clientId(clientId)
                .build();
    }

    private static ApplicationOAuthSettings oauthSettings(boolean enabled,
                                                          int idJagValiditySeconds,
                                                          ApplicationCrossAppAccessResourceServer... resourceServers) {
        List<ApplicationCrossAppAccessResourceServer> mappings = new ArrayList<>(Arrays.asList(resourceServers));
        ApplicationOAuthSettings settings = new ApplicationOAuthSettings();
        settings.setIdJagValiditySeconds(idJagValiditySeconds);
        settings.setCrossAppAccessSettings(ApplicationCrossAppAccessSettings.builder()
                .enabled(enabled)
                .resourceServers(mappings)
                .build());
        return settings;
    }
}

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
package io.gravitee.am.repository.jdbc.management.api.model.mapper;

import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.application.ApplicationSettings;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class ApplicationSettingsConverterTest {

    private final ApplicationSettingsConverter converter = new ApplicationSettingsConverter();

    @Test
    public void shouldDropTokenClaimWithUnknownTokenType() {
        String json = """
                {"oauth":{"clientId":"client","tokenCustomClaims":[
                  {"tokenType":"ID_JAG","claimName":"jag-claim","claimValue":"jag-value"}
                ]}}""";

        ApplicationSettings settings = converter.convertFrom(json, null);

        assertThat(settings.getOauth().getClientId()).isEqualTo("client");
        assertThat(settings.getOauth().getTokenCustomClaims()).isEmpty();
    }

    @Test
    public void shouldKeepKnownTokenClaims() {
        String json = """
                {"oauth":{"tokenCustomClaims":[
                  {"tokenType":"ACCESS_TOKEN","claimName":"at-claim","claimValue":"at-value"},
                  {"tokenType":"ID_JAG","claimName":"jag-claim","claimValue":"jag-value"},
                  {"tokenType":"ID_TOKEN","claimName":"id-claim","claimValue":"id-value"},
                  {"tokenType":"REFRESH_TOKEN","claimName":"rt-claim","claimValue":"rt-value"}
                ]}}""";

        ApplicationSettings settings = converter.convertFrom(json, null);

        assertThat(settings.getOauth().getTokenCustomClaims())
                .extracting(TokenClaim::getTokenType, TokenClaim::getClaimName, TokenClaim::getClaimValue)
                .containsExactly(
                        tuple(TokenTypeHint.ACCESS_TOKEN, "at-claim", "at-value"),
                        tuple(TokenTypeHint.ID_TOKEN, "id-claim", "id-value"),
                        tuple(TokenTypeHint.REFRESH_TOKEN, "rt-claim", "rt-value"));
    }

    @Test
    public void shouldPersistOnlyKnownTokenClaimsOnSave() {
        String json = """
                {"oauth":{"tokenCustomClaims":[
                  {"tokenType":"ID_JAG","claimName":"jag-claim","claimValue":"jag-value"},
                  {"tokenType":"ACCESS_TOKEN","claimName":"at-claim","claimValue":"at-value"}
                ]}}""";

        String saved = converter.convertTo(converter.convertFrom(json, null), null);

        assertThat(saved).contains("at-claim").doesNotContain("ID_JAG").doesNotContain("jag-claim");
    }
}

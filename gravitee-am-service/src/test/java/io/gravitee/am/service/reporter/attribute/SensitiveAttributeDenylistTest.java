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
package io.gravitee.am.service.reporter.attribute;

import io.gravitee.am.model.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class SensitiveAttributeDenylistTest {

    private static SensitiveAttributeDenylist configuredWith(String... deniedAttributes) {
        MockEnvironment environment = new MockEnvironment();
        for (int i = 0; i < deniedAttributes.length; i++) {
            environment.setProperty(SensitiveAttributeDenylist.DENIED_ATTRIBUTE_KEY.formatted(i), deniedAttributes[i]);
        }
        SensitiveAttributeDenylist denylist = new SensitiveAttributeDenylist();
        ReflectionTestUtils.setField(denylist, "environment", environment);
        denylist.afterPropertiesSet();
        return denylist;
    }

    private final SensitiveAttributeDenylist denylist = configuredWith();

    @Nested
    class Baseline {

        @ParameterizedTest
        @ValueSource(strings = {
                "op_access_token", "op_id_token", "access_token", "refresh_token", "id_token", "token",
                "password", "client_secret", "secret", "private_key", "api_key", "credentials",
                "otp_code", "mfa_code", "recovery_code", "client_assertion", "code_verifier"})
        void deniesAKnownCredential(String name) {
            assertThat(denylist.isDenied(name)).isTrue();
        }

        @Test
        void appliesWithoutAnySpringContext() {
            assertThat(new SensitiveAttributeDenylist().isDenied("client_secret")).isTrue();
        }

        @Test
        void coversEveryNameTheUserModelAlreadyTreatsAsSensitive() {
            assertThat(User.SENSITIVE_ADDITIONAL_PROPERTIES).allSatisfy(name ->
                    assertThat(denylist.isDenied(name)).isTrue());
        }
    }

    @Nested
    class Normalization {

        @ParameterizedTest
        @ValueSource(strings = {"client_secret", "clientSecret", "CLIENT-SECRET", "ClientSecret", "client-Secret"})
        void treatsSeparatorsAndCaseAsOneName(String spelling) {
            assertThat(denylist.isDenied(spelling)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        void anAbsentNameIsNotDenied(String name) {
            assertThat(denylist.isDenied(name)).isFalse();
        }
    }

    @Nested
    class Suffixes {

        @ParameterizedTest
        @ValueSource(strings = {"azure_b2c_refresh_token", "vendorApiKey", "my_private_key", "user_passwd", "legacy_credentials"})
        void aVendorPrefixDoesNotSmuggleACredentialOut(String name) {
            assertThat(denylist.isDenied(name)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"secretary_id", "tokenizer_version"})
        void aNameThatMerelyContainsOneIsNotDenied(String name) {
            assertThat(denylist.isDenied(name)).isFalse();
        }
    }

    @Nested
    class OrdinaryAttributesSurvive {

        @ParameterizedTest
        @ValueSource(strings = {
                "department_code", "login_count", "employeeId", "sub", "email", "given_name",
                "token_exchange", "subject_token_id", "client_id", "delegation", "last_identity"})
        void isNotDenied(String name) {
            assertThat(denylist.isDenied(name)).isFalse();
        }
    }

    @Nested
    class OperatorAdditions {

        @Test
        void areDeniedAlongsideTheBaseline() {
            var extended = configuredWith("azure_b2c_internal", "legacy_session_key");

            assertThat(extended.isDenied("azure_b2c_internal")).isTrue();
            assertThat(extended.isDenied("legacy_session_key")).isTrue();
            assertThat(extended.isDenied("client_secret")).isTrue();
        }

        @Test
        void areNormalizedLikeTheBaseline() {
            var extended = configuredWith("azure_b2c_internal");

            assertThat(extended.isDenied("azureB2cInternal")).isTrue();
        }

        @Test
        void cannotRemoveABaselineName() {
            var extended = configuredWith("department_code");

            assertThat(extended.isDenied("client_secret")).isTrue();
            assertThat(extended.isDenied("department_code")).isTrue();
        }

        @Test
        void stopAtTheFirstGapInTheIndexedKeys() {
            MockEnvironment environment = new MockEnvironment();
            environment.setProperty(SensitiveAttributeDenylist.DENIED_ATTRIBUTE_KEY.formatted(0), "first");
            environment.setProperty(SensitiveAttributeDenylist.DENIED_ATTRIBUTE_KEY.formatted(2), "third");
            SensitiveAttributeDenylist denylist = new SensitiveAttributeDenylist();
            ReflectionTestUtils.setField(denylist, "environment", environment);
            denylist.afterPropertiesSet();

            assertThat(denylist.isDenied("first")).isTrue();
            assertThat(denylist.isDenied("third")).isFalse();
        }
    }

    @Nested
    class Scrubbing {

        @Test
        void dropsTheDeniedKeys() {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("employeeId", "E-4471");
            attributes.put("op_access_token", "OP-AT");
            attributes.put("department_code", "PLT");

            assertThat(denylist.scrubbedCopyOf(attributes)).containsOnlyKeys("employeeId", "department_code");
        }

        @Test
        void dropsADeniedKeyNestedInsideAnotherClaim() {
            Map<String, Object> attributes = Map.of("idp", Map.of("access_token", "SECRET-AT", "name", "Acme IdP"));

            var scrubbed = denylist.scrubbedCopyOf(attributes);

            assertThat(scrubbed).extracting("idp").isEqualTo(Map.of("name", "Acme IdP"));
        }

        @Test
        void dropsADeniedKeyNestedInsideACollection() {
            Map<String, Object> attributes = Map.of("identities", List.of(
                    Map.of("provider", "acme", "refresh_token", "RT-XYZ")));

            var scrubbed = denylist.scrubbedCopyOf(attributes);

            assertThat(scrubbed).extracting("identities").isEqualTo(List.of(Map.of("provider", "acme")));
        }

        @Test
        void leavesTheSourceMapUntouched() {
            Map<String, Object> nested = new LinkedHashMap<>();
            nested.put("access_token", "SECRET-AT");
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("idp", nested);

            denylist.scrubbedCopyOf(attributes);

            assertThat(attributes).containsKey("idp");
            assertThat(nested).containsEntry("access_token", "SECRET-AT");
        }

        @Test
        void omitsAStructureTooDeepToWalk() {
            Map<String, Object> attributes = new LinkedHashMap<>(Map.of("access_token", "SECRET-AT"));
            for (int i = 0; i < 20; i++) {
                attributes = new LinkedHashMap<>(Map.of("level", attributes));
            }

            assertThat(denylist.scrubbedCopyOf(attributes).toString()).doesNotContain("SECRET-AT");
        }

        @Test
        void keepsAStructureWithinTheDepthLimit() {
            Map<String, Object> attributes = Map.of("a", Map.of("b", Map.of("c", "kept", "access_token", "SECRET-AT")));

            var scrubbed = denylist.scrubbedCopyOf(attributes);

            assertThat(scrubbed.toString()).contains("kept").doesNotContain("SECRET-AT");
        }

        @Test
        void toleratesAnAbsentMap() {
            assertThat(denylist.scrubbedCopyOf(null)).isNull();
            assertThat(denylist.scrubbedCopyOf(new HashMap<>())).isEmpty();
        }
    }
}

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

import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.ReporterAttributeMapping;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEnrichmentContext;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class ReporterAttributeResolverTest {

    private final ReporterAttributeResolver resolver = new ReporterAttributeResolver(new SensitiveAttributeDenylist());

    private static ReporterAttributeMapping mapping(String expression, String exportedName) {
        return new ReporterAttributeMapping(expression, exportedName);
    }

    private static User user() {
        User user = new User();
        user.setId("user-1");
        user.setUsername("jsmith");
        user.setEmail("jsmith@example.com");
        user.setFirstName("Jane");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domain-1");
        user.setSource("ldap-idp");
        Map<String, Object> additional = new HashMap<>();
        additional.put("sub", "external-sub");
        additional.put("employeeId", "E-4471");
        additional.put("department", "Platform");
        additional.put("loginCount", 12);
        user.setAdditionalInformation(additional);
        user.setPassword("s3cret");
        return user;
    }

    private static Client client() {
        Client client = new Client();
        client.setId("client-internal-id");
        client.setClientId("my-app");
        client.setClientName("My Application");
        return client;
    }

    /** An audit as it looks after AuditBuilder has captured the user and client behind the event. */
    private static Audit audit() {
        Audit audit = new Audit();
        audit.setId("audit-1");
        audit.setType("USER_LOGIN");
        audit.setTransactionId("txn-1");

        AuditAccessPoint accessPoint = new AuditAccessPoint();
        accessPoint.setIpAddress("10.0.0.9");
        accessPoint.setUserAgent("Mozilla/5.0");
        audit.setAccessPoint(accessPoint);

        AuditOutcome outcome = new AuditOutcome();
        outcome.setStatus(Status.SUCCESS);
        audit.setOutcome(outcome);

        audit.setEnrichmentContext(new AuditEnrichmentContext(user(), client()));
        return audit;
    }

    @Nested
    class NothingToDo {

        @ParameterizedTest
        @NullAndEmptySource
        void noMappingsResolvesToNothing(List<ReporterAttributeMapping> mappings) {
            assertThat(resolver.resolve(mappings, audit())).isEmpty();
        }

        @Test
        void nullAuditResolvesToNothing() {
            assertThat(resolver.resolve(List.of(mapping("{#context.attributes['user'].email}", "e")), null)).isEmpty();
        }

        @Test
        void anAuditWithoutAnEnrichmentContextStillResolvesWhatItCan() {
            Audit audit = audit();
            audit.setEnrichmentContext(null);

            var resolved = resolver.resolve(List.of(
                    mapping("{#context.attributes['user'].email}", "user_email"),
                    mapping("{#context.attributes['audit'].type}", "audit_type")), audit);

            assertThat(resolved).containsExactly(Map.entry("audit_type", "USER_LOGIN"));
        }
    }

    @Nested
    class SupportedSources {

        @Test
        void resolvesATopLevelUserAttribute() {
            var resolved = resolver.resolve(List.of(mapping("{#context.attributes['user'].email}", "user_email")), audit());

            assertThat(resolved).containsExactly(Map.entry("user_email", "jsmith@example.com"));
        }

        @Test
        void resolvesANestedCustomUserAttribute() {
            var resolved = resolver.resolve(
                    List.of(mapping("{#context.attributes['user'].additionalInformation['employeeId']}", "employee_id")), audit());

            assertThat(resolved).containsExactly(Map.entry("employee_id", "E-4471"));
        }

        @Test
        void resolvesAClientAttribute() {
            var resolved = resolver.resolve(List.of(
                    mapping("{#context.attributes['client'].clientId}", "application_id"),
                    mapping("{#context.attributes['client'].name}", "application_name")), audit());

            assertThat(resolved).containsOnly(
                    Map.entry("application_id", "my-app"),
                    Map.entry("application_name", "My Application"));
        }

        @Test
        void resolvesTheRequestContext() {
            var resolved = resolver.resolve(List.of(
                    mapping("{#context.attributes['request'].ip}", "source_ip"),
                    mapping("{#context.attributes['request'].userAgent}", "ua")), audit());

            assertThat(resolved).containsOnly(
                    Map.entry("source_ip", "10.0.0.9"),
                    Map.entry("ua", "Mozilla/5.0"));
        }

        @Test
        void resolvesTheAuditItself() {
            var resolved = resolver.resolve(List.of(
                    mapping("{#context.attributes['audit'].transactionId}", "txn"),
                    mapping("{#context.attributes['audit'].status}", "result")), audit());

            assertThat(resolved).containsOnly(
                    Map.entry("txn", "txn-1"),
                    Map.entry("result", Status.SUCCESS));
        }

        @Test
        void theIdentityProviderIsReachableAsTheUserSource() {
            var resolved = resolver.resolve(List.of(mapping("{#context.attributes['user'].source}", "idp")), audit());

            assertThat(resolved).containsExactly(Map.entry("idp", "ldap-idp"));
        }
    }

    @Nested
    class Renaming {

        @Test
        void theValueIsExportedUnderTheConfiguredName() {
            var resolved = resolver.resolve(
                    List.of(mapping("{#context.attributes['user'].additionalInformation['department']}", "department_name")), audit());

            assertThat(resolved).containsOnlyKeys("department_name");
        }

        @Test
        void oneSourceCanBeExportedTwiceUnderDifferentNames() {
            var resolved = resolver.resolve(List.of(
                    mapping("{#context.attributes['user'].email}", "email"),
                    mapping("{#context.attributes['user'].email}", "contact_email")), audit());

            assertThat(resolved).containsOnly(
                    Map.entry("email", "jsmith@example.com"),
                    Map.entry("contact_email", "jsmith@example.com"));
        }

        @Test
        void declarationOrderIsPreserved() {
            var resolved = resolver.resolve(List.of(
                    mapping("{#context.attributes['user'].username}", "a_username"),
                    mapping("{#context.attributes['user'].email}", "b_email"),
                    mapping("{#context.attributes['client'].clientId}", "c_client")), audit());

            assertThat(resolved.keySet()).containsExactly("a_username", "b_email", "c_client");
        }
    }

    @Nested
    class NonStringValues {

        @Test
        void keepTheirNativeType() {
            var resolved = resolver.resolve(
                    List.of(mapping("{#context.attributes['user'].additionalInformation['loginCount']}", "login_count")), audit());

            assertThat(resolved).containsExactly(Map.entry("login_count", 12));
        }

        @Test
        void aCollectionResolvesAsACollection() {
            User user = user();
            user.setGroups(List.of("admins", "platform"));
            Audit audit = audit();
            audit.setEnrichmentContext(new AuditEnrichmentContext(user, client()));

            var resolved = resolver.resolve(List.of(mapping("{#context.attributes['user'].groups}", "groups")), audit);

            assertThat(resolved.get("groups")).isEqualTo(List.of("admins", "platform"));
        }
    }

    @Nested
    class NeverFailsTheEvent {

        @Test
        void aMissingAttributeIsOmittedRatherThanExportedAsNull() {
            var resolved = resolver.resolve(
                    List.of(mapping("{#context.attributes['user'].additionalInformation['nope']}", "missing")), audit());

            assertThat(resolved).isEmpty();
        }

        @Test
        void anUnknownRootKeyIsOmitted() {
            var resolved = resolver.resolve(List.of(mapping("{#context.attributes['nope'].whatever}", "missing")), audit());

            assertThat(resolved).isEmpty();
        }

        /** Navigating off an absent root throws, so the whole mapping is lost. */
        @Test
        void anAbsentRootTakesTheLiteralPartsWithIt() {
            Audit audit = audit();
            audit.setEnrichmentContext(null);

            var resolved = resolver.resolve(
                    List.of(mapping("EMPLOYEE#{#context.attributes['user'].additionalInformation['employeeId']}", "badge")), audit);

            assertThat(resolved).isEmpty();
        }

        @Test
        void safeNavigationKeepsTheLiteralPartsWhenTheRootIsAbsent() {
            Audit audit = audit();
            audit.setEnrichmentContext(null);

            var resolved = resolver.resolve(List.of(mapping(
                    "EMPLOYEE#{#context.attributes['user']?.additionalInformation?.get('employeeId')}", "badge")), audit);

            assertThat(resolved).containsExactly(Map.entry("badge", "EMPLOYEE#"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{#context.attributes['user'.email}",
                "{#context.attributes['user'].email()}",
                "{#1/0}"
        })
        void aMalformedExpressionIsOmitted(String expression) {
            assertThat(resolver.resolve(List.of(mapping(expression, "broken")), audit())).isEmpty();
        }

        @Test
        void oneBadExpressionDoesNotCostTheOthers() {
            var resolved = resolver.resolve(List.of(
                    mapping("{#context.attributes['user'].email}", "good_before"),
                    mapping("{#context.attributes['user'.email}", "broken"),
                    mapping("{#context.attributes['client'].clientId}", "good_after")), audit());

            assertThat(resolved).containsOnly(
                    Map.entry("good_before", "jsmith@example.com"),
                    Map.entry("good_after", "my-app"));
        }

        @Test
        void aMappingMissingEitherHalfIsSkipped() {
            var resolved = resolver.resolve(java.util.Arrays.asList(
                    null,
                    mapping(null, "no_expression"),
                    mapping("{#context.attributes['user'].email}", null),
                    mapping("{#context.attributes['user'].username}", "fine")), audit());

            assertThat(resolved).containsExactly(Map.entry("fine", "jsmith"));
        }
    }

    /**
     * The engine treats anything outside {@code {#...}} as template text.
     */
    @Nested
    class TemplateText {

        @Test
        void interpolatesALiteralPrefix() {
            var resolved = resolver.resolve(List.of(mapping(
                    "tenant-{#context.attributes['user'].additionalInformation['department']}", "tenant")), audit());

            assertThat(resolved).containsExactly(Map.entry("tenant", "tenant-Platform"));
        }

        @Test
        void interpolatesALiteralSuffix() {
            var resolved = resolver.resolve(List.of(mapping(
                    "{#context.attributes['user'].additionalInformation['department']}-team", "team")), audit());

            assertThat(resolved).containsExactly(Map.entry("team", "Platform-team"));
        }

        @Test
        void exportsAPlainConstant() {
            var resolved = resolver.resolve(List.of(mapping("production", "environment")), audit());

            assertThat(resolved).containsExactly(Map.entry("environment", "production"));
        }

        @Test
        void interpolatesLiteralTextBetweenTwoExpressions() {
            var resolved = resolver.resolve(List.of(mapping(
                    "{#context.attributes['user'].firstName} of {#context.attributes['user'].additionalInformation['department']}",
                    "who")), audit());

            assertThat(resolved).containsExactly(Map.entry("who", "Jane of Platform"));
        }

        @Test
        void combinesTwoExpressionsInOneValue() {
            var resolved = resolver.resolve(List.of(mapping(
                    "{#context.attributes['user'].username}@{#context.attributes['client'].clientId}", "who")), audit());

            assertThat(resolved).containsExactly(Map.entry("who", "jsmith@my-app"));
        }

        @Test
        void aConstantIsExportedVerbatimBracesIncluded() {
            var resolved = resolver.resolve(List.of(mapping("{prod}", "environment")), audit());

            assertThat(resolved).containsExactly(Map.entry("environment", "{prod}"));
        }

        /** The engine renders a null leaf as empty text, so the literal parts survive. */
        @Test
        void aMissingLeafStillLeavesTheLiteralParts() {
            var resolved = resolver.resolve(
                    List.of(mapping("EMPLOYEE#{#context.attributes['user'].additionalInformation['absent']}", "badge")), audit());

            assertThat(resolved).containsExactly(Map.entry("badge", "EMPLOYEE#"));
        }

        @Test
        void anExpressionMissingItsHashIsExportedAsItsOwnText() {
            var resolved = resolver.resolve(List.of(mapping("{context.attributes['user'].email}", "user_email")), audit());

            assertThat(resolved).containsExactly(Map.entry("user_email", "{context.attributes['user'].email}"));
        }
    }

    @Nested
    class SensitiveValuesAreUnreachable {

        @Test
        void thePasswordIsNotExposedOnTheUserProjection() {
            var resolved = resolver.resolve(List.of(mapping("{#context.attributes['user'].password}", "pwd")), audit());

            assertThat(resolved).isEmpty();
        }

        @Test
        void theClientSecretIsNotExposedOnTheClientProjection() {
            Client client = client();
            client.setClientSecret("super-secret");
            Audit audit = audit();
            audit.setEnrichmentContext(new AuditEnrichmentContext(user(), client));

            var resolved = resolver.resolve(List.of(mapping("{#context.attributes['client'].clientSecret}", "secret")), audit);

            assertThat(resolved).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{#context.attributes['user'].class}",
                "{#context.attributes['user'].getClass()}",
                "{#context.attributes['user'].class.classLoader}",
                "{T(java.lang.Runtime).getRuntime()}",
                "{T(java.lang.System).getenv()}",
                "{#context.attributes['user'].getAdditionalInformation()}"})
        void theEngineDoesNotReachBeyondTheProjection(String expression) {
            var resolved = resolver.resolve(List.of(mapping(expression, "escaped")), audit());

            assertThat(resolved).isEmpty();
        }
    }

    @Nested
    class DeniedAttributes {

        private Audit auditWithSensitiveClaims() {
            User user = user();
            user.getAdditionalInformation().put("refresh_token", "RT-XYZ");
            user.getAdditionalInformation().put("op_access_token", "OP-AT");

            UserIdentity identity = new UserIdentity();
            identity.setProviderId("idp-1");
            Map<String, Object> identityInformation = new HashMap<>();
            identityInformation.put("refresh_token", "ID-RT");
            identityInformation.put("department_code", "PLT");
            identity.setAdditionalInformation(identityInformation);
            user.setIdentities(List.of(identity));
            user.setLastIdentityUsed("idp-1");

            Client client = client();
            client.setMetadata(new HashMap<>(Map.of("internal_api_key", "META-SECRET", "tier", "gold")));

            Audit audit = audit();
            audit.setEnrichmentContext(new AuditEnrichmentContext(user, client));
            return audit;
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{#context.attributes['user'].claims['refresh_token']}",
                "{#context.attributes['user'].additionalInformation['refresh_token']}",
                "{#context.attributes['user']['claims']['refresh_token']}",
                "{#context.attributes['user'].claims['refresh'+'_token']}",
                "{#context.attributes['user'].claims['op_access_token']}",
                "{#context.attributes['user'].identities[0].additionalInformation['refresh_token']}",
                "{#context.attributes['client'].metadata['internal_api_key']}"})
        void areNotResolvableHoweverTheyAreSpelled(String expression) {
            var resolved = resolver.resolve(List.of(mapping(expression, "leaked")), auditWithSensitiveClaims());

            assertThat(resolved).isEmpty();
        }

        @Test
        void aDeniedKeyNestedInsideAClaimIsNotReachable() {
            User user = user();
            user.getAdditionalInformation().put("idp", new HashMap<>(Map.of(
                    "access_token", "SECRET-AT",
                    "name", "Acme IdP")));
            Audit audit = audit();
            audit.setEnrichmentContext(new AuditEnrichmentContext(user, client()));

            var resolved = resolver.resolve(List.of(
                    mapping("{#context.attributes['user']['claims']['idp']['access_token']}", "nested_secret"),
                    mapping("{#context.attributes['user'].claims['idp']['name']}", "idp_name")), audit);

            assertThat(resolved).containsOnly(Map.entry("idp_name", "Acme IdP"));
        }

        @Test
        void leaveTheirNonSensitiveSiblingsAlone() {
            var resolved = resolver.resolve(List.of(
                    mapping("{#context.attributes['user'].identities[0].additionalInformation['department_code']}", "department"),
                    mapping("{#context.attributes['client'].metadata['tier']}", "tier")), auditWithSensitiveClaims());

            assertThat(resolved).containsOnly(Map.entry("department", "PLT"), Map.entry("tier", "gold"));
        }

        @Test
        void leaveTheSurvivingClaimsReadableUnderBothNames() {
            var resolved = resolver.resolve(List.of(
                    mapping("{#context.attributes['user'].claims['employeeId']}", "from_claims"),
                    mapping("{#context.attributes['user'].additionalInformation['employeeId']}", "from_additional")),
                    auditWithSensitiveClaims());

            assertThat(resolved).containsOnly(
                    Map.entry("from_claims", "E-4471"),
                    Map.entry("from_additional", "E-4471"));
        }

        @Test
        void aProjectionThatCannotBeFilteredIsWithheldEntirely() {
            var failing = new SensitiveAttributeDenylist() {
                @Override
                public Map<String, Object> scrubbedCopyOf(Map<String, ?> attributes) {
                    throw new UnsupportedOperationException("unfilterable");
                }
            };
            var strictResolver = new ReporterAttributeResolver(failing);

            var resolved = strictResolver.resolve(List.of(
                    mapping("{#context.attributes['user'].email}", "email"),
                    mapping("{#context.attributes['audit'].type}", "event_type")), auditWithSensitiveClaims());

            assertThat(resolved).containsOnlyKeys("event_type");
        }
    }

    @Nested
    class OnlySingleValuesAreExported {

        @ParameterizedTest
        @ValueSource(strings = {
                "{#context.attributes['user'].claims}",
                "{#context.attributes['user'].additionalInformation}",
                "{#context.attributes['user'].lastIdentityInformation}",
                "{#context.attributes['user'].identitiesAsMap}",
                "{#context.attributes['user'].identities}",
                "{#context.attributes['user'].rolesPermissions}",
                "{#context.attributes['client'].metadata}"})
        void aStructureIsDropped(String expression) {
            User user = user();
            UserIdentity identity = new UserIdentity();
            identity.setProviderId("idp-1");
            identity.setAdditionalInformation(new HashMap<>(Map.of("employeeId", "E-4471")));
            user.setIdentities(List.of(identity));
            user.setLastIdentityUsed("idp-1");
            Role role = new Role();
            role.setId("role-1");
            role.setName("ADMIN");
            user.setRolesPermissions(Set.of(role));
            Client client = client();
            client.setMetadata(new HashMap<>(Map.of("tier", "gold")));
            Audit audit = audit();
            audit.setEnrichmentContext(new AuditEnrichmentContext(user, client));

            var resolved = resolver.resolve(List.of(mapping(expression, "dumped")), audit);

            assertThat(resolved).isEmpty();
        }

        @Test
        void aCollectionOfScalarsIsKept() {
            User user = user();
            user.setGroups(List.of("admins", "platform"));
            Audit audit = audit();
            audit.setEnrichmentContext(new AuditEnrichmentContext(user, client()));

            var resolved = resolver.resolve(List.of(mapping("{#context.attributes['user'].groups}", "groups")), audit);

            assertThat(resolved).containsOnlyKeys("groups");
        }

        @Test
        void oneStructureDoesNotCostTheScalarsBesideIt() {
            var resolved = resolver.resolve(List.of(
                    mapping("{#context.attributes['user'].claims}", "everything"),
                    mapping("{#context.attributes['user'].email}", "email")), audit());

            assertThat(resolved).containsOnlyKeys("email");
        }
    }

}

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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.ReporterAttributeMapping;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditEnrichmentContext;
import io.gravitee.am.reporter.api.provider.ReportableCriteria;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.reporter.api.Reportable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author GraviteeSource Team
 */
class AttributeMappingReporterTest {

    private static final String EMAIL_EXPRESSION = "{#context.attributes['user'].email}";
    private static final String CLIENT_EXPRESSION = "{#context.attributes['client'].clientId}";

    private static class CapturingReporter implements Reporter<Audit, ReportableCriteria> {
        private final List<Reportable> reported = new ArrayList<>();

        @Override
        public void report(Reportable reportable) {
            reported.add(reportable);
        }

        @Override
        public io.reactivex.rxjava3.core.Single<io.gravitee.am.model.common.Page<Audit>> search(
                ReferenceType referenceType, String referenceId, ReportableCriteria criteria, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.reactivex.rxjava3.core.Single<Map<Object, Object>> aggregate(
                ReferenceType referenceType, String referenceId, ReportableCriteria criteria,
                io.gravitee.am.common.analytics.Type analyticsType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.reactivex.rxjava3.core.Maybe<Audit> findById(ReferenceType referenceType, String referenceId, String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canSearch() {
            return false;
        }

        @Override
        public Reporter<Audit, ReportableCriteria> start() {
            return this;
        }

        @Override
        public Reporter<Audit, ReportableCriteria> stop() {
            return this;
        }

        @Override
        public io.gravitee.common.component.Lifecycle.State lifecycleState() {
            return io.gravitee.common.component.Lifecycle.State.STARTED;
        }

        Audit onlyAudit() {
            assertThat(reported).hasSize(1);
            return (Audit) reported.get(0);
        }
    }

    private static ReporterAttributeMapping mapping(String expression, String exportedName) {
        return new ReporterAttributeMapping(expression, exportedName);
    }

    private static Audit auditWithContext() {
        User user = new User();
        user.setEmail("jsmith@example.com");
        user.setAdditionalInformation(new HashMap<>(Map.of("sub", "external-sub")));

        Client client = new Client();
        client.setClientId("my-app");

        Audit audit = new Audit();
        audit.setId("audit-1");
        audit.setType("USER_LOGIN");
        audit.setEnrichmentContext(new AuditEnrichmentContext(user, client));
        return audit;
    }

    @Nested
    class NotWrappedWhenThereIsNothingToApply {

        @ParameterizedTest
        @NullAndEmptySource
        void decorateReturnsTheDelegateItself(List<ReporterAttributeMapping> mappings) {
            var delegate = new CapturingReporter();

            assertThat(AttributeMappingReporter.decorate(delegate, mappings)).isSameAs(delegate);
        }
    }

    @Nested
    class Enrichment {

        @Test
        void theReporterReceivesTheResolvedAttributes() {
            var delegate = new CapturingReporter();
            var reporter = AttributeMappingReporter.decorate(delegate,
                    List.of(mapping(EMAIL_EXPRESSION, "user_email"), mapping(CLIENT_EXPRESSION, "application_id")));

            reporter.report(auditWithContext());

            assertThat(delegate.onlyAudit().getCustomAttributes()).containsOnly(
                    Map.entry("user_email", "jsmith@example.com"),
                    Map.entry("application_id", "my-app"));
        }

        @Test
        void theRestOfTheAuditIsCarriedThrough() {
            var delegate = new CapturingReporter();
            var reporter = AttributeMappingReporter.decorate(delegate, List.of(mapping(EMAIL_EXPRESSION, "user_email")));
            var original = auditWithContext();

            reporter.report(original);

            var delivered = delegate.onlyAudit();
            assertThat(delivered.getId()).isEqualTo(original.getId());
            assertThat(delivered.getType()).isEqualTo(original.getType());
        }

        @Test
        void anAuditWithNothingResolvableIsPassedStraightThrough() {
            var delegate = new CapturingReporter();
            var reporter = AttributeMappingReporter.decorate(delegate,
                    List.of(mapping("{#context.attributes['user'].additionalInformation['absent']}", "nope")));
            var original = auditWithContext();

            reporter.report(original);

            assertThat(delegate.onlyAudit()).isSameAs(original);
            assertThat(original.getCustomAttributes()).isNull();
        }

        @Test
        void somethingThatIsNotAnAuditIsPassedStraightThrough() {
            var delegate = new CapturingReporter();
            var reporter = AttributeMappingReporter.decorate(delegate, List.of(mapping(EMAIL_EXPRESSION, "user_email")));
            var other = mock(Reportable.class);

            reporter.report(other);

            assertThat(delegate.reported).containsExactly(other);
        }
    }

    @Nested
    class BrokenProjection {

        /** A non-string {@code locale} with no preferredLanguage makes {@code new UserProperties(..)} throw. */
        private Audit auditWithUnprojectableUser() {
            User user = new User();
            user.setEmail("jsmith@example.com");
            Map<String, Object> additional = new HashMap<>();
            additional.put("locale", Map.of("lang", "en"));
            user.setAdditionalInformation(additional);

            Client client = new Client();
            client.setClientId("my-app");

            Audit audit = new Audit();
            audit.setId("audit-1");
            audit.setType("USER_LOGIN");
            audit.setEnrichmentContext(new AuditEnrichmentContext(user, client));
            return audit;
        }

        @Test
        void theEventStillReachesTheReporter() {
            var delegate = new CapturingReporter();
            var reporter = AttributeMappingReporter.decorate(delegate, List.of(mapping(CLIENT_EXPRESSION, "application_id")));

            reporter.report(auditWithUnprojectableUser());

            assertThat(delegate.reported).hasSize(1);
        }

        @Test
        void mappingsThatDoNotTouchTheUserStillResolve() {
            var delegate = new CapturingReporter();
            var reporter = AttributeMappingReporter.decorate(delegate, List.of(mapping(CLIENT_EXPRESSION, "application_id")));

            reporter.report(auditWithUnprojectableUser());

            assertThat(delegate.onlyAudit().getCustomAttributes()).containsExactly(Map.entry("application_id", "my-app"));
        }
    }

    @Nested
    class PerReporterIsolation {

        @Test
        void twoReportersOverOneAuditEachSeeOnlyTheirOwnAttributes() {
            var first = new CapturingReporter();
            var second = new CapturingReporter();
            var firstReporter = AttributeMappingReporter.decorate(first, List.of(mapping(EMAIL_EXPRESSION, "user_email")));
            var secondReporter = AttributeMappingReporter.decorate(second, List.of(mapping(CLIENT_EXPRESSION, "application_id")));

            var shared = auditWithContext();
            firstReporter.report(shared);
            secondReporter.report(shared);

            assertThat(first.onlyAudit().getCustomAttributes()).containsOnlyKeys("user_email");
            assertThat(second.onlyAudit().getCustomAttributes()).containsOnlyKeys("application_id");
        }

        @Test
        void theSharedAuditIsNeverMutated() {
            var delegate = new CapturingReporter();
            var reporter = AttributeMappingReporter.decorate(delegate, List.of(mapping(EMAIL_EXPRESSION, "user_email")));
            var shared = auditWithContext();

            reporter.report(shared);

            assertThat(shared.getCustomAttributes()).isNull();
            assertThat(delegate.onlyAudit()).isNotSameAs(shared);
        }

        @Test
        void aReporterWithoutMappingsIsUnaffectedByOneWithThem() {
            var enriched = new CapturingReporter();
            var plain = new CapturingReporter();
            var enrichedReporter = AttributeMappingReporter.decorate(enriched, List.of(mapping(EMAIL_EXPRESSION, "user_email")));
            var plainReporter = AttributeMappingReporter.decorate(plain, List.of());

            var shared = auditWithContext();
            enrichedReporter.report(shared);
            plainReporter.report(shared);

            assertThat(enriched.onlyAudit().getCustomAttributes()).containsOnlyKeys("user_email");
            assertThat(plain.onlyAudit().getCustomAttributes()).isNull();
        }
    }
}

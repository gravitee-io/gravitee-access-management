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
package io.gravitee.am.service.impl;

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.ReporterAttributeMapping;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.PluginConfigurationValidationService;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.ReporterConfigurationException;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.model.UpdateReporter;
import io.gravitee.am.service.validators.reporter.ReporterAttributeMappingsValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class ReporterServiceImplTest {

    @Mock
    private RepositoriesEnvironment environment;

    @Mock
    private ReporterRepository reporterRepository;

    @Mock
    private EventService eventService;

    @Mock
    private AuditService auditService;

    @Mock
    private PluginConfigurationValidationService validationService;

    @Mock
    private PluginLicenseGate pluginLicenseGate;

    /**
     * The real validator rather than a mock: the rules it enforces are what these tests are
     * asserting the service reacts to.
     */
    @Spy
    private ReporterAttributeMappingsValidator attributeMappingsValidator = new ReporterAttributeMappingsValidator();

    @InjectMocks
    private ReporterServiceImpl service;

    private static final String REPORTER_ID = "reporter-id";
    private final Reference reference = Reference.domain("domainId");

    private Reporter existing(String type) {
        return Reporter.builder()
                .id(REPORTER_ID)
                .type(type)
                .configuration("{}")
                .enabled(true)
                .reference(reference)
                .build();
    }

    @Test
    void update_rejects_type_change() {
        when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(existing("reporter-am-kafka")));

        UpdateReporter update = new UpdateReporter();
        update.setName("name");
        update.setType("reporter-am-file");
        update.setConfiguration("{}");

        TestObserver<Reporter> observer = service.update(reference, REPORTER_ID, update, new DefaultUser(), false).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertError(InvalidParameterException.class);
        verify(reporterRepository, never()).update(any());
    }

    @Test
    void update_allows_matching_type() {
        when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(existing("reporter-am-kafka")));
        when(pluginLicenseGate.check(any(), any(), any())).thenReturn(Completable.complete());
        when(reporterRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        UpdateReporter update = new UpdateReporter();
        update.setName("name");
        update.setType("reporter-am-kafka");
        update.setConfiguration("{}");

        TestObserver<Reporter> observer = service.update(reference, REPORTER_ID, update, new DefaultUser(), false).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        verify(reporterRepository).update(any());
    }

    // ---------------------------------------------------------------------------------------------
    // Attribute mappings
    // ---------------------------------------------------------------------------------------------

    private static final ReporterAttributeMapping USER_SUB =
            new ReporterAttributeMapping("{#context.attributes['user'].additionalInformation['sub']}", "user_sub");
    private static final ReporterAttributeMapping CLIENT_ID =
            new ReporterAttributeMapping("{#context.attributes['client'].clientId}", "client_id");
    private static final ReporterAttributeMapping INVALID = new ReporterAttributeMapping("   ", "user_sub");

    private Reporter systemReporter(List<ReporterAttributeMapping> attributeMappings, ManagedBy managedBy) {
        return Reporter.builder()
                .id(REPORTER_ID)
                .type("reporter-am-kafka")
                .configuration("{}")
                .enabled(true)
                .system(true)
                .managedBy(managedBy)
                .reference(reference)
                .attributeMappings(attributeMappings)
                .build();
    }

    private UpdateReporter updateWith(List<ReporterAttributeMapping> attributeMappings) {
        UpdateReporter update = new UpdateReporter();
        update.setName("name");
        update.setType("reporter-am-kafka");
        update.setConfiguration("{}");
        update.setAttributeMappings(attributeMappings);
        return update;
    }

    private void stubUpdatePersistence() {
        when(reporterRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
    }

    /** A system reporter skips the licence gate, so only regular reporters need it stubbed. */
    private void stubSuccessfulUpdate() {
        when(pluginLicenseGate.check(any(), any(), any())).thenReturn(Completable.complete());
        stubUpdatePersistence();
    }

    private Reporter updatedReporter() {
        ArgumentCaptor<Reporter> captor = ArgumentCaptor.forClass(Reporter.class);
        verify(reporterRepository).update(captor.capture());
        return captor.getValue();
    }

    @Nested
    class Create {

        private NewReporter newReporterWith(List<ReporterAttributeMapping> attributeMappings) {
            NewReporter newReporter = new NewReporter();
            newReporter.setName("name");
            newReporter.setType("reporter-am-kafka");
            newReporter.setConfiguration("{}");
            newReporter.setAttributeMappings(attributeMappings);
            return newReporter;
        }

        private void stubSuccessfulCreate() {
            when(pluginLicenseGate.check(any(), any(), any())).thenReturn(Completable.complete());
            when(reporterRepository.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
            when(eventService.create(any())).thenReturn(Single.just(new Event()));
        }

        @Test
        void persists_declared_mappings() {
            stubSuccessfulCreate();

            TestObserver<Reporter> observer = service
                    .create(reference, newReporterWith(List.of(USER_SUB, CLIENT_ID)), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);
            observer.assertComplete();

            ArgumentCaptor<Reporter> captor = ArgumentCaptor.forClass(Reporter.class);
            verify(reporterRepository).create(captor.capture());
            assertThat(captor.getValue().getAttributeMappings()).containsExactly(USER_SUB, CLIENT_ID);
        }

        @Test
        void leaves_the_payload_unchanged_when_no_mappings_are_declared() {
            stubSuccessfulCreate();

            TestObserver<Reporter> observer = service
                    .create(reference, newReporterWith(null), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);
            observer.assertComplete();

            ArgumentCaptor<Reporter> captor = ArgumentCaptor.forClass(Reporter.class);
            verify(reporterRepository).create(captor.capture());
            assertThat(captor.getValue().getAttributeMappings()).isNull();
        }

        @Test
        void rejects_invalid_mappings_as_a_bad_request_not_a_server_error() {
            when(pluginLicenseGate.check(any(), any(), any())).thenReturn(Completable.complete());

            TestObserver<Reporter> observer = service
                    .create(reference, newReporterWith(List.of(INVALID)), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);

            // the create pipeline rewraps errors raised inside its defer as a 500, so this assertion
            // is what pins the validation to the outer, 400-preserving stage
            observer.assertError(ReporterConfigurationException.class);
            verify(reporterRepository, never()).create(any());
        }

        @Test
        void rejects_duplicate_exported_names() {
            when(pluginLicenseGate.check(any(), any(), any())).thenReturn(Completable.complete());

            var duplicate = new ReporterAttributeMapping("{#context.attributes['user'].id}", "user_sub");
            TestObserver<Reporter> observer = service
                    .create(reference, newReporterWith(List.of(USER_SUB, duplicate)), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);

            observer.assertError(ReporterConfigurationException.class);
            observer.assertError(e -> e.getMessage().contains("user_sub"));
            verify(reporterRepository, never()).create(any());
        }

        @Test
        void validates_mappings_even_when_the_system_flag_is_set() {
            // no production caller reaches this: createDefault and createSystem build their own payload
            // and never set mappings. The case is pinned so validation stays ungated on the system flag,
            // the way validateConfiguration is not, and cannot be bypassed by a future caller
            TestObserver<Reporter> observer = service
                    .create(reference, newReporterWith(List.of(INVALID)), new DefaultUser(), true).test();
            observer.awaitDone(5, TimeUnit.SECONDS);

            observer.assertError(ReporterConfigurationException.class);
            verify(reporterRepository, never()).create(any());
        }
    }

    @Nested
    class UpdateMappings {

        @Test
        void applies_mappings_to_a_regular_reporter() {
            when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(existing("reporter-am-kafka")));
            stubSuccessfulUpdate();

            TestObserver<Reporter> observer = service
                    .update(reference, REPORTER_ID, updateWith(List.of(USER_SUB)), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);
            observer.assertComplete();

            assertThat(updatedReporter().getAttributeMappings()).containsExactly(USER_SUB);
        }

        @Test
        void clears_mappings_when_an_empty_list_is_supplied() {
            var reporter = existing("reporter-am-kafka");
            reporter.setAttributeMappings(List.of(USER_SUB));
            when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(reporter));
            stubSuccessfulUpdate();

            TestObserver<Reporter> observer = service
                    .update(reference, REPORTER_ID, updateWith(List.of()), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);
            observer.assertComplete();

            assertThat(updatedReporter().getAttributeMappings()).isNull();
        }

        @Test
        void clears_mappings_when_none_are_supplied() {
            // an update carries the desired state, so an absent list declares "no mappings" rather
            // than "leave whatever is stored". The Automation API relies on this to converge.
            var reporter = existing("reporter-am-kafka");
            reporter.setAttributeMappings(List.of(USER_SUB, CLIENT_ID));
            when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(reporter));
            stubSuccessfulUpdate();

            TestObserver<Reporter> observer = service
                    .update(reference, REPORTER_ID, updateWith(null), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);
            observer.assertComplete();

            assertThat(updatedReporter().getAttributeMappings()).isNull();
        }

        @Test
        void rejects_invalid_mappings() {
            when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(existing("reporter-am-kafka")));
            when(pluginLicenseGate.check(any(), any(), any())).thenReturn(Completable.complete());

            TestObserver<Reporter> observer = service
                    .update(reference, REPORTER_ID, updateWith(List.of(INVALID)), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);

            observer.assertError(ReporterConfigurationException.class);
            verify(reporterRepository, never()).update(any());
        }
    }

    @Nested
    class SystemReporterMappingsAreNotSupported {

        @Test
        void refuses_mappings() {
            when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(systemReporter(null, null)));

            TestObserver<Reporter> observer = service
                    .update(reference, REPORTER_ID, updateWith(List.of(USER_SUB)), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);

            observer.assertError(InvalidParameterException.class);
            observer.assertError(e -> "Attribute mappings are not supported on a system reporter".equals(e.getMessage()));
            verify(reporterRepository, never()).update(any());
        }

        @Test
        void refuses_even_when_the_automation_api_manages_the_reporter() {
            // the configuration gate exempts Automation-API-managed reporters; mappings deliberately do
            // not, because that API can neither set them on createSystem nor reconcile them on re-PUT
            when(reporterRepository.findById(REPORTER_ID))
                    .thenReturn(Maybe.just(systemReporter(null, ManagedBy.AUTOMATION_API)));

            TestObserver<Reporter> observer = service
                    .update(reference, REPORTER_ID, updateWith(List.of(USER_SUB)), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);

            observer.assertError(InvalidParameterException.class);
            verify(reporterRepository, never()).update(any());
        }

        @Test
        void refuses_before_validating_so_an_invalid_mapping_reports_the_real_reason() {
            // the mapping is also malformed, but "not supported here" is the actionable message
            when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(systemReporter(null, null)));

            TestObserver<Reporter> observer = service
                    .update(reference, REPORTER_ID, updateWith(List.of(INVALID)), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);

            observer.assertError(InvalidParameterException.class);
            verify(reporterRepository, never()).update(any());
        }

        @Test
        void accepts_a_null_list_so_an_unrelated_update_still_goes_through() {
            // the console PUTs the whole reporter to rename it; not supplying mappings must not be refused
            when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(systemReporter(null, null)));
            stubUpdatePersistence();

            TestObserver<Reporter> observer = service
                    .update(reference, REPORTER_ID, updateWith(null), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);
            observer.assertComplete();

            assertThat(updatedReporter().getAttributeMappings()).isNull();
        }

        @Test
        void accepts_an_empty_list_as_the_same_thing_as_none() {
            // the console echoes back what it read, which for a system reporter may be [] rather than null
            when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(systemReporter(null, null)));
            stubUpdatePersistence();

            TestObserver<Reporter> observer = service
                    .update(reference, REPORTER_ID, updateWith(List.of()), new DefaultUser(), false).test();
            observer.awaitDone(5, TimeUnit.SECONDS);
            observer.assertComplete();

            assertThat(updatedReporter().getAttributeMappings()).isNull();
        }
    }
}

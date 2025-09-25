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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.junit.management.MemoryReporterRepository;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.exception.ReporterConfigurationException;
import io.gravitee.am.service.exception.ReporterDeleteException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.ReporterServiceImpl;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.model.UpdateReporter;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class ReporterServiceTest {
    private final Random rng = new Random(1234);
    private final RandomStringGenerator randomStringGenerator = new RandomStringGenerator.Builder()
            .withinRange(new char[]{'a', 'z'},
                    new char[]{'A', 'Z'},
                    new char[]{'0', '9'},
                    new char[]{'-', '.'},
                    new char[]{'_', '_'})
            .usingRandom(rng::nextInt).build();

    private final ReporterRepository reporterRepository = new MemoryReporterRepository();

    @Mock
    private EventService eventService;
    @Mock
    private PluginConfigurationValidationService validationService;
    private final MockEnvironment environment = new MockEnvironment();

    @InjectMocks
    private ReporterService reporterService = new ReporterServiceImpl(new RepositoriesEnvironment(environment), reporterRepository, null, null, validationService);


    @Test
    void shouldAccept_ReportFileName() {
        final var reporter = new NewReporter();
        reporter.setEnabled(true);
        reporter.setName("Test");
        reporter.setType("reporter-am-file");
        reporter.setConfiguration("{\"filename\":\"9f4bdf97-5481-4420-8bdf-9754818420f3\"}");

        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        reporterService.create(Reference.domain("domain"), reporter)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertNoErrors();

    }

    @Test
    void shouldReject_ReportFileName() {
        final var reporter = new NewReporter();
        reporter.setEnabled(true);
        reporter.setName("Test");
        reporter.setType("reporter-am-file");
        reporter.setConfiguration("{\"filename\":\"../9f4bdf97-5481-4420-8bdf-9754818420f3\"}");

        reporterService.create(Reference.domain("domain"), reporter)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(ex -> ex instanceof TechnicalManagementException && ex.getCause() instanceof ReporterConfigurationException);

    }

    @Test
    void should_validate_config_with_schema() throws Exception{
        final var reporter = new NewReporter();
        reporter.setEnabled(true);
        reporter.setName("Test");
        reporter.setType("reporter-am-file");
        reporter.setConfiguration("{\"filename\":\"9f4bdf97-5481-4420-8bdf-9754818420f3\"}");

        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        final var createdReporter = reporterService.create(Reference.domain("domain"), reporter).blockingGet();

        final var observer = reporterService.update(createdReporter.getReference(), createdReporter.getId(), new UpdateReporter(), null, false).test();

        observer.await(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(validationService).validate(any(), any());
    }

    @Test
    void shouldFindAllByReference() {
        final var reporterA = randomTestFileReporter("wanted");
        final var reporterB = randomTestFileReporter("wanted");
        final var reporterC = randomTestFileReporter("unwanted");

        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        Reference firstDomain = Reference.domain("first");
        reporterService.create(firstDomain, reporterA)
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();
        reporterService.create(firstDomain, reporterB)
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();
        reporterService.create(Reference.domain("other"), reporterC)
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();

        var foundReporters = reporterService.findByReference(firstDomain)
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertComplete()
                .values();
        assertThat(foundReporters)
                .hasSize(2)
                .noneMatch(r -> r.getName().equals("unwanted"));
    }

    @Test
    void shouldCreateDefaultForOrganization() {
        environment.setProperty("repositories.management.type", "mongodb");
        environment.setProperty("repositories.management.mongodb.host", "doesnt-exist.local");
        environment.setProperty("repositories.management.mongodb.port", "12345");
        environment.setProperty("repositories.management.mongodb.username", "invalid");
        environment.setProperty("repositories.management.mongodb.password", "credentials");

        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        reporterService.createDefault(Reference.organization("some-org"))
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete()
                .assertValue(r -> r.getType().contains("mongo"))
                .assertValue(r -> r.getReference().equals(Reference.organization("some-org")));
    }

    @Test
    void canCreateInheritedOrganizationReporter() {
        var newReporter = randomTestFileReporter("test");
        newReporter.setInherited(true);
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        reporterService.create(Reference.organization("organization"), newReporter)
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();
    }

    @Test
    void cantCreateInheritedDomainReporter() {
        var newReporter = randomTestFileReporter("test");
        newReporter.setInherited(true);
        reporterService.create(Reference.domain("domain"), newReporter)
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(ReporterConfigurationException.class);
    }

    @Test
    void cantMakeDomainReporterInherited() {
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        var newReporter = randomTestFileReporter("test");
        var reference = Reference.domain("test-domain");
        var createdReporter = reporterService.create(reference, newReporter)
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertComplete()
                .values()
                .get(0);
        var updateReporter = new UpdateReporter();
        updateReporter.setName(newReporter.getName());
        updateReporter.setConfiguration(newReporter.getConfiguration());
        updateReporter.setEnabled(newReporter.isEnabled());
        updateReporter.setInherited(true);
        reporterService.update(reference, createdReporter.getId(), updateReporter, false)
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(ReporterConfigurationException.class);
    }

    @Test
    void canMakeOrganizationReporterInherited() {
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        var reference = Reference.organization("test-org");
        var newReporter = randomTestFileReporter("test");
        var createdReporter = reporterService.create(reference, newReporter, null, false)
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertComplete()
                .values()
                .get(0);
        var updateReporter = new UpdateReporter();
        updateReporter.setName(newReporter.getName());
        updateReporter.setConfiguration(newReporter.getConfiguration());
        updateReporter.setEnabled(newReporter.isEnabled());
        updateReporter.setInherited(true);
        reporterService.update(reference, createdReporter.getId(), updateReporter, false)
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();
    }

    @Test
    void canNotRemoveDefaultReporter() {
        environment.setProperty("repositories.management.type", "mongodb");
        environment.setProperty("repositories.management.mongodb.host", "some.host");
        environment.setProperty("repositories.management.mongodb.port", "12345");
        environment.setProperty("repositories.management.mongodb.username", "invalid");
        environment.setProperty("repositories.management.mongodb.password", "credentials");

        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<Reporter> reporterTestObserver = reporterService.createDefault(Reference.organization("org"))
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete()
                .assertValue(r -> r.getType().contains("mongo"))
                .assertValue(r -> r.getReference().equals(Reference.organization("org")));
        Reporter createdReporter = reporterTestObserver.values().get(0);
        reporterService.delete(createdReporter.getId(), null)
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(ReporterDeleteException.class)
                .assertError(t -> t.getMessage().contains("System reporter cannot be deleted."));
    }


    @Test
    @SuppressWarnings("unchecked")
    public void default_reporter_config_has_null_port_when_mongo_servers_are_defined() throws Exception {
        environment.setProperty("repositories.management.mongodb.servers[0].host", "localhost");
        environment.setProperty("repositories.management.mongodb.servers[0].port", 27017);
        environment.setProperty("repositories.management.mongodb.port", 99999); // this value should be ignored

        String reporterConfig = reporterService.createReporterConfig(Reference.domain("test"));
        Map<String, Object> test = new ObjectMapper().readValue(reporterConfig, Map.class);

        assertNull(test.get("port"));
        assertEquals("mongodb://localhost:27017/gravitee-am?connectTimeoutMS=5000&socketTimeoutMS=5000", test.get("uri"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void default_reporter_config_when_mongo_servers_are_not_defined() throws Exception {
        environment.setProperty("repositories.management.mongodb.host", "localhost");
        environment.setProperty("repositories.management.mongodb.port", 27017);

        String reporterConfig = reporterService.createReporterConfig(Reference.domain("test"));
        Map<String, Object> test = new ObjectMapper().readValue(reporterConfig, Map.class);

        assertEquals("localhost", (String) test.get("host"));
        assertEquals(27017, test.get("port"));
        assertEquals("mongodb://localhost:27017/gravitee-am?connectTimeoutMS=5000&socketTimeoutMS=5000", test.get("uri"));
    }

    private NewReporter randomTestFileReporter(String name) {
        var reporter = new NewReporter();
        reporter.setEnabled(true);
        reporter.setName(name);
        reporter.setType("reporter-am-file");
        reporter.setConfiguration("{\"filename\":\"" + randomStringGenerator.generate(12) + "\"}");

        return reporter;
    }
}

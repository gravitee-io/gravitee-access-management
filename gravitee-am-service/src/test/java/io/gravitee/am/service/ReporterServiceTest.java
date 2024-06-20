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

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.exception.ReporterConfigurationException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.ReporterServiceImpl;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.repository.MemoryReporterRepository;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ReporterServiceTest {
    private final Random rng = new Random(1234);
    private final RandomStringGenerator randomStringGenerator = new RandomStringGenerator.Builder()
            .withinRange(new char[]{'a', 'z'},
                    new char[]{'A', 'Z'},
                    new char[]{'0', '9'},
                    new char[]{'-', '.'},
                    new char[]{'_', '_'})
            .usingRandom(rng::nextInt).build();

    private ReporterRepository reporterRepository = new MemoryReporterRepository();

    @Mock
    private EventService eventService;
    private MockEnvironment environment = new MockEnvironment();

    @InjectMocks
    private ReporterService reporterService = new ReporterServiceImpl(environment, reporterRepository, null, null);


    @Test
    public void shouldAccept_ReportFileName() {
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
    public void shouldReject_ReportFileName() {
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
    public void shouldFindAllByReference() {
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
                .noneMatch(r->r.getName().equals("unwanted"));
    }

    @Test
    public void shouldCreateDefaultForOrganization() {
        environment.setProperty("management.type", "mongodb");
        environment.setProperty("management.mongodb.host", "doesnt-exist.local");
        environment.setProperty("management.mongodb.port", "12345");
        environment.setProperty("management.mongodb.username","invalid");
        environment.setProperty("management.mongodb.password","credentials");

        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        reporterService.createDefault(Reference.organization("some-org"))
                .test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete()
                .assertValue(r -> r.getType().contains("mongo"))
                .assertValue(r -> r.getReference().equals(Reference.organization("some-org")));
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

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

import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.exception.ReporterConfigurationException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.ReporterServiceImpl;
import io.gravitee.am.service.model.NewReporter;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ReporterServiceTest {

    @Mock
    private ReporterRepository reporterRepository;

    @Mock
    private EventService eventService;

    @InjectMocks
    private ReporterService reporterService = new ReporterServiceImpl();

    @Test
    public void shouldAccept_ReportFileName() {
        final var reporter = new NewReporter();
        reporter.setEnabled(true);
        reporter.setName("Test");
        reporter.setType(ReporterServiceImpl.REPORTER_AM_FILE);
        reporter.setConfiguration("{\"filename\":\"9f4bdf97-5481-4420-8bdf-9754818420f3\"}");

        when(reporterRepository.findByDomain(any())).thenReturn(Flowable.empty());
        when(reporterRepository.create(any())).thenReturn(Single.just(new Reporter()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        final TestObserver<Reporter> observer = reporterService.create("domain", reporter).test();
        observer.awaitTerminalEvent();
        observer.assertNoErrors();

        verify(reporterRepository).create(any());
    }

    @Test
    public void shouldReject_ReportFileName() {
        final var reporter = new NewReporter();
        reporter.setEnabled(true);
        reporter.setName("Test");
        reporter.setType(ReporterServiceImpl.REPORTER_AM_FILE);
        reporter.setConfiguration("{\"filename\":\"../9f4bdf97-5481-4420-8bdf-9754818420f3\"}");

        final TestObserver<Reporter> observer = reporterService.create("domain", reporter).test();
        observer.awaitTerminalEvent();
        observer.assertError(ex -> ex instanceof TechnicalManagementException && ex.getCause() instanceof ReporterConfigurationException);

        verify(reporterRepository, never()).create(any());
    }
}

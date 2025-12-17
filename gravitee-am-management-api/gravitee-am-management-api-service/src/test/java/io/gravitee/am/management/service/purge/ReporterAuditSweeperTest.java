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
package io.gravitee.am.management.service.purge;

import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.reporter.api.provider.NoOpReporter;
import io.gravitee.am.service.ReporterService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ReporterAuditSweeperTest {

    @Mock
    private AuditReporterManager auditReporterManager;

    @Mock
    private ReporterService reporterService;

    @InjectMocks
    private ReporterAuditSweeper sweeper;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void should_purge_all_enabled_reporters() {
        // Given
        io.gravitee.am.reporter.api.provider.Reporter mockReporter1 = mock(io.gravitee.am.reporter.api.provider.Reporter.class);
        io.gravitee.am.reporter.api.provider.Reporter mockReporter2 = mock(io.gravitee.am.reporter.api.provider.Reporter.class);

        Reporter config1 = createReporterConfig("MongoDB Reporter", true);
        Reporter config2 = createReporterConfig("JDBC Reporter", true);

        when(reporterService.findAll()).thenReturn(Flowable.just(config1, config2));
        when(auditReporterManager.getReporter(any(Reference.class)))
                .thenReturn(Maybe.just(mockReporter1))
                .thenReturn(Maybe.just(mockReporter2));
        when(mockReporter1.purgeExpiredData()).thenReturn(Completable.complete());
        when(mockReporter2.purgeExpiredData()).thenReturn(Completable.complete());

        // When
        TestObserver<Void> testObserver = sweeper.purgeExpiredData().test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);

        // Then
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(mockReporter1).purgeExpiredData();
        verify(mockReporter2).purgeExpiredData();
    }

    @Test
    public void should_skip_disabled_reporters() {
        // Given
        Reporter config = createReporterConfig("MongoDB Reporter", false);
        when(reporterService.findAll()).thenReturn(Flowable.just(config));

        // When
        TestObserver<Void> testObserver = sweeper.purgeExpiredData().test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);

        // Then
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(auditReporterManager, never()).getReporter(any());
    }

    @Test
    public void should_handle_reporter_errors_gracefully() {
        // Given
        io.gravitee.am.reporter.api.provider.Reporter mockReporter = mock(io.gravitee.am.reporter.api.provider.Reporter.class);
        Reporter config = createReporterConfig("MongoDB Reporter", true);

        when(reporterService.findAll()).thenReturn(Flowable.just(config));
        when(auditReporterManager.getReporter(any(Reference.class)))
                .thenReturn(Maybe.just(mockReporter));
        when(mockReporter.purgeExpiredData())
                .thenReturn(Completable.error(new RuntimeException("Database connection error")));

        // When
        TestObserver<Void> testObserver = sweeper.purgeExpiredData().test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);

        // Then
        testObserver.assertComplete(); // Should complete despite error
        testObserver.assertNoErrors(); // Error handled gracefully
        verify(mockReporter).purgeExpiredData();
    }

    @Test
    public void should_handle_NoOpReporter() {
        // Given
        NoOpReporter noOpReporter = new NoOpReporter();
        Reporter config = createReporterConfig("MongoDB Reporter", true);

        when(reporterService.findAll()).thenReturn(Flowable.just(config));
        when(auditReporterManager.getReporter(any(Reference.class)))
                .thenReturn(Maybe.just(noOpReporter));

        // When
        TestObserver<Void> testObserver = sweeper.purgeExpiredData().test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);

        // Then
        testObserver.assertComplete(); // NoOpReporter uses default (no-op)
        testObserver.assertNoErrors();
    }

    @Test
    public void should_continue_on_reporter_not_found() {
        // Given
        Reporter config = createReporterConfig("MongoDB Reporter", true);

        when(reporterService.findAll()).thenReturn(Flowable.just(config));
        when(auditReporterManager.getReporter(any(Reference.class)))
                .thenReturn(Maybe.empty()); // Reporter not bootstrapped

        // When
        TestObserver<Void> testObserver = sweeper.purgeExpiredData().test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);

        // Then
        testObserver.assertComplete(); // Should complete despite missing reporter
        testObserver.assertNoErrors();
    }

    @Test
    public void should_purge_multiple_reporters_even_if_one_fails() {
        // Given
        io.gravitee.am.reporter.api.provider.Reporter reporter1 = mock(io.gravitee.am.reporter.api.provider.Reporter.class);
        io.gravitee.am.reporter.api.provider.Reporter reporter2 = mock(io.gravitee.am.reporter.api.provider.Reporter.class);
        io.gravitee.am.reporter.api.provider.Reporter reporter3 = mock(io.gravitee.am.reporter.api.provider.Reporter.class);

        Reporter config1 = createReporterConfig("Reporter 1", true);
        Reporter config2 = createReporterConfig("Reporter 2", true);
        Reporter config3 = createReporterConfig("Reporter 3", true);

        when(reporterService.findAll()).thenReturn(Flowable.just(config1, config2, config3));
        when(auditReporterManager.getReporter(any(Reference.class)))
                .thenReturn(Maybe.just(reporter1))
                .thenReturn(Maybe.just(reporter2))
                .thenReturn(Maybe.just(reporter3));

        when(reporter1.purgeExpiredData()).thenReturn(Completable.complete());
        when(reporter2.purgeExpiredData()).thenReturn(Completable.error(new RuntimeException("Reporter 2 failed")));
        when(reporter3.purgeExpiredData()).thenReturn(Completable.complete());

        // When
        TestObserver<Void> testObserver = sweeper.purgeExpiredData().test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);

        // Then
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(reporter1).purgeExpiredData();
        verify(reporter2).purgeExpiredData();
        verify(reporter3).purgeExpiredData();
    }

    private Reporter createReporterConfig(String name, boolean enabled) {
        Reporter reporter = new Reporter();
        reporter.setId("reporter-id-" + name);
        reporter.setName(name);
        reporter.setEnabled(enabled);
        reporter.setReference(Reference.domain("domain-id"));
        return reporter;
    }
}

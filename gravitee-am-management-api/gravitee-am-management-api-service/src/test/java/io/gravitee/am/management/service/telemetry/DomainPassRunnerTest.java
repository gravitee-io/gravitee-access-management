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
package io.gravitee.am.management.service.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.management.service.telemetry.model.DomainBatch;
import io.gravitee.am.model.Domain;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class DomainPassRunnerTest {

    private static final String INSTALLATION_ID = "5f0c2d3e-8b1a-4f6c-9e2d-7a1b3c4d5e6f";
    private static final String DOMAINS_URL = "http://localhost:8080/v1/am/domains";

    @Mock
    private TelemetryPublisher publisher;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private IdentityProviderRepository identityProviderRepository;

    @Mock
    private FactorRepository factorRepository;

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LastDomainPassHolder lastDomainPass = new LastDomainPassHolder();
    private DomainPassRunner runner;

    @BeforeEach
    void setUp() {
        runner =
            new DomainPassRunner(
                settings(2),
                objectMapper,
                publisher,
                domainRepository,
                applicationRepository,
                identityProviderRepository,
                factorRepository,
                certificateRepository,
                dataPlaneRegistry,
                lastDomainPass
            );

        lenient().when(domainRepository.findAll()).thenReturn(Flowable.fromIterable(domains(3)));
        lenient().when(identityProviderRepository.findAll()).thenReturn(Flowable.empty());
        lenient().when(factorRepository.findAll()).thenReturn(Flowable.empty());
        lenient().when(certificateRepository.findAll()).thenReturn(Flowable.empty());
        lenient().when(applicationRepository.countByDomain(anyString())).thenReturn(Single.just(4L));
        lenient().when(dataPlaneRegistry.getUserRepository(any())).thenReturn(userRepository);
        lenient().when(userRepository.countByReference(any())).thenReturn(Single.just(100L));
        lenient()
            .when(dataPlaneRegistry.getDescription(any()))
            .thenReturn(new DataPlaneDescription("default", "Default", "mongodb", "", null));
        lenient().when(publisher.send(eq(DOMAINS_URL), any())).thenReturn(Completable.complete());
    }

    @Test
    void shouldSendOneBatchPerBatchSizeAndCloseTheRun() {
        runner.run(INSTALLATION_ID).blockingAwait();

        final List<DomainBatch> batches = sentBatches();
        assertThat(batches).hasSize(2);
        assertThat(batches.get(0).batch()).isEqualTo(1);
        assertThat(batches.get(0).last()).isFalse();
        assertThat(batches.get(0).domains()).hasSize(2);
        assertThat(batches.get(1).batch()).isEqualTo(2);
        assertThat(batches.get(1).last()).isTrue();
        assertThat(batches.get(1).domains()).hasSize(1);
        assertThat(batches.get(0).runId()).isEqualTo(batches.get(1).runId());
    }

    @Test
    void shouldCarryPseudonymousKeysAndCounts() {
        runner.run(INSTALLATION_ID).blockingAwait();

        assertThat(sentBatches().get(0).domains()).allSatisfy(record -> {
            assertThat(record.key()).matches("^[0-9a-f]{16}$");
            assertThat(record.fingerprint()).matches("^[0-9a-f]{16}$");
            assertThat(record.applications()).isEqualTo(4);
            assertThat(record.users()).isEqualTo(100);
            assertThat(record.dataPlaneType()).isEqualTo("mongodb");
            assertThat(record.createdMonth()).matches("^[0-9]{4}-[0-9]{2}$");
        });
    }

    @Test
    void shouldRecordTheUserTotalForTheNextSummary() {
        runner.run(INSTALLATION_ID).blockingAwait();

        assertThat(lastDomainPass.get().users()).isEqualTo(300);
        assertThat(lastDomainPass.get().complete()).isTrue();
    }

    @Test
    void shouldStopOnTheFirstFailedBatch() {
        when(publisher.send(eq(DOMAINS_URL), any())).thenReturn(Completable.error(new IllegalStateException("collector down")));

        runner.run(INSTALLATION_ID).onErrorComplete().blockingAwait();

        verify(publisher).send(eq(DOMAINS_URL), any());
        assertThat(lastDomainPass.get()).isNull();
    }

    @Test
    void shouldSendNothingWhenThereIsNoDomain() {
        when(domainRepository.findAll()).thenReturn(Flowable.empty());

        runner.run(INSTALLATION_ID).blockingAwait();

        assertThat(sentBatches()).isEmpty();
    }

    @Test
    void shouldBuildTheFirstBatchWithoutSendingIt() {
        final DomainBatch preview = runner.preview(INSTALLATION_ID).blockingGet();

        assertThat(preview.domains()).hasSize(2);
        assertThat(preview.installationId()).isEqualTo(INSTALLATION_ID);
        assertThat(sentBatches()).isEmpty();
    }

    private List<DomainBatch> sentBatches() {
        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, org.mockito.Mockito.atLeast(0)).send(eq(DOMAINS_URL), captor.capture());
        final List<DomainBatch> batches = new ArrayList<>();
        captor.getAllValues().forEach(value -> batches.add((DomainBatch) value));
        return batches;
    }

    private static TelemetrySettings settings(int batchSize) {
        return new TelemetrySettings(
            true,
            "http://localhost:8080/v1/am",
            "",
            0,
            10000,
            "0 0 4 * * *",
            0,
            true,
            "0 0 3 * * SUN",
            batchSize,
            0,
            4,
            3600000
        );
    }

    private static List<Domain> domains(int count) {
        final List<Domain> domains = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final Domain domain = new Domain();
            domain.setId("domain-" + i);
            domain.setEnabled(true);
            domain.setCreatedAt(new Date());
            domains.add(domain);
        }
        return domains;
    }
}

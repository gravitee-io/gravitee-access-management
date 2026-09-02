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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.telemetry.model.DomainBatch;
import io.gravitee.am.management.service.telemetry.model.DomainRecord;
import io.gravitee.am.management.service.telemetry.model.SummaryReport;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reference;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

/**
 * Streams the domains, enriches each one with its application and user counts, and sends the
 * records to the collector in batches.
 * <p>
 * The pass never resumes mid-way. The first failed batch ends it, and the next cron tick starts
 * again from the first domain. A pass that runs out of its time budget closes with
 * {@code last: false}, and the next summary reports the run as incomplete.
 *
 * @author GraviteeSource Team
 */
@CustomLog
@RequiredArgsConstructor
public class DomainPassRunner {

    private static final DateTimeFormatter INSTANT_FORMAT = DateTimeFormatter.ISO_INSTANT;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TelemetrySettings settings;
    private final ObjectMapper objectMapper;
    private final TelemetryPublisher publisher;
    private final DomainRepository domainRepository;
    private final ApplicationRepository applicationRepository;
    private final IdentityProviderRepository identityProviderRepository;
    private final FactorRepository factorRepository;
    private final CertificateRepository certificateRepository;
    private final DataPlaneRegistry dataPlaneRegistry;
    private final LastDomainPassHolder lastDomainPass;

    /**
     * Runs one full pass against the collector.
     */
    public Completable run(String installationId) {
        final String runId = UUID.randomUUID().toString();
        final AtomicInteger batchNumber = new AtomicInteger();
        final AtomicLong users = new AtomicLong();
        final AtomicBoolean budgetExpired = new AtomicBoolean();
        final AtomicReference<List<DomainRecord>> held = new AtomicReference<>();

        return enrichedBatches(installationId, budgetExpired, users)
            .concatMapCompletable(records -> {
                final List<DomainRecord> previous = held.getAndSet(records);
                if (previous == null) {
                    return Completable.complete();
                }
                return send(installationId, runId, batchNumber.incrementAndGet(), false, previous)
                    .delay(settings.batchDelay(), TimeUnit.MILLISECONDS);
            })
            .andThen(
                Completable.defer(() -> {
                    final List<DomainRecord> previous = held.get();
                    if (previous == null) {
                        log.debug("The telemetry domain pass found no domain to report");
                        return Completable.complete();
                    }
                    return send(installationId, runId, batchNumber.incrementAndGet(), !budgetExpired.get(), previous);
                })
            )
            .doOnComplete(() -> lastDomainPass.set(
                new SummaryReport.LastDomainPass(runId, INSTANT_FORMAT.format(Instant.now()), !budgetExpired.get(), users.get())
            ));
    }

    /**
     * Builds the first batch without sending it. The technical API preview uses this, so an operator
     * can read exactly what a pass would report before they allow one.
     */
    public Single<DomainBatch> preview(String installationId) {
        final String runId = UUID.randomUUID().toString();
        return enrichedBatches(installationId, new AtomicBoolean(), new AtomicLong())
            .take(1)
            .map(records -> batch(installationId, runId, 1, true, records))
            .first(batch(installationId, runId, 1, true, List.of()));
    }

    private Flowable<List<DomainRecord>> enrichedBatches(String installationId, AtomicBoolean budgetExpired, AtomicLong users) {
        return childCounts()
            .flatMapPublisher(counts ->
                domainRepository
                    .findAll()
                    .takeUntil(
                        Flowable.timer(settings.maxDuration(), TimeUnit.MILLISECONDS).doOnNext(tick -> budgetExpired.set(true))
                    )
                    .map(domain -> record(installationId, domain, counts))
                    .buffer(settings.batchSize())
                    .concatMapSingle(records -> enrich(records, users))
            );
    }

    private Single<List<DomainRecord>> enrich(List<PendingRecord> records, AtomicLong users) {
        return Flowable
            .fromIterable(records)
            .flatMapSingle(
                record ->
                    Single
                        .zip(
                            applicationRepository.countByDomain(record.domainId()),
                            userCount(record),
                            (applications, userCount) -> {
                                users.addAndGet(userCount);
                                return record.record().withCounts(applications, userCount);
                            }
                        )
                        .onErrorReturn(throwable -> {
                            log.debug("Unable to count the children of a domain for telemetry", throwable);
                            return record.record();
                        }),
                false,
                settings.concurrency()
            )
            .toList();
    }

    private Single<Long> userCount(PendingRecord record) {
        return dataPlaneRegistry.getUserRepository(record.domain()).countByReference(Reference.domain(record.domainId()));
    }

    /**
     * Loads the identity providers, factors and certificates once, grouped by domain. Three queries
     * serve the whole pass, whatever the domain count.
     */
    private Single<ChildCounts> childCounts() {
        return Single.zip(
            identityProviderRepository.findAll().toList(),
            factorRepository.findAll().toList(),
            certificateRepository.findAll().toList(),
            (idps, factors, certificates) ->
                new ChildCounts(
                    group(idps, IdentityProvider::getReferenceId, IdentityProvider::getType),
                    group(factors, Factor::getDomain, Factor::getType),
                    group(certificates, Certificate::getDomain, Certificate::getType)
                )
        );
    }

    private static <T> Map<String, Map<String, Long>> group(
        List<T> items,
        java.util.function.Function<T, String> owner,
        java.util.function.Function<T, String> type
    ) {
        final Map<String, Map<String, Long>> byOwner = new LinkedHashMap<>();
        for (T item : items) {
            final String ownerId = owner.apply(item);
            final String typeId = type.apply(item);
            if (ownerId == null || typeId == null) {
                continue;
            }
            byOwner.computeIfAbsent(ownerId, key -> new LinkedHashMap<>()).merge(typeId, 1L, Long::sum);
        }
        return byOwner;
    }

    private PendingRecord record(String installationId, Domain domain, ChildCounts counts) {
        final DomainRecord record = new DomainRecord(
            DomainKeys.key(installationId, domain.getId()),
            createdMonth(domain),
            dataPlaneType(domain),
            domain.isEnabled(),
            domain.isMaster(),
            domain.isVhostMode(),
            Boolean.TRUE.equals(domain.isAlertEnabled()),
            DomainFlags.of(domain),
            counts.identityProviders().getOrDefault(domain.getId(), Map.of()),
            counts.factors().getOrDefault(domain.getId(), Map.of()),
            counts.certificates().getOrDefault(domain.getId(), Map.of()),
            null,
            null,
            null
        );
        return new PendingRecord(domain, withFingerprint(record));
    }

    private DomainRecord withFingerprint(DomainRecord record) {
        try {
            return new DomainRecord(
                record.key(),
                record.createdMonth(),
                record.dataPlaneType(),
                record.enabled(),
                record.master(),
                record.vhostMode(),
                record.alertEnabled(),
                record.settings(),
                record.identityProvidersByType(),
                record.factorsByType(),
                record.certificatesByType(),
                null,
                null,
                DomainKeys.fingerprint(objectMapper.writeValueAsString(record))
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to fingerprint a telemetry domain record", e);
        }
    }

    private String dataPlaneType(Domain domain) {
        try {
            return dataPlaneRegistry.getDescription(domain).type();
        } catch (Exception e) {
            log.debug("Unable to resolve the data plane of a domain for telemetry", e);
            return null;
        }
    }

    private static String createdMonth(Domain domain) {
        if (domain.getCreatedAt() == null) {
            return null;
        }
        return MONTH_FORMAT.format(YearMonth.from(domain.getCreatedAt().toInstant().atZone(ZoneOffset.UTC)));
    }

    private Completable send(String installationId, String runId, int number, boolean last, List<DomainRecord> records) {
        return publisher.send(settings.domainsUrl(), batch(installationId, runId, number, last, records));
    }

    private DomainBatch batch(String installationId, String runId, int number, boolean last, List<DomainRecord> records) {
        return new DomainBatch(
            DomainBatch.SCHEMA_VERSION,
            DomainBatch.PRODUCT,
            installationId,
            settings.label().isBlank() ? null : settings.label(),
            runId,
            number,
            last,
            INSTANT_FORMAT.format(Instant.now()),
            new ArrayList<>(records)
        );
    }

    /** A record that still needs its counts, kept with the domain the counts are read from. */
    private record PendingRecord(Domain domain, DomainRecord record) {
        String domainId() {
            return domain.getId();
        }
    }

    private record ChildCounts(
        Map<String, Map<String, Long>> identityProviders,
        Map<String, Map<String, Long>> factors,
        Map<String, Map<String, Long>> certificates
    ) {}
}

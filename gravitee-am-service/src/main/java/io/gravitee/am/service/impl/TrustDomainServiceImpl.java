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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.TrustDomainService;
import io.gravitee.am.service.exception.InvalidTrustDomainException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.TrustDomainAlreadyExistsException;
import io.gravitee.am.service.exception.TrustDomainNotFoundException;
import io.gravitee.am.service.model.NewTrustDomain;
import io.gravitee.am.service.model.UpdateTrustDomain;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.TrustDomainAuditBuilder;
import io.gravitee.am.service.utils.PrivateAddressGuard;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static io.gravitee.am.common.event.Type.TRUST_DOMAIN;

@Component
public class TrustDomainServiceImpl implements TrustDomainService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrustDomainServiceImpl.class);

    /**
     * SPIFFE trust-domain names are case-insensitive DNS-style host labels per the
     * <a href="https://github.com/spiffe/spiffe/blob/main/standards/SPIFFE-ID.md#21-trust-domain">SPIFFE ID spec</a>.
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9.\\-]*[a-z0-9])?$");

    @Lazy
    @Autowired
    private TrustDomainRepository repository;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<TrustDomain> findById(String id) {
        return repository.findById(id)
                .onErrorResumeNext(ex -> Maybe.error(new TechnicalManagementException("Failed to find trust domain " + id, ex)));
    }

    @Override
    public Maybe<TrustDomain> findByName(ReferenceType referenceType, String referenceId, String name) {
        return repository.findByName(referenceType, referenceId, name);
    }

    @Override
    public Flowable<TrustDomain> findByReference(ReferenceType referenceType, String referenceId) {
        return repository.findByReference(referenceType, referenceId);
    }

    @Override
    public Single<TrustDomain> create(Domain domain, NewTrustDomain input, User principal) {
        Objects.requireNonNull(domain, "domain is required");
        Objects.requireNonNull(input, "newTrustDomain is required");

        TrustDomain td = new TrustDomain();
        td.setReferenceType(ReferenceType.DOMAIN);
        td.setReferenceId(domain.getId());
        td.setName(input.getName() != null ? input.getName().toLowerCase(Locale.ROOT) : null);
        td.setDescription(input.getDescription());
        td.setBundleSource(input.getBundleSource());
        td.setJwksUrl(input.getJwksUrl());
        td.setRefreshIntervalSeconds(Optional.ofNullable(input.getRefreshIntervalSeconds())
                .orElse(TrustDomain.DEFAULT_REFRESH_INTERVAL_SECONDS));
        td.setAllowedAlgorithms(input.getAllowedAlgorithms());
        Date now = new Date();
        td.setCreatedAt(now);
        td.setUpdatedAt(now);

        return validate(domain, td)
                .andThen(repository.findByName(ReferenceType.DOMAIN, domain.getId(), td.getName())
                        .isEmpty()
                        .flatMap(absent -> {
                            if (!absent) {
                                return Single.error(new TrustDomainAlreadyExistsException(td.getName()));
                            }
                            return repository.create(td)
                                    .flatMap(created -> publish(domain, created, Action.CREATE).andThen(Single.just(created)));
                        }))
                .doOnSuccess(created -> auditService.report(AuditBuilder.builder(TrustDomainAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.TRUST_DOMAIN_CREATED)
                        .trustDomain(created)))
                .doOnError(ex -> auditService.report(AuditBuilder.builder(TrustDomainAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.TRUST_DOMAIN_CREATED)
                        .trustDomain(td)
                        .throwable(ex)));
    }

    @Override
    public Single<TrustDomain> update(Domain domain, String id, UpdateTrustDomain input, User principal) {
        Objects.requireNonNull(domain, "domain is required");
        Objects.requireNonNull(input, "updateTrustDomain is required");
        Objects.requireNonNull(id, "id is required");

        AtomicReference<TrustDomain> existingRef = new AtomicReference<>();
        AtomicReference<TrustDomain> updatedRef = new AtomicReference<>();

        return repository.findById(id)
                .switchIfEmpty(Single.error(new TrustDomainNotFoundException(id)))
                .flatMap(existing -> {
                    existingRef.set(existing);
                    if (!ReferenceType.DOMAIN.equals(existing.getReferenceType())
                            || !domain.getId().equals(existing.getReferenceId())) {
                        return Single.error(new InvalidTrustDomainException("Trust domain is not linked to domain " + domain.getId()));
                    }
                    TrustDomain updated = new TrustDomain(existing);
                    updated.setDescription(input.getDescription());
                    if (input.getBundleSource() != null) {
                        updated.setBundleSource(input.getBundleSource());
                    }
                    if (input.getJwksUrl() != null) {
                        updated.setJwksUrl(input.getJwksUrl());
                    }
                    if (input.getRefreshIntervalSeconds() != null) {
                        updated.setRefreshIntervalSeconds(input.getRefreshIntervalSeconds());
                    }
                    if (input.getAllowedAlgorithms() != null) {
                        updated.setAllowedAlgorithms(input.getAllowedAlgorithms());
                    }
                    updated.setUpdatedAt(new Date());
                    updatedRef.set(updated);

                    return validate(domain, updated)
                            .andThen(repository.update(updated))
                            .flatMap(saved -> publish(domain, saved, Action.UPDATE).andThen(Single.just(saved)));
                })
                .doOnSuccess(saved -> auditService.report(AuditBuilder.builder(TrustDomainAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.TRUST_DOMAIN_UPDATED)
                        .trustDomain(saved)
                        .oldValue(existingRef.get())))
                .doOnError(ex -> {
                    TrustDomainAuditBuilder builder = AuditBuilder.builder(TrustDomainAuditBuilder.class)
                            .principal(principal)
                            .type(EventType.TRUST_DOMAIN_UPDATED)
                            .throwable(ex);
                    TrustDomain updated = updatedRef.get();
                    if (updated != null) {
                        builder.trustDomain(updated).oldValue(existingRef.get());
                    } else {
                        // pre-load failure (not-found or repository error) — anchor the audit to the domain
                        // so it still surfaces in the domain audit log without a concrete trust-domain target.
                        builder.reference(new Reference(ReferenceType.DOMAIN, domain.getId()));
                    }
                    auditService.report(builder);
                });
    }

    @Override
    public Completable delete(Domain domain, String id, User principal) {
        AtomicReference<TrustDomain> tdRef = new AtomicReference<>();
        return repository.findById(id)
                .switchIfEmpty(Maybe.error(new TrustDomainNotFoundException(id)))
                .flatMapCompletable(td -> {
                    tdRef.set(td);
                    if (!ReferenceType.DOMAIN.equals(td.getReferenceType())
                            || !domain.getId().equals(td.getReferenceId())) {
                        return Completable.error(new InvalidTrustDomainException("Trust domain is not linked to domain " + domain.getId()));
                    }
                    return repository.delete(id)
                            .andThen(publish(domain, td, Action.DELETE));
                })
                .doOnComplete(() -> {
                    TrustDomain td = tdRef.get();
                    auditService.report(AuditBuilder.builder(TrustDomainAuditBuilder.class)
                            .principal(principal)
                            .type(EventType.TRUST_DOMAIN_DELETED)
                            .trustDomain(td)
                            .reference(new Reference(td.getReferenceType(), td.getReferenceId()))
                            .oldValue(td));
                })
                .doOnError(ex -> {
                    TrustDomainAuditBuilder builder = AuditBuilder.builder(TrustDomainAuditBuilder.class)
                            .principal(principal)
                            .type(EventType.TRUST_DOMAIN_DELETED)
                            .throwable(ex);
                    TrustDomain td = tdRef.get();
                    if (td != null) {
                        builder.trustDomain(td).oldValue(td);
                    } else {
                        builder.reference(new Reference(ReferenceType.DOMAIN, domain.getId()));
                    }
                    auditService.report(builder);
                });
    }

    private Completable publish(Domain domain, TrustDomain trustDomain, Action action) {
        Event event = new Event(TRUST_DOMAIN, new Payload(trustDomain.getId(), trustDomain.getReferenceType(), trustDomain.getReferenceId(), action));
        return eventService.create(event, domain).ignoreElement();
    }

    private Completable validate(Domain domain, TrustDomain td) {
        SpiffeDomainSettings settings = Optional.ofNullable(domain.getOidc())
                .map(o -> o.getSpiffeSettings())
                .orElseGet(SpiffeDomainSettings::defaultSettings);

        if (!settings.isEnabled()) {
            return Completable.error(new InvalidTrustDomainException(
                    "SPIFFE workload identity is disabled for this domain. Enable it in domain settings before registering trust domains."));
        }
        if (td.getName() == null || !NAME_PATTERN.matcher(td.getName()).matches()) {
            return Completable.error(new InvalidTrustDomainException("name must be a DNS-style label (lowercase letters, digits, '.' or '-')"));
        }
        if (td.getBundleSource() == null) {
            return Completable.error(new InvalidTrustDomainException("bundleSource is required"));
        }
        if (td.getBundleSource() != SpiffeBundleSource.JWKS_URL) {
            return Completable.error(new InvalidTrustDomainException(
                    "Only bundleSource=JWKS_URL is supported in this release"));
        }
        if (td.getJwksUrl() == null || td.getJwksUrl().isBlank()) {
            return Completable.error(new InvalidTrustDomainException("jwksUrl is required when bundleSource=JWKS_URL"));
        }
        Optional<String> urlError = PrivateAddressGuard.validateHttpUrl(
                "jwksUrl", td.getJwksUrl(), settings.isAllowUnsecuredHttpUri(), settings.isAllowPrivateIpAddress());
        if (urlError.isPresent()) {
            return Completable.error(new InvalidTrustDomainException(urlError.get()));
        }
        if (td.getRefreshIntervalSeconds() <= 0) {
            return Completable.error(new InvalidTrustDomainException("refreshIntervalSeconds must be positive"));
        }
        List<String> algos = td.getAllowedAlgorithms();
        if (algos != null) {
            for (String alg : algos) {
                if (alg == null || alg.isBlank() || alg.equalsIgnoreCase("none") || alg.toUpperCase(Locale.ROOT).startsWith("HS")) {
                    return Completable.error(new InvalidTrustDomainException(
                            "allowedAlgorithms must not contain 'none' or HMAC variants (HS256/HS384/HS512)"));
                }
            }
        }
        return Completable.complete();
    }
}

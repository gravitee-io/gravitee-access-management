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

import io.gravitee.am.certificate.api.X509CertUtils;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyRetrievalSettings;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.CrossAppAccessResourceServer;
import io.gravitee.am.model.oidc.CrossAppAccessSettings;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.TrustDomainService;
import io.gravitee.am.service.exception.InvalidTrustDomainException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.TrustDomainAlreadyExistsException;
import io.gravitee.am.service.exception.TrustDomainAudienceAlreadyExistsException;
import io.gravitee.am.service.exception.TrustDomainIssuerAlreadyExistsException;
import io.gravitee.am.service.exception.TrustDomainNotFoundException;
import io.gravitee.am.service.exception.TrustDomainSpiffeAlreadyExistsException;
import io.gravitee.am.service.model.NewTrustDomain;
import io.gravitee.am.service.model.UpdateTrustDomain;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.TrustDomainAuditBuilder;
import io.gravitee.am.service.utils.PrivateAddressGuard;
import io.gravitee.el.spel.SpelExpressionParser;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.ParseException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

import static io.gravitee.am.common.event.Type.TRUST_DOMAIN;
import static java.util.stream.Collectors.toSet;
import lombok.CustomLog;

@Component
@CustomLog
public class TrustDomainServiceImpl implements TrustDomainService {

    /**
     * SPIFFE trust domains are case-insensitive DNS-style host labels per the
     * <a href="https://github.com/spiffe/spiffe/blob/main/standards/SPIFFE-ID.md#21-trust-domain">SPIFFE ID spec</a>.
     */
    private static final Pattern SPIFFE_TRUST_DOMAIN_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9.\\-]*[a-z0-9])?$");

    private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

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
    public Maybe<TrustDomain> findBySpiffeTrustDomain(ReferenceType referenceType, String referenceId, String spiffeTrustDomain) {
        return repository.findBySpiffeTrustDomain(referenceType, referenceId, spiffeTrustDomain);
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
        td.setName(trimToNull(input.getName()));
        td.setDescription(input.getDescription());
        td.setCrossAppAccess(input.getCrossAppAccess());
        applyMatchers(td, input.getSpiffeTrustDomain(), input.getIssuer());
        td.setKeyMaterial(resolveKeyMaterial(input.getKeyMaterial(), input.getBundleSource(), input.getJwksUrl()));
        td.setRefreshIntervalSeconds(Optional.ofNullable(input.getRefreshIntervalSeconds())
                .orElse(TrustDomain.DEFAULT_REFRESH_INTERVAL_SECONDS));
        td.setAllowedAlgorithms(input.getAllowedAlgorithms());
        td.setScopeMappings(input.getScopeMappings());
        td.setUserBindingEnabled(input.isUserBindingEnabled());
        td.setUserBindingCriteria(input.getUserBindingCriteria());
        Date now = new Date();
        td.setCreatedAt(now);
        td.setUpdatedAt(now);

        return validate(domain, td)
                .andThen(rejectDuplicates(domain, td, null))
                .andThen(Single.defer(() -> {
                    td.setCrossAppAccess(copyWithGeneratedIds(td.getCrossAppAccess(), null));
                    return repository.create(td);
                }))
                .flatMap(created -> publish(domain, created, Action.CREATE).andThen(Single.just(created)))
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
                    if (input.getName() != null) {
                        updated.setName(trimToNull(input.getName()));
                    }
                    updated.setDescription(input.getDescription());
                    updated.setCrossAppAccess(input.getCrossAppAccess());
                    if (input.getSpiffeTrustDomain() != null || input.getIssuer() != null) {
                        applyMatchers(updated, input.getSpiffeTrustDomain(), input.getIssuer());
                    }
                    TrustDomainKeyMaterial keyMaterial =
                            resolveKeyMaterial(input.getKeyMaterial(), input.getBundleSource(), input.getJwksUrl());
                    if (keyMaterial != null) {
                        updated.setKeyMaterial(keyMaterial);
                    }
                    if (input.getRefreshIntervalSeconds() != null) {
                        updated.setRefreshIntervalSeconds(input.getRefreshIntervalSeconds());
                    }
                    if (input.getAllowedAlgorithms() != null) {
                        updated.setAllowedAlgorithms(input.getAllowedAlgorithms());
                    }
                    if (input.getScopeMappings() != null) {
                        updated.setScopeMappings(input.getScopeMappings());
                    }
                    if (input.getUserBindingEnabled() != null) {
                        updated.setUserBindingEnabled(input.getUserBindingEnabled());
                    }
                    if (input.getUserBindingCriteria() != null) {
                        updated.setUserBindingCriteria(input.getUserBindingCriteria());
                    }
                    updated.setUpdatedAt(new Date());
                    updatedRef.set(updated);

                    return validate(domain, updated)
                            .andThen(rejectDuplicates(domain, updated, existing))
                            .andThen(Single.defer(() -> {
                                updated.setCrossAppAccess(copyWithGeneratedIds(updated.getCrossAppAccess(), existing));
                                return repository.update(updated);
                            }))
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

    /**
     * Sets the matchers a trusted domain is recognised by. A payload that declares neither is written
     * against the SPIFFE-only API that preceded the issuer matcher, and means the name. Cross App
     * Access is a usage in its own right, so a payload that declares it is not falling back.
     */
    private static void applyMatchers(TrustDomain td, String spiffeTrustDomain, String issuer) {
        String spiffe = spiffeTrustDomain == null && issuer == null && td.getCrossAppAccess() == null
                ? td.getName()
                : trimToNull(spiffeTrustDomain);
        td.setSpiffeTrustDomain(spiffe != null ? spiffe.toLowerCase(Locale.ROOT) : null);
        td.setIssuer(trimToNull(issuer));
    }

    /**
     * Stamps every written resource server with an id, keeping one a stored resource server already
     * carries so a rename or a new resource never orphans an application referencing it. Runs once
     * the block has been validated, so the copy never walks an entry validation would have rejected.
     */
    private static CrossAppAccessSettings copyWithGeneratedIds(CrossAppAccessSettings written, TrustDomain existing) {
        if (written == null) {
            return null;
        }
        CrossAppAccessSettings settings = new CrossAppAccessSettings(written);
        if (settings.getResourceServers() == null) {
            return settings;
        }
        Set<String> stored = existing == null ? Set.of() : existing.crossAppAccessResourceServers().stream()
                .map(CrossAppAccessResourceServer::getId)
                .filter(Objects::nonNull)
                .collect(toSet());
        Set<String> taken = new HashSet<>();
        settings.getResourceServers().stream()
                .filter(Objects::nonNull)
                .forEach(resourceServer -> {
                    boolean keep = resourceServer.getId() != null
                            && stored.contains(resourceServer.getId())
                            && taken.add(resourceServer.getId());
                    resourceServer.setId(keep ? resourceServer.getId() : RandomString.generate());
                });
        return settings;
    }


    private Completable rejectDuplicates(Domain domain, TrustDomain td, TrustDomain beforeUpdate) {
        return rejectDuplicate(domain, td, beforeUpdate, TrustDomain::getName,
                repository::findByName, TrustDomainAlreadyExistsException::new)
                .andThen(rejectDuplicate(domain, td, beforeUpdate, TrustDomain::getSpiffeTrustDomain,
                        repository::findBySpiffeTrustDomain, TrustDomainSpiffeAlreadyExistsException::new))
                .andThen(rejectDuplicate(domain, td, beforeUpdate, TrustDomain::getIssuer,
                        repository::findByIssuer, TrustDomainIssuerAlreadyExistsException::new))
                .andThen(rejectDuplicateAudience(domain, td));
    }

    /**
     * An audience identifies one authorization server across the whole security domain. Stored as
     * JSON, so this reads the siblings instead of an index, with the issuer check's read-before-write
     * race.
     */
    private Completable rejectDuplicateAudience(Domain domain, TrustDomain td) {
        String audience = td.crossAppAccessAudience();
        if (audience == null) {
            return Completable.complete();
        }
        return Completable.defer(() -> repository.findByReference(ReferenceType.DOMAIN, domain.getId())
                .filter(sibling -> !Objects.equals(sibling.getId(), td.getId()))
                .filter(sibling -> audience.equals(sibling.crossAppAccessAudience()))
                .firstElement()
                .flatMapCompletable(taken -> Completable.error(new TrustDomainAudienceAlreadyExistsException(audience))));
    }

    private Completable rejectDuplicate(Domain domain,
                                        TrustDomain td,
                                        TrustDomain beforeUpdate,
                                        Function<TrustDomain, String> field,
                                        TrustDomainLookup lookup,
                                        Function<String, Throwable> conflict) {
        String value = field.apply(td);
        if (value == null) {
            return Completable.complete();
        }
        if (beforeUpdate != null && value.equals(field.apply(beforeUpdate))) {
            return Completable.complete();
        }
        return Completable.defer(() -> lookup.find(ReferenceType.DOMAIN, domain.getId(), value)
                .flatMapCompletable(found -> Completable.error(conflict.apply(value))));
    }

    @FunctionalInterface
    private interface TrustDomainLookup {
        Maybe<TrustDomain> find(ReferenceType referenceType, String referenceId, String value);
    }

    private Completable publish(Domain domain, TrustDomain trustDomain, Action action) {
        Event event = new Event(TRUST_DOMAIN, new Payload(trustDomain.getId(), trustDomain.getReferenceType(), trustDomain.getReferenceId(), action));
        return eventService.create(event, domain).ignoreElement();
    }

    /**
     * Maps the deprecated bundle-source input onto the shared key-material shape. The deprecated
     * fields are ignored whenever key material is supplied directly; a bare {@code jwksUrl} means
     * the JWKS-URL source, as it did before the bundle source became explicit.
     */
    private static TrustDomainKeyMaterial resolveKeyMaterial(TrustDomainKeyMaterial keyMaterial,
                                                             SpiffeBundleSource bundleSource,
                                                             String jwksUrl) {
        if (keyMaterial != null) {
            return keyMaterial;
        }
        if (bundleSource == null && jwksUrl == null) {
            return null;
        }
        return TrustDomainKeyMaterial.fromBundleSource(
                bundleSource != null ? bundleSource : SpiffeBundleSource.JWKS_URL, jwksUrl);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Completable validate(Domain domain, TrustDomain td) {
        SpiffeDomainSettings spiffeSettings = Optional.ofNullable(domain.getOidc())
                .map(o -> o.getWorkloadIdentitySettings())
                .orElseGet(SpiffeDomainSettings::defaultSettings);
        KeyRetrievalSettings settings = Optional.ofNullable(domain.getKeyRetrievalSettings())
                .orElseGet(KeyRetrievalSettings::defaultSettings);

        if (td.getName() == null) {
            return Completable.error(new InvalidTrustDomainException("name is required"));
        }
        if (td.getName().length() > TrustDomain.NAME_MAX_LENGTH) {
            return Completable.error(new InvalidTrustDomainException("name must be at most " + TrustDomain.NAME_MAX_LENGTH + " characters"));
        }
        Optional<String> matcherError = validateMatchers(td, spiffeSettings);
        if (matcherError.isPresent()) {
            return Completable.error(new InvalidTrustDomainException(matcherError.get()));
        }
        Optional<String> userBindingError = validateUserBinding(td);
        if (userBindingError.isPresent()) {
            return Completable.error(new InvalidTrustDomainException(userBindingError.get()));
        }
        Optional<String> crossAppAccessError = validateCrossAppAccess(td.getCrossAppAccess());
        if (crossAppAccessError.isPresent()) {
            return Completable.error(new InvalidTrustDomainException(crossAppAccessError.get()));
        }
        if (td.trustsSpiffe() || td.trustsTokenExchange()) {
            Optional<String> keyMaterialError = validateKeyMaterial(td.getKeyMaterial(), settings);
            if (keyMaterialError.isPresent()) {
                return Completable.error(new InvalidTrustDomainException(keyMaterialError.get()));
            }
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

    private Optional<String> validateMatchers(TrustDomain td, SpiffeDomainSettings spiffeSettings) {
        if (!td.trustsSpiffe() && !td.trustsTokenExchange() && !td.trustsCrossAppAccess()) {
            return Optional.of("a trusted domain must declare spiffeTrustDomain, issuer, or crossAppAccess");
        }
        if (td.trustsSpiffe()) {
            if (!spiffeSettings.isEnabled()) {
                return Optional.of("SPIFFE workload identity is disabled for this domain. Enable it in domain settings before registering a SPIFFE trust domain.");
            }
            if (!SPIFFE_TRUST_DOMAIN_PATTERN.matcher(td.getSpiffeTrustDomain()).matches()) {
                return Optional.of("spiffeTrustDomain must be a DNS-style label (lowercase letters, digits, '.' or '-')");
            }
            if (td.getSpiffeTrustDomain().length() > TrustDomain.SPIFFE_TRUST_DOMAIN_MAX_LENGTH) {
                return Optional.of("spiffeTrustDomain must be at most " + TrustDomain.SPIFFE_TRUST_DOMAIN_MAX_LENGTH + " characters");
            }
        }
        if (td.trustsTokenExchange() && td.getIssuer().length() > TrustDomain.ISSUER_MAX_LENGTH) {
            return Optional.of("issuer must be at most " + TrustDomain.ISSUER_MAX_LENGTH + " characters");
        }
        if (!td.trustsTokenExchange()) {
            if (td.getScopeMappings() != null && !td.getScopeMappings().isEmpty()) {
                return Optional.of("scopeMappings requires an issuer");
            }
            if (td.isUserBindingEnabled()) {
                return Optional.of("userBindingEnabled requires an issuer");
            }
        }
        return Optional.empty();
    }

    private Optional<String> validateUserBinding(TrustDomain td) {
        if (!td.isUserBindingEnabled()) {
            return Optional.empty();
        }
        List<UserBindingCriterion> criteria = td.getUserBindingCriteria();
        if (criteria == null || criteria.isEmpty()) {
            return Optional.of("userBindingCriteria must not be empty when user binding is enabled");
        }
        boolean incomplete = criteria.stream().anyMatch(c -> c == null
                || c.getAttribute() == null || c.getAttribute().isBlank()
                || c.getExpression() == null || c.getExpression().isBlank());
        if (incomplete) {
            return Optional.of("userBindingCriteria entries must have a non-blank attribute and expression");
        }
        return criteria.stream()
                .map(c -> parses(c.getExpression())
                        ? null
                        : "userBindingCriteria expression is not a valid expression: " + c.getExpression())
                .filter(Objects::nonNull)
                .findFirst();
    }

    private Optional<String> validateCrossAppAccess(CrossAppAccessSettings settings) {
        if (settings == null) {
            return Optional.empty();
        }
        Optional<String> audSubError = validateAudSubMapping(settings.getAudSubMapping());
        if (audSubError.isPresent()) {
            return audSubError;
        }
        Optional<String> scopeMappingError = validateOutboundScopeMappings(settings.getScopeMappings());
        if (scopeMappingError.isPresent()) {
            return scopeMappingError;
        }
        Optional<String> audienceError = validateAudience(settings.getAudience(), settings.isEnabled());
        if (audienceError.isPresent()) {
            return audienceError;
        }
        Optional<String> resourceServerError = validateResourceServers(settings.getResourceServers());
        if (resourceServerError.isPresent()) {
            return resourceServerError;
        }
        if (settings.isEnabled() && (settings.getResourceServers() == null || settings.getResourceServers().isEmpty())) {
            return Optional.of("crossAppAccess.resourceServers must declare at least one resource server when Cross App Access is enabled");
        }
        return Optional.empty();
    }

    private Optional<String> validateResourceServers(List<CrossAppAccessResourceServer> resourceServers) {
        if (resourceServers == null) {
            return Optional.empty();
        }
        Set<String> resources = new HashSet<>();
        for (CrossAppAccessResourceServer resourceServer : resourceServers) {
            if (resourceServer == null) {
                return Optional.of("crossAppAccess.resourceServers must not contain a null entry");
            }
            if (trimToNull(resourceServer.getName()) == null) {
                return Optional.of("crossAppAccess.resourceServers entries must have a non-blank name");
            }
            if (resourceServer.getName().length() > CrossAppAccessResourceServer.NAME_MAX_LENGTH) {
                return Optional.of("crossAppAccess.resourceServers name must be at most "
                        + CrossAppAccessResourceServer.NAME_MAX_LENGTH + " characters");
            }
            Optional<String> resourceError = validateResource(resourceServer.getResource());
            if (resourceError.isPresent()) {
                return resourceError;
            }
            if (!resources.add(resourceServer.getResource())) {
                return Optional.of("crossAppAccess.resourceServers must not repeat resource " + resourceServer.getResource());
            }
        }
        return Optional.empty();
    }

    private Optional<String> validateAudience(String audience, boolean enabled) {
        if (trimToNull(audience) == null) {
            return enabled
                    ? Optional.of("crossAppAccess.audience is required when Cross App Access is enabled")
                    : Optional.empty();
        }
        if (audience.length() > CrossAppAccessSettings.AUDIENCE_MAX_LENGTH) {
            return Optional.of("crossAppAccess.audience must be at most "
                    + CrossAppAccessSettings.AUDIENCE_MAX_LENGTH + " characters");
        }
        return isAbsolute(audience)
                ? Optional.empty()
                : Optional.of("crossAppAccess.audience must be an absolute URI: " + audience);
    }

    private Optional<String> validateResource(String resource) {
        if (trimToNull(resource) == null) {
            return Optional.of("crossAppAccess.resourceServers entries must have a non-blank resource");
        }
        if (resource.length() > CrossAppAccessResourceServer.RESOURCE_MAX_LENGTH) {
            return Optional.of("crossAppAccess.resourceServers resource must be at most "
                    + CrossAppAccessResourceServer.RESOURCE_MAX_LENGTH + " characters");
        }
        return isAbsolute(resource)
                ? Optional.empty()
                : Optional.of("crossAppAccess.resourceServers resource must be an absolute URI: " + resource);
    }

    private static boolean isAbsolute(String uri) {
        try {
            return new URI(uri).isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private Optional<String> validateOutboundScopeMappings(Map<String, String> scopeMappings) {
        if (scopeMappings == null) {
            return Optional.empty();
        }
        boolean incomplete = scopeMappings.entrySet().stream()
                .anyMatch(entry -> trimToNull(entry.getKey()) == null || trimToNull(entry.getValue()) == null);
        return incomplete
                ? Optional.of("crossAppAccess.scopeMappings must not contain a blank domain or external scope")
                : Optional.empty();
    }

    private Optional<String> validateAudSubMapping(String audSubMapping) {
        if (audSubMapping == null || audSubMapping.isBlank()) {
            return Optional.empty();
        }
        if (audSubMapping.length() > CrossAppAccessSettings.AUD_SUB_MAPPING_MAX_LENGTH) {
            return Optional.of("crossAppAccess.audSubMapping must be at most "
                    + CrossAppAccessSettings.AUD_SUB_MAPPING_MAX_LENGTH + " characters");
        }
        return parses(audSubMapping)
                ? Optional.empty()
                : Optional.of("crossAppAccess.audSubMapping is not a valid expression: " + audSubMapping);
    }

    /**
     * Parses with the engine the gateway evaluates the value with, so a typo is caught at save time
     * rather than at mint time. A value carrying no expression is a valid literal.
     */
    private static boolean parses(String expression) {
        try {
            EXPRESSION_PARSER.parseExpression(expression);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    private Optional<String> validateKeyMaterial(TrustDomainKeyMaterial keyMaterial, KeyRetrievalSettings settings) {
        if (keyMaterial == null || keyMaterial.getSource() == null) {
            return Optional.of("keyMaterial.source is required");
        }
        return switch (keyMaterial.getSource()) {
            case JWKS_URL -> validateJwksUrl(keyMaterial.getJwksUrl(), settings);
            case JWK_SET -> validateJwkSet(keyMaterial.getJwkSet());
            case PEM -> validateCertificate(keyMaterial.getCertificate());
        };
    }

    private Optional<String> validateJwkSet(JWKSet jwkSet) {
        if (jwkSet == null || jwkSet.getKeys() == null || jwkSet.getKeys().isEmpty()) {
            return Optional.of("keyMaterial.jwkSet must contain at least one key when source=JWK_SET");
        }
        return Optional.empty();
    }

    private Optional<String> validateJwksUrl(String jwksUrl, KeyRetrievalSettings settings) {
        if (jwksUrl == null || jwksUrl.isBlank()) {
            return Optional.of("keyMaterial.jwksUrl is required when source=JWKS_URL");
        }
        return PrivateAddressGuard.validateHttpUrl(
                "jwksUrl", jwksUrl, settings.isAllowUnsecuredHttpUri(), settings.isAllowPrivateIpAddress());
    }

    private Optional<String> validateCertificate(String certificate) {
        if (certificate == null || certificate.isBlank()) {
            return Optional.of("keyMaterial.certificate is required when source=PEM");
        }
        if (X509CertUtils.parse(certificate) == null) {
            return Optional.of("keyMaterial.certificate is not a valid PEM-encoded X.509 certificate");
        }
        return Optional.empty();
    }
}

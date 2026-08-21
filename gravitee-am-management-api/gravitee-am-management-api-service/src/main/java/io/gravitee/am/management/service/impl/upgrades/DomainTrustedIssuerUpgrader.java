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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.trustdomain.TrustedIssuerNaming;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.model.oidc.TrustDomainKind;
import io.gravitee.am.model.oidc.TrustDomainTokenExchangeSettings;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.DOMAIN_TRUSTED_ISSUER_UPGRADER;
import static java.util.stream.Collectors.toSet;

/**
 * Gives every trusted issuer declared inside a security domain's token-exchange settings a trusted
 * domain of its own. The inline declarations are left in place and stay authoritative; this upgrader
 * only makes the entities exist, so the migration can be proven before anything reads them.
 *
 * <p>Entities are written straight to the repository rather than through
 * {@code TrustDomainService}: that service applies the SSRF guard the inline shape never had, so an
 * issuer whose JWKS URL resolves to a private address — legal when it was configured — would fail
 * the upgrade rather than migrate.
 *
 * @author GraviteeSource Team
 */
@Component
@ManagementRepositoryScope
@CustomLog
public class DomainTrustedIssuerUpgrader extends SystemTaskUpgrader {

    private static final String TASK_ID = "trusted_issuer_trust_domain_migration";
    private static final String UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE =
            "Trusted issuers can't be migrated to trusted domains, other instance may process them or an upgrader has failed previously";

    private final DomainService domainService;
    private final TrustDomainRepository trustDomainRepository;

    public DomainTrustedIssuerUpgrader(@Lazy SystemTaskRepository systemTaskRepository,
                                       DomainService domainService,
                                       @Lazy TrustDomainRepository trustDomainRepository) {
        super(systemTaskRepository);
        this.domainService = domainService;
        this.trustDomainRepository = trustDomainRepository;
    }

    @Override
    protected Single<Boolean> processUpgrade(String instanceOperationId, SystemTask task, String previousOperationId) {
        return updateSystemTask(task, SystemTaskStatus.ONGOING, previousOperationId)
                .flatMap(ongoing -> {
                    if (!ongoing.getOperationId().equals(instanceOperationId)) {
                        return Single.error(new IllegalStateException("Task " + getTaskId() + " already processed by another instance : trigger a retry"));
                    }
                    return migrateDomains(ongoing)
                            .andThen(Single.just(true))
                            .onErrorResumeNext(err -> {
                                log.error("Unable to migrate trusted issuers (task: {}): {}", TASK_ID, err.getMessage());
                                return Single.just(false);
                            });
                });
    }

    private Completable migrateDomains(SystemTask task) {
        return domainService.listAll()
                .concatMapCompletable(this::migrateDomain)
                .doOnError(err -> updateSystemTask(task, SystemTaskStatus.FAILURE, task.getOperationId()).subscribe())
                .andThen(updateSystemTask(task, SystemTaskStatus.SUCCESS, task.getOperationId()).ignoreElement());
    }

    private Completable migrateDomain(Domain domain) {
        List<TrustedIssuer> issuers = declaredIssuers(domain);
        if (issuers.isEmpty()) {
            return Completable.complete();
        }
        return trustDomainRepository.findByReference(ReferenceType.DOMAIN, domain.getId())
                .filter(trustDomain -> trustDomain.getKind() == TrustDomainKind.TOKEN_EXCHANGE)
                .toList()
                .flatMapCompletable(existing -> migrateIssuers(domain, issuers, existing));
    }

    private Completable migrateIssuers(Domain domain, List<TrustedIssuer> issuers, List<TrustDomain> existing) {
        Set<String> alreadyVouchedFor = existing.stream()
                .map(TrustDomain::getTokenExchange)
                .filter(Objects::nonNull)
                .map(TrustDomainTokenExchangeSettings::getIssuer)
                .filter(Objects::nonNull)
                .collect(toSet());
        Set<String> takenNames = existing.stream().map(TrustDomain::getName).collect(toSet());
        Map<String, String> derivedNames = TrustedIssuerNaming.deriveNames(
                issuers.stream().map(TrustedIssuer::getIssuer).filter(issuer -> !alreadyVouchedFor.contains(issuer)).toList(),
                takenNames);

        return Flowable.fromIterable(issuers)
                .filter(issuer -> !alreadyVouchedFor.contains(issuer.getIssuer()))
                .concatMapSingle(issuer ->
                        migrateIssuer(domain, issuer, derivedNames.get(issuer.getIssuer()), takenNames))
                .all(migrated -> migrated)
                .flatMapCompletable(allMigrated -> allMigrated ? purgeInlineIssuers(domain) : Completable.complete());
    }

    private Single<Boolean> migrateIssuer(Domain domain, TrustedIssuer issuer, String name, Set<String> takenNames) {
        if (takenNames.contains(name)) {
            log.warn("Trusted issuer {} of domain {} is left inline: a trusted domain already holds its derived name {}",
                    issuer.getIssuer(), domain.getId(), name);
            return Single.just(false);
        }
        log.debug("Migrating trusted issuer {} of domain {} to trusted domain {}", issuer.getIssuer(), domain.getId(), name);
        return trustDomainRepository.create(asTrustDomain(domain, issuer, name)).map(created -> true);
    }

    /**
     * Drops the inline declarations now that the entities are authoritative. The storage layer no
     * longer persists them, so re-saving the security domain is what removes them.
     */
    private Completable purgeInlineIssuers(Domain domain) {
        domain.getTokenExchangeSettings().setTrustedIssuers(null);
        return domainService.update(domain.getId(), domain).ignoreElement();
    }

    private static TrustDomain asTrustDomain(Domain domain, TrustedIssuer issuer, String name) {
        Date now = Date.from(Instant.now());
        return TrustDomain.builder()
                .referenceType(ReferenceType.DOMAIN)
                .referenceId(domain.getId())
                .kind(TrustDomainKind.TOKEN_EXCHANGE)
                .name(name)
                .keyMaterial(keyMaterialOf(issuer))
                .refreshIntervalSeconds(TrustDomain.DEFAULT_REFRESH_INTERVAL_SECONDS)
                .tokenExchange(TrustDomainTokenExchangeSettings.builder()
                        .issuer(issuer.getIssuer())
                        .scopeMappings(issuer.getScopeMappings())
                        .userBindingEnabled(issuer.isUserBindingEnabled())
                        .userBindingCriteria(issuer.getUserBindingCriteria())
                        .build())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static TrustDomainKeyMaterial keyMaterialOf(TrustedIssuer issuer) {
        KeyResolutionMethod method = issuer.getKeyResolutionMethod();
        if (method == null) {
            return null;
        }
        return switch (method) {
            case JWKS_URL -> TrustDomainKeyMaterial.builder()
                    .source(KeyMaterialSource.JWKS_URL)
                    .jwksUrl(issuer.getJwksUri())
                    .build();
            case PEM -> TrustDomainKeyMaterial.builder()
                    .source(KeyMaterialSource.PEM)
                    .certificate(issuer.getCertificate())
                    .build();
        };
    }

    private static List<TrustedIssuer> declaredIssuers(Domain domain) {
        TokenExchangeSettings settings = domain.getTokenExchangeSettings();
        if (settings == null || settings.getTrustedIssuers() == null) {
            return List.of();
        }
        Map<String, TrustedIssuer> byIssuer = new LinkedHashMap<>();
        settings.getTrustedIssuers().stream()
                .filter(issuer -> issuer != null && StringUtils.hasText(issuer.getIssuer()))
                .forEach(issuer -> byIssuer.putIfAbsent(issuer.getIssuer(), issuer));
        return List.copyOf(byIssuer.values());
    }

    @Override
    protected IllegalStateException getIllegalStateException() {
        return new IllegalStateException(UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE);
    }

    @Override
    protected String getTaskId() {
        return TASK_ID;
    }

    @Override
    public int getOrder() {
        return DOMAIN_TRUSTED_ISSUER_UPGRADER;
    }
}

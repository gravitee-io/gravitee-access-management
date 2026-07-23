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

import io.gravitee.am.common.env.CloudProperties;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.LicenseService;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.exception.LicenseFeatureRequiredException;
import io.gravitee.node.api.license.License;
import io.gravitee.node.api.license.LicenseFactory;
import io.gravitee.node.api.license.LicenseManager;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginRegistry;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * @author GraviteeSource Team
 */
@CustomLog
@Component
public class PluginLicenseGateImpl implements PluginLicenseGate {

    // Domains never migrate organizations, so entries never need invalidation; the cap only bounds memory.
    private static final int MAX_ORGANIZATION_CACHE_SIZE = 10_000;

    private final PluginRegistry pluginRegistry;
    private final LicenseManager licenseManager;
    private final LicenseService licenseService;
    private final LicenseFactory licenseFactory;
    private final DomainReadService domainReadService;
    private final EnvironmentService environmentService;
    private final boolean managedCloudEnabled;

    private final ConcurrentMap<String, String> organizationIdByDomainId = new ConcurrentHashMap<>();

    public PluginLicenseGateImpl(@Lazy PluginRegistry pluginRegistry,
                                 @Lazy LicenseManager licenseManager,
                                 @Lazy LicenseService licenseService,
                                 @Lazy LicenseFactory licenseFactory,
                                 @Lazy DomainReadService domainReadService,
                                 @Lazy EnvironmentService environmentService,
                                 org.springframework.core.env.Environment environment) {
        this.pluginRegistry = pluginRegistry;
        this.licenseManager = licenseManager;
        this.licenseService = licenseService;
        this.licenseFactory = licenseFactory;
        this.domainReadService = domainReadService;
        this.environmentService = environmentService;
        this.managedCloudEnabled = CloudProperties.isManagedCloudEnabled(environment);
    }

    @Override
    public Completable check(Reference reference, String pluginType, String pluginId) {
        return doCheck(reference, pluginType, pluginId,
                organizationId -> Maybe.fromCallable(() -> licenseManager.getOrganizationLicense(organizationId)));
    }

    @Override
    public Completable checkPersisted(Reference reference, String pluginType, String pluginId) {
        return doCheck(reference, pluginType, pluginId, organizationId ->
                licenseService.findByReference(ReferenceType.ORGANIZATION, organizationId)
                        .flatMap(license -> parseLicense(organizationId, license.getLicense())));
    }

    private Completable doCheck(Reference reference, String pluginType, String pluginId,
                                Function<String, Maybe<License>> resolvedLicense) {
        if (!managedCloudEnabled) {
            return Completable.complete();
        }
        final String feature = resolveFeature(pluginType, pluginId);
        if (feature == null) {
            return Completable.complete();
        }
        return resolveOrganizationId(reference)
                .flatMapCompletable(organizationId -> resolvedLicense.apply(organizationId)
                        .map(license -> license.isFeatureEnabled(feature))
                        // no license for the organization -> only OSS plugins are allowed
                        .defaultIfEmpty(false)
                        .flatMapCompletable(enabled -> {
                            if (enabled) {
                                return Completable.complete();
                            }
                            log.debug("Feature {} required by plugin {} is not granted by the license of organization {}", feature, pluginId, organizationId);
                            return Completable.error(new LicenseFeatureRequiredException(feature, pluginId));
                        }));
    }

    private String resolveFeature(String pluginType, String pluginId) {
        if (pluginId == null) {
            return null;
        }
        final Plugin plugin = pluginRegistry.get(pluginType, pluginId);
        if (plugin == null || plugin.manifest() == null) {
            // unknown plugin: let the write path fail with its usual plugin-not-deployed error
            return null;
        }
        final String feature = plugin.manifest().feature();
        return feature == null || feature.isBlank() ? null : feature;
    }

    private Single<String> resolveOrganizationId(Reference reference) {
        return switch (reference.type()) {
            case ORGANIZATION -> Single.just(reference.id());
            case ENVIRONMENT -> environmentService.findById(reference.id()).map(Environment::getOrganizationId);
            case DOMAIN -> resolveOrganizationIdFromDomain(reference.id());
            default -> Single.error(new IllegalArgumentException("Cannot resolve an organization from reference " + reference));
        };
    }

    private Single<String> resolveOrganizationIdFromDomain(String domainId) {
        final String cachedOrganizationId = organizationIdByDomainId.get(domainId);
        if (cachedOrganizationId != null) {
            return Single.just(cachedOrganizationId);
        }
        return domainReadService.findById(domainId)
                .switchIfEmpty(Single.error(new DomainNotFoundException(domainId)))
                .flatMap(domain -> {
                    if (domain.getReferenceType() != ReferenceType.ENVIRONMENT) {
                        return Single.error(new EnvironmentNotFoundException("Domain " + domainId + " should be linked to an Environment"));
                    }
                    return environmentService.findById(domain.getReferenceId()).map(Environment::getOrganizationId);
                })
                .doOnSuccess(organizationId -> {
                    if (organizationIdByDomainId.size() >= MAX_ORGANIZATION_CACHE_SIZE) {
                        organizationIdByDomainId.clear();
                    }
                    organizationIdByDomainId.put(domainId, organizationId);
                });
    }

    private Maybe<License> parseLicense(String organizationId, String rawLicense) {
        try {
            return Maybe.just(licenseFactory.create(ReferenceType.ORGANIZATION.name(), organizationId, rawLicense));
        } catch (Exception e) {
            log.warn("Cannot read the license of organization {}", organizationId, e);
            return Maybe.empty();
        }
    }
}

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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyRetrievalSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.CustomLog;
import org.springframework.stereotype.Component;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.DOMAIN_KEY_RETRIEVAL_SETTINGS_UPGRADER;

/**
 * Copies the fetch, SSRF and cache limits a security domain persisted under its OIDC SPIFFE settings
 * into the domain-level block that now governs key retrieval for every trusted domain.
 *
 * @author GraviteeSource Team
 */
@Component
@ManagementRepositoryScope
@CustomLog
public class DomainKeyRetrievalSettingsUpgrader extends AsyncUpgrader {

    private final DomainService domainService;

    public DomainKeyRetrievalSettingsUpgrader(DomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    Completable doUpgrade() {
        log.info("Applying trusted domain key retrieval settings upgrade");
        return Completable.fromPublisher(domainService.listAll().flatMapMaybe(this::upgradeDomain));
    }

    private Maybe<Domain> upgradeDomain(Domain domain) {
        if (!needsRelocation(domain)) {
            return Maybe.empty();
        }
        SpiffeDomainSettings legacy = domain.getOidc().getWorkloadIdentitySettings();
        domain.setKeyRetrievalSettings(KeyRetrievalSettings.fromLegacySpiffeSettings(legacy));
        log.debug("Relocating key retrieval settings of domain {}", domain.getId());
        return Maybe.fromSingle(domainService.update(domain.getId(), domain));
    }

    private static boolean needsRelocation(Domain domain) {
        OIDCSettings oidc = domain.getOidc();
        SpiffeDomainSettings spiffe = oidc != null ? oidc.getWorkloadIdentitySettings() : null;
        return domain.getConfiguredKeyRetrievalSettings() == null
                && spiffe != null
                && spiffe.hasLegacyRetrievalSettings();
    }

    @Override
    public int getOrder() {
        return DOMAIN_KEY_RETRIEVAL_SETTINGS_UPGRADER;
    }
}

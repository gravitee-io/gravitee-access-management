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
package io.gravitee.am.management.service.impl.upgrades.system.upgraders;

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.idp.SystemClusterIdpPolicy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import lombok.CustomLog;
import org.springframework.stereotype.Component;

/**
 * The database of an identity provider pinned to the system cluster is written from the platform
 * settings at creation time, so it goes stale as soon as those settings change - a data plane added
 * or moved, a connection uri rewritten. The stored value is what the console shows and what an
 * operator reads when looking for the users of that provider, so it is realigned on every start
 * with the store the provider actually reads.
 *
 * <p>Keyed on the provider's own flag, not on the current setting: a provider created under the
 * regime stays in it whatever the setting becomes later, so its stored database must keep following
 * the platform even once the setting is turned off.
 *
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class SystemClusterIdpDatabaseUpgrader implements SystemUpgrader {

    private final IdentityProviderService identityProviderService;
    private final SystemClusterIdpPolicy systemClusterIdpPolicy;

    public SystemClusterIdpDatabaseUpgrader(IdentityProviderService identityProviderService,
                                            SystemClusterIdpPolicy systemClusterIdpPolicy) {
        this.identityProviderService = identityProviderService;
        this.systemClusterIdpPolicy = systemClusterIdpPolicy;
    }

    @Override
    public Completable upgrade() {
        return identityProviderService.findAll()
                .filter(IdentityProvider::isSystemClusterRestricted)
                // concatMap: the node is starting, so the repairs go one at a time rather than
                // opening as many writes as there are identity providers.
                .concatMap(this::refreshDatabase)
                .ignoreElements();
    }

    private Flowable<IdentityProvider> refreshDatabase(IdentityProvider identityProvider) {
        // An unchanged database yields nothing, so a start that has nothing to repair emits no
        // identity provider event and leaves the gateways alone.
        return systemClusterIdpPolicy.refreshPinnedDatabase(identityProvider)
                .map(repinned -> identityProviderService.updatePinnedStorage(identityProvider, repinned.configuration())
                        .doOnSuccess(updated -> log.info("Repinned IDP id={} to database {}", updated.getId(), repinned.database()))
                        .toFlowable())
                .orElseGet(Flowable::empty);
    }

    @Override
    public int getOrder() {
        return SystemUpgraderOrder.SYSTEM_CLUSTER_IDP_DATABASE_UPGRADER;
    }
}

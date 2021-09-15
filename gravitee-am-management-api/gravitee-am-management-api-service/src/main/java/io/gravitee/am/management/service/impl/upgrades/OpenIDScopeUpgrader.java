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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.model.NewSystemScope;
import io.gravitee.am.service.model.UpdateSystemScope;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.OPENID_SCOPE_UPGRADER;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class OpenIDScopeUpgrader implements Upgrader, Ordered {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(OpenIDScopeUpgrader.class);

    @Autowired
    private DomainService domainService;

    @Autowired
    private ScopeService scopeService;

    @Override
    public boolean upgrade() {
        logger.info("Applying OIDC scope upgrade");
        domainService.findAll()
                .flatMapObservable(Observable::fromIterable)
                .flatMapSingle(this::createOrUpdateSystemScopes)
                .subscribe();
        return true;
    }

    private Single<Domain> createOrUpdateSystemScopes(Domain domain) {
        return Observable.fromArray(io.gravitee.am.common.oidc.Scope.values())
                .flatMapSingle(scope -> createSystemScope(domain.getId(), scope))
                .lastOrError()
                .map(scope -> domain);
    }

    private Single<Scope> createSystemScope(String domain, io.gravitee.am.common.oidc.Scope systemScope) {
        return scopeService.findByDomainAndKey(domain, systemScope.getKey())
                .map(scope -> Optional.of(scope))
                .defaultIfEmpty(Optional.empty())
                .flatMapSingle(optScope -> {
                    if (!optScope.isPresent()) {
                        logger.info("Create a new system scope key[{}] for domain[{}]", systemScope.getKey(), domain);
                        NewSystemScope scope = new NewSystemScope();
                        scope.setKey(systemScope.getKey());
                        scope.setClaims(systemScope.getClaims());
                        scope.setName(systemScope.getLabel());
                        scope.setDescription(systemScope.getDescription());
                        scope.setDiscovery(systemScope.isDiscovery());
                        return scopeService.create(domain, scope);
                    } else if (shouldUpdateSystemScope(optScope, systemScope)){
                        logger.info("Update a system scope key[{}] for domain[{}]", systemScope.getKey(), domain);
                        final Scope existingScope = optScope.get();
                        UpdateSystemScope scope = new UpdateSystemScope();
                        scope.setName(existingScope.getName() != null ? existingScope.getName() : systemScope.getLabel());
                        scope.setDescription(existingScope.getDescription() != null ? existingScope.getDescription() : systemScope.getDescription());
                        scope.setClaims(systemScope.getClaims());
                        scope.setExpiresIn(existingScope.getExpiresIn());
                        scope.setDiscovery(systemScope.isDiscovery());
                        return scopeService.update(domain, optScope.get().getId(), scope);
                    }
                    return Single.just(optScope.get());
                });
    }

    /**
     * Update System scope if it is not currently set as system or if discovery property does not match.
     * @param optScope
     * @param systemScope
     * @return
     */
    private boolean shouldUpdateSystemScope(Optional<Scope> optScope, io.gravitee.am.common.oidc.Scope systemScope) {
        //If not currently set as system or if discovery property does not match
        return !optScope.get().isSystem() || optScope.get().isDiscovery() != systemScope.isDiscovery();
    }

    @Override
    public int getOrder() {
        return OPENID_SCOPE_UPGRADER;
    }
}

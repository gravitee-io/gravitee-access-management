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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.model.NewScope;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.SCOPE_UPGRADER;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@RequiredArgsConstructor
@ManagementRepositoryScope
public class ScopeUpgrader extends AsyncUpgrader {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(ScopeUpgrader.class);

    private final DomainService domainService;
    private final ScopeService scopeService;
    private final ApplicationService applicationService;
    private final RoleService roleService;

    @Override
    public Completable doUpgrade() {
        logger.info("Applying scope upgrade");
        return Completable.fromPublisher(domainService.listAll()
                .flatMapSingle(this::upgradeDomain));
    }

    private Single<List<Scope>> upgradeDomain(Domain domain) {
        logger.info("Looking for scopes for domain id[{}] name[{}]", domain.getId(), domain.getName());
        return scopeService.findByDomain(domain.getId(), 0, Integer.MAX_VALUE)
                .flatMap(scopes -> {
                    if (scopes.getData().isEmpty()) {
                        logger.info("No scope found for domain id[{}] name[{}]. Upgrading...", domain.getId(), domain.getName());
                        return createAppScopes(domain)
                                .flatMap(irrelevant -> createRoleScopes(domain));
                    }
                    logger.info("No scope to update, skip upgrade");
                    return Single.just(new ArrayList<>(scopes.getData()));
                });
    }

    private Single<List<Scope>> createAppScopes(Domain domain) {
        return applicationService.findByDomain(domain.getId())
                .filter(Objects::nonNull)
                .flatMapObservable(Observable::fromIterable)
                .filter(app -> app.getSettings() != null && app.getSettings().getOauth() != null)
                .flatMap(app -> Observable.fromIterable(app.getSettings().getOauth().getScopes()))
                .flatMapSingle(scope -> createScope(domain, scope))
                .toList();
    }

    private Single<List<Scope>> createRoleScopes(Domain domain) {
        return roleService.findByDomain(domain.getId())
                .filter(Objects::nonNull)
                .flatMapObservable(Observable::fromIterable)
                .filter(role -> role.getOauthScopes() != null)
                .flatMap(role -> Observable.fromIterable(role.getOauthScopes()))
                .flatMapSingle(scope -> createScope(domain, scope))
                .toList();
    }

    private Single<Scope> createScope(Domain domain, String scopeKey) {
        return scopeService.findByDomain(domain.getId(), 0, Integer.MAX_VALUE)
                .flatMap(scopes -> {
                    Optional<Scope> optScope = scopes.getData().stream().filter(scope -> scope.getKey().equalsIgnoreCase(scopeKey)).findFirst();
                    if (optScope.isEmpty()) {
                        logger.info("Create a new scope key[{}] for domain[{}]", scopeKey, domain);
                        NewScope scope = new NewScope();
                        scope.setKey(scopeKey);
                        scope.setName(Character.toUpperCase(scopeKey.charAt(0)) + scopeKey.substring(1));
                        scope.setDescription("Default description for scope " + scopeKey);
                        return scopeService.create(domain, scope);
                    }
                    return Single.just(optScope.get());
                });
    }

    @Override
    public int getOrder() {
        return SCOPE_UPGRADER;
    }
}

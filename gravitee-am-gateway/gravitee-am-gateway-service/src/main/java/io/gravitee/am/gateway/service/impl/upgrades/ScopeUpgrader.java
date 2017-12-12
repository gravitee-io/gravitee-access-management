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
package io.gravitee.am.gateway.service.impl.upgrades;

import io.gravitee.am.gateway.service.ClientService;
import io.gravitee.am.gateway.service.DomainService;
import io.gravitee.am.gateway.service.RoleService;
import io.gravitee.am.gateway.service.ScopeService;
import io.gravitee.am.gateway.service.model.NewScope;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.oauth2.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Order(160)
public class ScopeUpgrader implements Upgrader {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(ScopeUpgrader.class);

    @Autowired
    private DomainService domainService;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private RoleService roleService;

    @Override
    public boolean upgrade() {
        logger.info("Applying scope upgrade");
        Set<Domain> domains = domainService.findAll();

        domains.forEach(this::upgradeDomain);

        return true;
    }

    private void upgradeDomain(Domain domain) {
        logger.info("Looking for scopes for domain id[{}] name[{}]", domain.getId(), domain.getName());
        Set<Scope> scopes = scopeService.findByDomain(domain.getId());
        if (scopes.isEmpty()) {
            logger.info("No scope found for domain id[{}] name[{}]. Upgrading...", domain.getId(), domain.getName());

            createClientScopes(domain);
            createRoleScopes(domain);
        }
    }

    private void createClientScopes(Domain domain) {
        Set<Client> clients = clientService.findByDomain(domain.getId());

        if (clients != null) {
            clients.forEach(client -> client.getScopes().forEach(scope -> {
                createScope(domain.getId(), scope);
            }));
        }
    }

    private void createRoleScopes(Domain domain) {
        Set<Role> roles = roleService.findByDomain(domain.getId());

        if (roles != null) {
            roles.forEach(role -> role.getPermissions().forEach(scope -> {
                createScope(domain.getId(), scope);
            }));
        }
    }

    private void createScope(String domain, String scopeKey) {
        Set<Scope> scopes = scopeService.findByDomain(domain);
        Optional<Scope> optScope = scopes.stream().filter(scope -> scope.getKey().equalsIgnoreCase(scopeKey)).findFirst();
        if (!optScope.isPresent()) {
            logger.info("Create a new scope key[{}] for domain[{}]", scopeKey, domain);
            NewScope scope = new NewScope();
            scope.setKey(scopeKey);
            scope.setName(Character.toUpperCase(scopeKey.charAt(0)) + scopeKey.substring(1));
            scope.setDescription("Default description for scope " + scopeKey);
            scopeService.create(domain, scope);
        }
    }
}

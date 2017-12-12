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
package io.gravitee.am.gateway.service.impl;

import io.gravitee.am.gateway.service.ClientService;
import io.gravitee.am.gateway.service.RoleService;
import io.gravitee.am.gateway.service.ScopeService;
import io.gravitee.am.gateway.service.exception.ScopeAlreadyExistsException;
import io.gravitee.am.gateway.service.exception.ScopeNotFoundException;
import io.gravitee.am.gateway.service.exception.TechnicalManagementException;
import io.gravitee.am.gateway.service.model.NewScope;
import io.gravitee.am.gateway.service.model.UpdateClient;
import io.gravitee.am.gateway.service.model.UpdateRole;
import io.gravitee.am.gateway.service.model.UpdateScope;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ScopeRepository;
import io.gravitee.common.utils.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ScopeServiceImpl implements ScopeService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(ScopeServiceImpl.class);

    @Autowired
    private ScopeRepository scopeRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private ClientService clientService;

    @Override
    public Scope findById(String id) {
        try {
            LOGGER.debug("Find scope by ID: {}", id);
            Optional<Scope> scopeOpt = scopeRepository.findById(id);

            if (!scopeOpt.isPresent()) {
                throw new ScopeNotFoundException(id);
            }

            return scopeOpt.get();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find a scope using its ID: {}", id, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find a scope using its ID: %s", id), ex);
        }
    }

    @Override
    public Scope create(String domain, NewScope newScope) {
        try {
            LOGGER.debug("Create a new scope {} for domain {}", newScope, domain);

            String scopeKey = newScope.getKey().toLowerCase();
            Optional<Scope> scopeOpt = scopeRepository.findByDomainAndKey(domain, scopeKey);

            if (scopeOpt.isPresent()) {
                throw new ScopeAlreadyExistsException(scopeKey, domain);
            }

            Scope scope = new Scope();
            scope.setId(UUID.toString(UUID.random()));
            scope.setDomain(domain);
            scope.setKey(scopeKey);
            scope.setName(newScope.getName());
            scope.setDescription(newScope.getDescription());
            scope.setCreatedAt(new Date());
            scope.setUpdatedAt(new Date());

            scopeRepository.create(scope);

            return scope;
        } catch (Exception ex) {
            LOGGER.error("An error occurs while trying to create a scope", ex);
            throw new TechnicalManagementException("An error occurs while trying to create a scope", ex);
        }
    }

    @Override
    public Scope update(String domain, String id, UpdateScope updateScope) {
        try {
            LOGGER.debug("Update a scope {} for domain {}", id, domain);

            Optional<Scope> scopeOpt = scopeRepository.findById(id);
            if (!scopeOpt.isPresent()) {
                throw new ScopeNotFoundException(id);
            }

            Scope scope = scopeOpt.get();
            scope.setName(updateScope.getName());
            scope.setDescription(updateScope.getDescription());
            scope.setNames(updateScope.getNames());
            scope.setDescriptions(updateScope.getDescriptions());
            scope.setUpdatedAt(new Date());

            return scopeRepository.update(scope);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to update a scope", ex);
            throw new TechnicalManagementException("An error occurs while trying to update a scope", ex);
        }
    }

    @Override
    public void delete(String scopeId) {
        try {
            LOGGER.debug("Delete scope {}", scopeId);

            Optional<Scope> optScope = scopeRepository.findById(scopeId);
            if (! optScope.isPresent()) {
                throw new ScopeNotFoundException(scopeId);
            }

            Scope scope = optScope.get();

            // 1_ Remove permissions from role
            roleService.findByDomain(scope.getDomain())
                    .stream()
                    .filter(role -> role.getPermissions().contains(scope.getKey()))
                    .forEach(role -> {
                        // Remove permission from role
                        role.getPermissions().remove(scope.getKey());
                        UpdateRole updatedRole = new UpdateRole();
                        updatedRole.setName(role.getName());
                        updatedRole.setDescription(role.getDescription());
                        updatedRole.setPermissions(role.getPermissions());

                        // Save role
                        roleService.update(scope.getDomain(), role.getId(), updatedRole);
                    });

            // 2_ Remove scopes from client
            clientService.findByDomain(scope.getDomain())
                    .stream()
                    .filter(client -> client.getScopes().contains(scope.getKey()))
                    .forEach(client -> {
                        // Remove scope from client
                        client.getScopes().remove(scope.getKey());

                        UpdateClient updateClient = new UpdateClient();
                        updateClient.setAutoApproveScopes(client.getAutoApproveScopes());
                        updateClient.setScopes(client.getScopes());
                        updateClient.setRefreshTokenValiditySeconds(client.getRefreshTokenValiditySeconds());
                        updateClient.setRedirectUris(client.getRedirectUris());
                        updateClient.setAccessTokenValiditySeconds(client.getAccessTokenValiditySeconds());
                        updateClient.setAuthorizedGrantTypes(client.getAuthorizedGrantTypes());
                        updateClient.setCertificate(client.getCertificate());
                        updateClient.setEnabled(client.isEnabled());
                        updateClient.setEnhanceScopesWithUserPermissions(client.isEnhanceScopesWithUserPermissions());
                        updateClient.setGenerateNewTokenPerRequest(client.isGenerateNewTokenPerRequest());
                        updateClient.setIdentities(client.getIdentities());
                        updateClient.setIdTokenCustomClaims(client.getIdTokenCustomClaims());
                        updateClient.setIdTokenValiditySeconds(client.getIdTokenValiditySeconds());

                        // Save client
                        clientService.update(scope.getDomain(), client.getId(), updateClient);
                    });
            scopeRepository.delete(scopeId);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to delete scope: {}", scopeId, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to delete scope: %s", scopeId), ex);
        }
    }

    @Override
    public Set<Scope> findByDomain(String domain) {
        try {
            LOGGER.debug("Find scopes by domain", domain);
            return scopeRepository.findByDomain(domain);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find scopes by domain: {}", domain, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find scopes by domain: %s", domain), ex);
        }
    }
}

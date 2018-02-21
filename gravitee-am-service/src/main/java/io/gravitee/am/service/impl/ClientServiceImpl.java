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

import io.gravitee.am.model.Client;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.ClientAlreadyExistsException;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewClient;
import io.gravitee.am.service.model.TopClient;
import io.gravitee.am.service.model.TotalClient;
import io.gravitee.am.service.model.UpdateClient;
import io.gravitee.common.utils.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ClientServiceImpl implements ClientService {

    private final Logger LOGGER = LoggerFactory.getLogger(ClientServiceImpl.class);

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private DomainService domainService;

    @Override
    public Client findById(String id) {
        try {
            LOGGER.debug("Find client by ID: {}", id);
            // TODO move to async call
            Optional<Client> clientOpt = Optional.ofNullable(clientRepository.findById(id).blockingGet());

            if (!clientOpt.isPresent()) {
                throw new ClientNotFoundException(id);
            }

            Client client = clientOpt.get();
            // Send an empty array in case of no grant types
            if (client.getAuthorizedGrantTypes() == null) {
                client.setAuthorizedGrantTypes(Collections.emptyList());
            }

            return client;
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find a client using its ID: {}", id, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find a client using its ID: %s", id), ex);
        }
    }

    @Override
    public Client findByDomainAndClientId(String domain, String clientId) {
        try {
            // TODO move to async call
            Optional<Client> clientOpt = Optional.ofNullable(clientRepository.findByClientIdAndDomain(clientId, domain).blockingGet());
            if (!clientOpt.isPresent()) {
                throw new ClientNotFoundException(clientId);
            }

            return clientOpt.get();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find client by domain: {} and client id: {}", domain, clientId, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find client by domain: %s and client id: %s", domain, clientId), ex);
        }
    }

    @Override
    public Set<Client> findByDomain(String domain) {
        try {
            LOGGER.debug("Find clients by domain", domain);
            // TODO move to async call
            return clientRepository.findByDomain(domain).blockingGet();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find clients by domain: {}", domain, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find clients by domain: %s", domain), ex);
        }
    }

    @Override
    public Page<Client> findByDomain(String domain, int page, int size) {
        try {
            LOGGER.debug("Find clients by domain", domain);
            // TODO move to async call
            return clientRepository.findByDomain(domain, page, size).blockingGet();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find clients by domain: {}", domain, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find clients by domain: %s", domain), ex);
        }
    }

    @Override
    public Set<Client> findByIdentityProvider(String identityProvider) {
        try {
            LOGGER.debug("Find clients by identity provider : {}", identityProvider);
            // TODO move to async call
            return clientRepository.findByIdentityProvider(identityProvider).blockingGet();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find clients by identity provider", ex);
            throw new TechnicalManagementException("An error occurs while trying to find clients by identity provider", ex);
        }
    }

    @Override
    public Set<Client> findByCertificate(String certificate) {
        try {
            LOGGER.debug("Find clients by certificate : {}", certificate);
            // TODO move to async call
            return clientRepository.findByCertificate(certificate).blockingGet();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find clients by certificate", ex);
            throw new TechnicalManagementException("An error occurs while trying to find clients by certificate", ex);
        }
    }

    @Override
    public Set<Client> findByExtensionGrant(String extensionGrant) {
        try {
            LOGGER.debug("Find clients by extension grant : {}", extensionGrant);
            // TODO move to async call
            return clientRepository.findByExtensionGrant(extensionGrant).blockingGet();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find clients by extension grant", ex);
            throw new TechnicalManagementException("An error occurs while trying to find clients by extension grant", ex);
        }
    }

    @Override
    public Set<Client> findAll() {
        try {
            LOGGER.debug("Find clients");
            // TODO move to async call
            return clientRepository.findAll().blockingGet();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find clients", ex);
            throw new TechnicalManagementException("An error occurs while trying to find clients", ex);
        }
    }

    @Override
    public Page<Client> findAll(int page, int size) {
        try {
            LOGGER.debug("Find clients");
            // TODO move to async call
            return clientRepository.findAll(page, size).blockingGet();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find clients", ex);
            throw new TechnicalManagementException("An error occurs while trying to find clients", ex);
        }
    }

    @Override
    public Set<TopClient> findTopClients() {
        try {
            LOGGER.debug("Find top clients");
            // TODO move to async call
            Set<Client> clients = clientRepository.findAll().blockingGet();
            return clients.parallelStream().map(c -> {
                TopClient client = new TopClient();
                client.setClient(c);
                // TODO move to async call
                client.setAccessTokens(tokenRepository.findTokensByClientId(c.getClientId()).blockingGet().size());
                return client;
            }).filter(topClient -> topClient.getAccessTokens() > 0).collect(Collectors.toSet());
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find top clients", ex);
            throw new TechnicalManagementException("An error occurs while trying to find top clients", ex);
        }
    }

    @Override
    public Set<TopClient> findTopClientsByDomain(String domain) {
        try {
            LOGGER.debug("Find top clients by domain: {}", domain);
            // TODO move to async call
            Set<Client> clients = clientRepository.findByDomain(domain).blockingGet();
            return clients.parallelStream().map(c -> {
                TopClient client = new TopClient();
                client.setClient(c);
                // TODO move to async call
                client.setAccessTokens(tokenRepository.findTokensByClientId(c.getClientId()).blockingGet().size());
                return client;
            }).filter(topClient -> topClient.getAccessTokens() > 0).collect(Collectors.toSet());
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find top clients by domain", ex);
            throw new TechnicalManagementException("An error occurs while trying to find top clients by domain", ex);
        }
    }

    @Override
    public TotalClient findTotalClientsByDomain(String domain) {
        try {
            LOGGER.debug("Find total clients by domain: {}", domain);
            TotalClient totalClient = new TotalClient();
            // TODO move to async call
            totalClient.setTotalClients(clientRepository.countByDomain(domain).blockingGet());
            return totalClient;
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find total clients by domain: {}", domain, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find total clients by domain: %s", domain), ex);
        }
    }

    @Override
    public TotalClient findTotalClients() {
        try {
            LOGGER.debug("Find total client");
            TotalClient totalClient = new TotalClient();
            // TODO move to async call
            totalClient.setTotalClients(clientRepository.count().blockingGet());
            return totalClient;
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find total clients", ex);
            throw new TechnicalManagementException("An error occurs while trying to find total clients", ex);
        }
    }

    @Override
    public Client create(String domain, NewClient newClient) {
        try {
            LOGGER.debug("Create a new client {} for domain {}", newClient, domain);

            // TODO move to async call
            Optional<Client> clientOpt = Optional.ofNullable(clientRepository.findByClientIdAndDomain(newClient.getClientId(), domain).blockingGet());
            if (clientOpt.isPresent()) {
                throw new ClientAlreadyExistsException(newClient.getClientId(), domain);
            }

            Client client = new Client();
            client.setId(UUID.toString(UUID.random()));
            client.setClientId(newClient.getClientId());
            if (newClient.getClientSecret() == null || newClient.getClientSecret().trim().isEmpty()) {
                client.setClientSecret(UUID.toString(UUID.random()));
            } else {
                client.setClientSecret(newClient.getClientSecret());
            }
            client.setDomain(domain);
            client.setAccessTokenValiditySeconds(Client.DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS);
            client.setRefreshTokenValiditySeconds(Client.DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS);
            client.setIdTokenValiditySeconds(Client.DEFAULT_ID_TOKEN_VALIDITY_SECONDS);
            client.setAuthorizedGrantTypes(Client.AUTHORIZED_GRANT_TYPES);
            client.setEnabled(true);
            client.setCreatedAt(new Date());
            client.setUpdatedAt(client.getCreatedAt());

            // TODO move to async call
            return clientRepository.create(client).blockingGet();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to create a client", ex);
            throw new TechnicalManagementException("An error occurs while trying to create a client", ex);
        }
    }

    @Override
    public Client update(String domain, String id, UpdateClient updateClient) {
        try {
            LOGGER.debug("Update a client {} for domain {}", id, domain);

            // TODO move to async call
            Optional<Client> clientOpt = Optional.ofNullable(clientRepository.findById(id).blockingGet());
            if (!clientOpt.isPresent()) {
                throw new ClientNotFoundException(id);
            }

            // Check associated identity providers
            Set<String> identities = updateClient.getIdentities();
            if (identities != null) {
                identities.forEach(identityProviderId -> identityProviderService.findById(identityProviderId));
            }

            Client client = clientOpt.get();
            client.setScopes(updateClient.getScopes());
            client.setAutoApproveScopes(updateClient.getAutoApproveScopes());
            client.setAccessTokenValiditySeconds(updateClient.getAccessTokenValiditySeconds());
            client.setRefreshTokenValiditySeconds(updateClient.getRefreshTokenValiditySeconds());
            client.setAuthorizedGrantTypes(updateClient.getAuthorizedGrantTypes());
            client.setRedirectUris(updateClient.getRedirectUris());
            client.setEnabled(updateClient.isEnabled());
            client.setIdentities(updateClient.getIdentities());
            client.setOauth2Identities(updateClient.getOauth2Identities());
            client.setIdTokenValiditySeconds(updateClient.getIdTokenValiditySeconds());
            client.setIdTokenCustomClaims(updateClient.getIdTokenCustomClaims());
            client.setCertificate(updateClient.getCertificate());
            client.setEnhanceScopesWithUserPermissions(updateClient.isEnhanceScopesWithUserPermissions());
            client.setGenerateNewTokenPerRequest(updateClient.isGenerateNewTokenPerRequest());
            client.setUpdatedAt(new Date());

            // TODO move to async call
            Client clientUpdated = clientRepository.update(client).blockingGet();

            // Reload domain to take care about client update
            domainService.reload(domain);

            return clientUpdated;
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to update a client", ex);
            throw new TechnicalManagementException("An error occurs while trying to update a client", ex);
        }
    }

    @Override
    public void delete(String clientId) {
        try {
            LOGGER.debug("Delete client {}", clientId);

            // TODO move to async call
            Optional<Client> optClient = Optional.ofNullable(clientRepository.findById(clientId).blockingGet());
            if (! optClient.isPresent()) {
                throw new ClientNotFoundException(clientId);
            }

            // TODO move to async call
            clientRepository.delete(clientId).subscribe();

            // Reload domain to take care about client delete
            domainService.reload(optClient.get().getDomain());
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to delete client: {}", clientId, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to delete client: %s", clientId), ex);
        }
    }
}

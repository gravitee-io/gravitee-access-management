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
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.ClientAlreadyExistsException;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewClient;
import io.gravitee.am.service.model.TopClient;
import io.gravitee.am.service.model.TotalClient;
import io.gravitee.am.service.model.UpdateClient;
import io.gravitee.common.utils.UUID;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
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
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
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
    public Maybe<Client> findById(String id) {
        LOGGER.debug("Find client by ID: {}", id);
        return clientRepository.findById(id)
                .map(client -> {
                    // Send an empty array in case of no grant types
                    if (client.getAuthorizedGrantTypes() == null) {
                        client.setAuthorizedGrantTypes(Collections.emptyList());
                    }
                    return client;
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a client using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a client using its ID: %s", id), ex));
                });
    }

    @Override
    public Maybe<Client> findByDomainAndClientId(String domain, String clientId) {
        LOGGER.debug("Find client by domain: {} and client id: {}", domain, clientId);
        return clientRepository.findByClientIdAndDomain(clientId, domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find client by domain: {} and client id: {}", domain, clientId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find client by domain: %s and client id: %s", domain, clientId), ex));
                });
    }

    @Override
    public Single<Set<Client>> findByDomain(String domain) {
        LOGGER.debug("Find clients by domain", domain);
        return clientRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find clients by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find clients by domain: %s", domain), ex));
                });
    }

    @Override
    public Single<Page<Client>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("Find clients by domain", domain);
        return clientRepository.findByDomain(domain, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find clients by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find clients by domain: %s", domain), ex));
                });
    }

    @Override
    public Single<Set<Client>> findByIdentityProvider(String identityProvider) {
        LOGGER.debug("Find clients by identity provider : {}", identityProvider);
        return clientRepository.findByIdentityProvider(identityProvider)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find clients by identity provider", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find clients by identity provider", ex));
                });
    }

    @Override
    public Single<Set<Client>> findByCertificate(String certificate) {
        LOGGER.debug("Find clients by certificate : {}", certificate);
        return clientRepository.findByCertificate(certificate)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find clients by certificate", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find clients by certificate", ex));
                });
    }

    @Override
    public Single<Set<Client>> findByExtensionGrant(String extensionGrant) {
        LOGGER.debug("Find clients by extension grant : {}", extensionGrant);
        return clientRepository.findByExtensionGrant(extensionGrant)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find clients by extension grant", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find clients by extension grant", ex));
                });
    }

    @Override
    public Single<Set<Client>> findAll() {
        LOGGER.debug("Find clients");
        return clientRepository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find clients", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find clients", ex));
                });
    }

    @Override
    public Single<Page<Client>> findAll(int page, int size) {
        LOGGER.debug("Find clients");
        return clientRepository.findAll(page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find clients", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find clients", ex));
                });
    }

    @Override
    public Single<Set<TopClient>> findTopClients() {
        LOGGER.debug("Find top clients");
        return clientRepository.findAll()
                .flatMapObservable(clients -> Observable.fromIterable(clients))
                .flatMapSingle(client -> tokenRepository.findTokensByClientId(client.getClientId())
                                                            .map(oAuth2AccessTokens -> {
                                                                TopClient topClient = new TopClient();
                                                                topClient.setClient(client);
                                                                topClient.setAccessTokens(oAuth2AccessTokens.size());
                                                                return topClient;
                                                            }))
                .toList()
                .map(topClients -> topClients.stream().filter(topClient -> topClient.getAccessTokens() > 0).collect(Collectors.toSet()))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find top clients", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find top clients", ex));
                });
    }

    @Override
    public Single<Set<TopClient>> findTopClientsByDomain(String domain) {
        LOGGER.debug("Find top clients by domain: {}", domain);
        return clientRepository.findByDomain(domain)
                .flatMapObservable(clients -> Observable.fromIterable(clients))
                .flatMapSingle(client -> tokenRepository.findTokensByClientId(client.getClientId())
                        .map(oAuth2AccessTokens -> {
                            TopClient topClient = new TopClient();
                            topClient.setClient(client);
                            topClient.setAccessTokens(oAuth2AccessTokens.size());
                            return topClient;
                        }))
                .toList()
                .map(topClients -> topClients.stream().filter(topClient -> topClient.getAccessTokens() > 0).collect(Collectors.toSet()))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find top clients by domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find top clients by domain", ex));
                });
    }

    @Override
    public Single<TotalClient> findTotalClientsByDomain(String domain) {
        LOGGER.debug("Find total clients by domain: {}", domain);
        return clientRepository.countByDomain(domain)
                .map(totalClients -> {
                    TotalClient totalClient = new TotalClient();
                    totalClient.setTotalClients(totalClients);
                    return totalClient;
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find total clients by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find total clients by domain: %s", domain), ex));
                });
    }

    @Override
    public Single<TotalClient> findTotalClients() {
        LOGGER.debug("Find total client");
        return clientRepository.count()
                .map(totalClients -> {
                    TotalClient totalClient = new TotalClient();
                    totalClient.setTotalClients(totalClients);
                    return totalClient;
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find total clients", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find total clients", ex));
                });
    }

    @Override
    public Single<Client> create(String domain, NewClient newClient) {
        LOGGER.debug("Create a new client {} for domain {}", newClient, domain);
        return clientRepository.findByClientIdAndDomain(newClient.getClientId(), domain)
                .isEmpty()
                    .flatMap(empty -> {
                        if (!empty) {
                            throw new ClientAlreadyExistsException(newClient.getClientId(), domain);
                        } else {
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

                            return clientRepository.create(client);
                        }
                    })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a client", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a client", ex));
                });
    }

    @Override
    public Single<Client> update(String domain, String id, UpdateClient updateClient) {
        LOGGER.debug("Update a client {} for domain {}", id, domain);
        return clientRepository.findById(id)
                .map(client -> Optional.of(client))
                .defaultIfEmpty(Optional.empty())
                .toSingle()
                .flatMap(clientOpt -> {
                    if (!clientOpt.isPresent()) {
                        throw new ClientNotFoundException(id);
                    }
                    return Single.just(clientOpt.get());
                })
                .flatMap(client -> {
                    Set<String> identities = updateClient.getIdentities();
                    if (identities == null) {
                        return Single.just(client);
                    } else {
                        return Observable.fromIterable(identities)
                                .flatMapMaybe(identityProviderId -> identityProviderService.findById(identityProviderId))
                                .toList()
                                .flatMap(idp -> Single.just(client));
                    }
                })
                .flatMap(client -> {
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

                    return clientRepository.update(client)
                            .doAfterSuccess(irrelevant -> {
                                // Reload domain to take care about client delete
                                domainService.reload(domain);
                            });
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a client", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a client", ex));
                });
    }

    @Override
    public Single<Irrelevant> delete(String clientId) {
        LOGGER.debug("Delete client {}", clientId);
        return clientRepository.findById(clientId)
                .map(client -> Optional.of(client))
                .defaultIfEmpty(Optional.empty())
                .toSingle()
                .flatMap(optClient -> {
                    if(!optClient.isPresent()) {
                        throw new ClientNotFoundException(clientId);
                    } else {
                        return clientRepository.delete(clientId)
                                    .doAfterSuccess(irrelevant -> {
                                        // Reload domain to take care about client delete
                                        domainService.reload(optClient.get().getDomain());
                                    });
                    }
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete client: {}", clientId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete client: %s", clientId), ex));
                });
    }
}

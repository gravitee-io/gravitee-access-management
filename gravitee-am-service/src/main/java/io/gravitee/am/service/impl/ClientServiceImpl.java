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

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationAdvancedSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewClient;
import io.gravitee.am.service.model.PatchClient;
import io.gravitee.am.service.model.TopClient;
import io.gravitee.am.service.model.TotalClient;
import io.gravitee.am.service.utils.GrantTypeUtils;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
public class ClientServiceImpl implements ClientService {

    public static final String DEFAULT_CLIENT_NAME = "Unknown Client";
    private final Logger LOGGER = LoggerFactory.getLogger(ClientServiceImpl.class);

    @Autowired
    private ApplicationService applicationService;

    @Override
    public Maybe<Client> findById(String id) {
        LOGGER.debug("Find client by ID: {}", id);
        return applicationService.findById(id)
                .map(application -> {
                    Client client = Application.convert(application);
                    // Send an empty array in case of no grant types
                    if (client.getAuthorizedGrantTypes() == null) {
                        client.setAuthorizedGrantTypes(Collections.emptyList());
                    }
                    return client;
                });
    }

    @Override
    public Maybe<Client> findByDomainAndClientId(String domain, String clientId) {
        LOGGER.debug("Find client by domain: {} and client id: {}", domain, clientId);
        return applicationService.findByDomainAndClientId(domain, clientId)
                .map(Application::convert)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find client by domain: {} and client id: {}", domain, clientId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find client by domain: %s and client id: %s", domain, clientId), ex));
                });
    }

    @Override
    public Single<Set<Client>> findByDomain(String domain) {
        LOGGER.debug("Find clients by domain", domain);
        return applicationService.findByDomain(domain)
                .map(pagedApplications -> pagedApplications
                        .stream()
                        .map(Application::convert)
                        .collect(Collectors.toSet()));
    }

    @Override
    public Single<Set<Client>> search(String domain, String query) {
        LOGGER.debug("Search clients for domain {} and with query {}", domain, query);
        return applicationService.search(domain, query, 0, Integer.MAX_VALUE)
                .map(pagedApplications -> pagedApplications.getData()
                        .stream()
                        .map(Application::convert)
                        .collect(Collectors.toSet()));
    }

    @Override
    public Single<Page<Client>> search(String domain, String query, int page, int size) {
        LOGGER.debug("Search clients for domain {} and with query {}", domain, query);
        return applicationService.search(domain, query, page, size)
                .map(pagedApplications -> {
                    Set<Client> clients = pagedApplications.getData()
                            .stream()
                            .map(Application::convert)
                            .collect(Collectors.toSet());
                    return new Page(clients, pagedApplications.getCurrentPage(), pagedApplications.getTotalCount());
                });
    }

    @Override
    public Single<Page<Client>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("Find clients by domain", domain);
        return applicationService.findByDomain(domain, page, size)
                .map(pagedApplications -> {
                    List<Client> clients = pagedApplications.getData()
                            .stream()
                            .map(Application::convert)
                            .collect(Collectors.toList());
                    return new Page<>(clients, pagedApplications.getCurrentPage(), pagedApplications.getTotalCount());
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find clients by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find clients by domain: %s", domain), ex));
                });
    }

    @Override
    public Single<Set<Client>> findAll() {
        LOGGER.debug("Find clients");
        return applicationService.findAll()
                .map(pagedApplications -> pagedApplications
                        .stream()
                        .map(Application::convert)
                        .collect(Collectors.toSet()))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find clients", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find clients", ex));
                });
    }

    @Override
    public Single<Page<Client>> findAll(int page, int size) {
        LOGGER.debug("Find clients");
        return applicationService.findAll(page, size)
                .map(pagedApplications -> {
                    List<Client> clients = pagedApplications.getData()
                            .stream()
                            .map(Application::convert)
                            .collect(Collectors.toList());
                    return new Page<>(clients, pagedApplications.getCurrentPage(), pagedApplications.getTotalCount());
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find clients", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find clients", ex));
                });
    }

    @Override
    public Single<Set<TopClient>> findTopClients() {
        LOGGER.debug("Find top clients");
        return applicationService.findTopApplications()
                .map(topApplications -> {
                    return topApplications
                            .stream()
                            .map(topApplication -> {
                                TopClient topClient = new TopClient();
                                topClient.setClient(Application.convert(topApplication.getApplication()));
                                topClient.setAccessTokens(topApplication.getAccessTokens());
                                return topClient;
                            })
                            .collect(Collectors.toSet());
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find top clients", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find top clients", ex));
                });
    }

    @Override
    public Single<Set<TopClient>> findTopClientsByDomain(String domain) {
        LOGGER.debug("Find top clients by domain: {}", domain);
        return applicationService.findTopApplicationsByDomain(domain)
                .map(topApplications -> {
                    return topApplications
                            .stream()
                            .map(topApplication -> {
                                TopClient topClient = new TopClient();
                                topClient.setClient(Application.convert(topApplication.getApplication()));
                                topClient.setAccessTokens(topApplication.getAccessTokens());
                                return topClient;
                            })
                            .collect(Collectors.toSet());
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find top clients by domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find top clients by domain", ex));
                });
    }

    @Override
    public Single<TotalClient> findTotalClientsByDomain(String domain) {
        LOGGER.debug("Find total clients by domain: {}", domain);
        return applicationService.countByDomain(domain)
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
        return applicationService.count()
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
    public Single<Client> create(String domain, NewClient newClient, User principal) {
        LOGGER.debug("Create a new client {} for domain {}", newClient, domain);
        Client client = new Client();
        client.setDomain(domain);
        client.setClientId(newClient.getClientId());
        client.setClientSecret(newClient.getClientSecret());
        client.setClientName(newClient.getClientName());
        //AM UI first step client creation does not provide field to specify redirect_uris, so better no use code grant by default.
        client.setAuthorizedGrantTypes(Arrays.asList());
        client.setResponseTypes(Arrays.asList());
        return create(client);
    }

    @Override
    public Single<Client> create(Client client) {
        LOGGER.debug("Create a client {} for domain {}", client, client.getDomain());

        if(client.getDomain()==null || client.getDomain().trim().isEmpty()) {
            return Single.error(new InvalidClientMetadataException("No domain set on client"));
        }

        boolean clientIdGenerated = false;

        /* openid response metadata */
        client.setId(RandomString.generate());
        //client_id & client_secret may be already informed if created through UI
        if(client.getClientId()==null) {
            client.setClientId(SecureRandomString.generate());
            clientIdGenerated = true;
        }
        if(client.getClientSecret()==null || client.getClientSecret().trim().isEmpty()) {
            client.setClientSecret(SecureRandomString.generate());
        }
        if(client.getClientName()==null || client.getClientName().trim().isEmpty()) {

            if(clientIdGenerated) {
                client.setClientName(DEFAULT_CLIENT_NAME);
            } else {
                // ClientId has been provided by user, reuse it as clientName.
                client.setClientName(client.getClientId());
            }
        }

        /* GRAVITEE.IO custom fields */
        client.setAccessTokenValiditySeconds(Client.DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS);
        client.setRefreshTokenValiditySeconds(Client.DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS);
        client.setIdTokenValiditySeconds(Client.DEFAULT_ID_TOKEN_VALIDITY_SECONDS);
        client.setEnabled(true);

        client.setCreatedAt(new Date());
        client.setUpdatedAt(client.getCreatedAt());

        return applicationService.create(convert(client))
                .map(Application::convert);
    }

    @Override
    public Single<Client> update(Client client) {
        LOGGER.debug("Update client {} for domain {}", client.getClientId(), client.getDomain());

        if(client.getDomain()==null || client.getDomain().trim().isEmpty()) {
            return Single.error(new InvalidClientMetadataException("No domain set on client"));
        }

        return applicationService.update(convert(client))
                .map(Application::convert);
    }

    @Override
    public Single<Client> patch(String domain, String id, PatchClient patchClient, boolean forceNull, User principal) {
        LOGGER.debug("Patch a client {} for domain {}", id, domain);
        return findById(id)
                .switchIfEmpty(Maybe.error(new ClientNotFoundException(id)))
                .flatMapSingle(toPatch -> this.update(patchClient.patch(toPatch, forceNull)));
    }

    @Override
    public Completable delete(String clientId, User principal) {
        LOGGER.debug("Delete client {}", clientId);
        return applicationService.delete(clientId, principal);
    }

    @Override
    public Single<Client> renewClientSecret(String domain, String id, User principal) {
        LOGGER.debug("Renew client secret for client {} in domain {}", id, domain);
        return applicationService.renewClientSecret(domain, id, principal)
                .map(Application::convert);
    }

    private Application convert(Client client) {
        Application application = new Application();
        application.setId(client.getId());
        application.setDomain(client.getDomain());
        application.setEnabled(client.isEnabled());
        application.setTemplate(client.isTemplate());
        application.setCertificate(client.getCertificate());
        application.setIdentities(client.getIdentities());
        application.setMetadata(client.getMetadata());
        application.setCreatedAt(client.getCreatedAt());
        application.setUpdatedAt(client.getUpdatedAt());
        // set application name
        application.setName(client.getClientName());
        // set application type
        application.setType(getType(client));
        // set application settings
        application.setSettings(getSettings(client));
        return application;
    }

    private ApplicationType getType(Client client) {
        GrantTypeUtils.completeGrantTypeCorrespondance(client);

        // if client has no grant => SERVICE
        // if client has only client_credentials grant_type => SERVICE
        // if client has only implicit => BROWSER
        // else if client type is native => NATIVE
        // else => WEB
        if (client.getAuthorizedGrantTypes() == null || client.getAuthorizedGrantTypes().isEmpty()) {
            return ApplicationType.SERVICE;
        }
        if (client.getAuthorizedGrantTypes().size() == 1) {
            if (client.getAuthorizedGrantTypes().contains(GrantType.CLIENT_CREDENTIALS)) {
                return ApplicationType.SERVICE;
            }
            if (client.getAuthorizedGrantTypes().contains(GrantType.IMPLICIT)) {
                return ApplicationType.BROWSER;
            }
        }
        if (client.getApplicationType() == null || client.getApplicationType().equals(io.gravitee.am.common.oidc.ApplicationType.WEB)) {
            return ApplicationType.WEB;
        }
        if (client.getApplicationType() != null && client.getApplicationType().equals(io.gravitee.am.common.oidc.ApplicationType.NATIVE)) {
            return ApplicationType.NATIVE;
        }
        return ApplicationType.SERVICE;
    }

    private ApplicationSettings getSettings(Client client) {
        // OAuth 2.0/OIDC settings
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setClientId(client.getClientId());
        oAuthSettings.setClientSecret(client.getClientSecret());
        oAuthSettings.setRedirectUris(client.getRedirectUris());
        oAuthSettings.setGrantTypes(client.getAuthorizedGrantTypes());
        oAuthSettings.setResponseTypes(client.getResponseTypes());
        oAuthSettings.setApplicationType(client.getApplicationType());
        oAuthSettings.setContacts(client.getContacts());
        oAuthSettings.setClientName(client.getClientName());
        oAuthSettings.setPolicyUri(client.getPolicyUri());
        oAuthSettings.setClientUri(client.getClientUri());
        oAuthSettings.setPolicyUri(client.getPolicyUri());
        oAuthSettings.setTosUri(client.getTosUri());
        oAuthSettings.setJwksUri(client.getJwksUri());
        oAuthSettings.setJwks(client.getJwks());
        oAuthSettings.setSectorIdentifierUri(client.getSectorIdentifierUri());
        oAuthSettings.setSubjectType(client.getSubjectType());
        oAuthSettings.setIdTokenSignedResponseAlg(client.getIdTokenSignedResponseAlg());
        oAuthSettings.setIdTokenEncryptedResponseAlg(client.getIdTokenEncryptedResponseAlg());
        oAuthSettings.setIdTokenEncryptedResponseEnc(client.getIdTokenEncryptedResponseEnc());
        oAuthSettings.setUserinfoSignedResponseAlg(client.getUserinfoSignedResponseAlg());
        oAuthSettings.setUserinfoEncryptedResponseAlg(client.getUserinfoEncryptedResponseAlg());
        oAuthSettings.setUserinfoEncryptedResponseEnc(client.getUserinfoEncryptedResponseEnc());
        oAuthSettings.setRequestObjectSigningAlg(client.getRequestObjectSigningAlg());
        oAuthSettings.setRequestObjectEncryptionAlg(client.getRequestObjectEncryptionAlg());
        oAuthSettings.setRequestObjectEncryptionEnc(client.getRequestObjectEncryptionEnc());
        oAuthSettings.setTokenEndpointAuthMethod(client.getTokenEndpointAuthMethod());
        oAuthSettings.setTokenEndpointAuthSigningAlg(client.getTokenEndpointAuthSigningAlg());
        oAuthSettings.setDefaultMaxAge(client.getDefaultMaxAge());
        oAuthSettings.setRequireAuthTime(client.getRequireAuthTime());
        oAuthSettings.setDefaultACRvalues(client.getDefaultACRvalues());
        oAuthSettings.setInitiateLoginUri(client.getInitiateLoginUri());
        oAuthSettings.setRequestUris(client.getRequestUris());
        oAuthSettings.setScopes(client.getScopes());
        oAuthSettings.setSoftwareId(client.getSoftwareId());
        oAuthSettings.setSoftwareVersion(client.getSoftwareVersion());
        oAuthSettings.setSoftwareStatement(client.getSoftwareStatement());
        oAuthSettings.setRegistrationAccessToken(client.getRegistrationAccessToken());
        oAuthSettings.setRegistrationClientUri(client.getRegistrationClientUri());
        oAuthSettings.setClientIdIssuedAt(client.getClientIdIssuedAt());
        oAuthSettings.setClientSecretExpiresAt(client.getClientSecretExpiresAt());
        oAuthSettings.setAccessTokenValiditySeconds(client.getAccessTokenValiditySeconds());
        oAuthSettings.setRefreshTokenValiditySeconds(client.getRefreshTokenValiditySeconds());
        oAuthSettings.setIdTokenValiditySeconds(client.getIdTokenValiditySeconds());
        oAuthSettings.setEnhanceScopesWithUserPermissions(client.isEnhanceScopesWithUserPermissions());
        oAuthSettings.setScopeApprovals(client.getScopeApprovals());
        oAuthSettings.setTokenCustomClaims(client.getTokenCustomClaims());
        oAuthSettings.setAuthorizationSignedResponseAlg(client.getAuthorizationSignedResponseAlg());
        oAuthSettings.setAuthorizationEncryptedResponseAlg(client.getAuthorizationEncryptedResponseAlg());
        oAuthSettings.setAuthorizationEncryptedResponseEnc(client.getAuthorizationEncryptedResponseEnc());

        // advanced settings
        ApplicationAdvancedSettings advancedSettings = new ApplicationAdvancedSettings();
        advancedSettings.setSkipConsent(client.getAutoApproveScopes() != null && client.getAutoApproveScopes().contains("true"));

        ApplicationSettings applicationSettings = new ApplicationSettings();
        applicationSettings.setOauth(oAuthSettings);
        applicationSettings.setAccount(client.getAccountSettings());
        applicationSettings.setAdvanced(advancedSettings);

        return applicationSettings;
    }
}

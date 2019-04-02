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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.oauth2.exception.OAuth2Exception;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Action;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.common.event.Type;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewClient;
import io.gravitee.am.service.model.PatchClient;
import io.gravitee.am.service.model.TopClient;
import io.gravitee.am.service.model.TotalClient;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ClientAuditBuilder;
import io.gravitee.am.service.utils.GrantTypeUtils;
import io.gravitee.am.service.utils.UriBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
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

    private final Logger LOGGER = LoggerFactory.getLogger(ClientServiceImpl.class);

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private FormService formService;

    @Autowired
    private AuditService auditService;

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
    public Single<Set<Client>> search(String domain, String query) {
        LOGGER.debug("Search clients for domain {} and with query {}", domain, query);
        return clientRepository.search(domain, query)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find clients for domain {} and query {}", domain, query, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find clients for domain %s and query %s", domain, query), ex));
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
    public Single<Set<Client>> findByDomainAndExtensionGrant(String domain, String extensionGrant) {
        LOGGER.debug("Find clients by domain {} and extension grant : {}", domain, extensionGrant);
        return clientRepository.findByDomainAndExtensionGrant(domain, extensionGrant)
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
                .flatMapSingle(client -> accessTokenRepository.countByClientId(client.getId())
                        .map(oAuth2AccessTokens -> {
                            TopClient topClient = new TopClient();
                            topClient.setClient(client);
                            topClient.setAccessTokens(oAuth2AccessTokens);
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
                .flatMapSingle(client -> accessTokenRepository.countByClientId(client.getId())
                        .map(oAuth2AccessTokens -> {
                            TopClient topClient = new TopClient();
                            topClient.setClient(client);
                            topClient.setAccessTokens(oAuth2AccessTokens);
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
    public Single<Client> create(String domain, NewClient newClient, User principal) {
        LOGGER.debug("Create a new client {} for domain {}", newClient, domain);
        return clientRepository.findByClientIdAndDomain(newClient.getClientId(), domain)
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (!isEmpty) {
                        return Single.error(new ClientAlreadyExistsException(newClient.getClientId(), domain));
                    }

                    Client client = new Client();
                    client.setClientId(newClient.getClientId());
                    client.setClientSecret(newClient.getClientSecret());
                    client.setClientName(newClient.getClientName());
                    client.setDomain(domain);
                    return Single.just(client);
                })
                .flatMap(client -> this.create(client))
                .onErrorResumeNext(this::handleError)
                .doOnSuccess(client -> auditService.report(AuditBuilder.builder(ClientAuditBuilder.class).principal(principal).type(EventType.CLIENT_CREATED).client(client)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ClientAuditBuilder.class).principal(principal).type(EventType.CLIENT_CREATED).throwable(throwable)));
    }

    @Override
    public Single<Client> create(Client client) {
        LOGGER.debug("Create a client {} for domain {}", client, client.getDomain());

        if(client.getDomain()==null || client.getDomain().trim().isEmpty()) {
            return Single.error(new InvalidClientMetadataException("No domain set on client"));
        }

        /* openid response metadata */
        client.setId(RandomString.generate());
        //client_id & client_secret may be already informed if created through UI
        if(client.getClientId()==null) {
            client.setClientId(SecureRandomString.generate());
        }
        if(client.getClientSecret()==null || client.getClientSecret().trim().isEmpty()) {
            client.setClientSecret(SecureRandomString.generate());
        }
        if(client.getClientName()==null || client.getClientName().trim().isEmpty()) {
            client.setClientName("Unknown Client");
        }

        /* GRAVITEE.IO custom fields */
        client.setAccessTokenValiditySeconds(Client.DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS);
        client.setRefreshTokenValiditySeconds(Client.DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS);
        client.setIdTokenValiditySeconds(Client.DEFAULT_ID_TOKEN_VALIDITY_SECONDS);
        client.setEnabled(true);

        client.setCreatedAt(new Date());
        client.setUpdatedAt(client.getCreatedAt());

        return this.validateClientMetadata(client.getDomain(), client)
                .flatMap(clientRepository::create)
                .flatMap(justCreatedClient -> {
                    // Reload domain to take care about client creation
                    Event event = new Event(Type.CLIENT, new Payload(justCreatedClient.getId(), justCreatedClient.getDomain(), Action.CREATE));
                    return domainService.reload(client.getDomain(), event).flatMap(domain1 -> Single.just(justCreatedClient));
                })
                .onErrorResumeNext(this::handleError);
        }

    @Override
    public Single<Client> update(Client client) {
        LOGGER.debug("Update client_id {} for domain {}", client.getClientId(), client.getDomain());

        if(client.getDomain()==null || client.getDomain().trim().isEmpty()) {
            return Single.error(new InvalidClientMetadataException("No domain set on client"));
        }

        return clientRepository.findById(client.getId())
                .switchIfEmpty(Maybe.error(new ClientNotFoundException(client.getId())))
                .flatMapSingle(found -> Single.just(client))
                .flatMap(toUpdate -> this.validateClientMetadata(toUpdate.getDomain(), toUpdate))
                .flatMap(toUpdate -> this.updateClientAndReloadDomain(toUpdate.getDomain(), toUpdate))
                .onErrorResumeNext(this::handleError);
    }

    @Override
    public Single<Client> patch(String domain, String id, PatchClient patchClient, boolean forceNull, User principal) {
        LOGGER.debug("Patch a client {} for domain {}", id, domain);
        return clientRepository.findById(id)
                .switchIfEmpty(Maybe.error(new ClientNotFoundException(id)))
                .flatMapSingle(client -> {
                    //Refresh with existing identity providers.
                    Optional<Set<String>> identities = patchClient.getIdentities();
                    if (identities == null || !identities.isPresent()) {
                        return Single.just(client);
                    } else {
                        return Observable.fromIterable(identities.get())
                                .flatMapMaybe(identityProviderId -> identityProviderService.findById(identityProviderId))
                                .toList()
                                .flatMap(idp -> Single.just(client));
                    }
                })
                .flatMap(toPatch -> Single.just(patchClient.patch(toPatch, forceNull))
                        .flatMap(client -> this.validateClientMetadata(domain, client))
                        .flatMap(client -> this.updateClientAndReloadDomain(domain, client))
                        .doOnSuccess(client -> auditService.report(AuditBuilder.builder(ClientAuditBuilder.class).principal(principal).type(EventType.CLIENT_UPDATED).oldValue(toPatch).client(client)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(ClientAuditBuilder.class).principal(principal).type(EventType.CLIENT_UPDATED).throwable(throwable))))
                .onErrorResumeNext(this::handleError);
    }

    @Override
    public Completable delete(String clientId, User principal) {
        LOGGER.debug("Delete client {}", clientId);
        return clientRepository.findById(clientId)
                .switchIfEmpty(Maybe.error(new ClientNotFoundException(clientId)))
                .flatMapCompletable(client -> {
                    // Reload domain to take care about delete client
                    Event event = new Event(Type.CLIENT, new Payload(client.getId(), client.getDomain(), Action.DELETE));
                    return clientRepository.delete(clientId)
                            .andThen(domainService.reload(client.getDomain(), event).toCompletable())
                            // delete email templates
                            .andThen(emailTemplateService.findByDomainAndClient(client.getDomain(), client.getId())
                                    .flatMapCompletable(emails -> {
                                        List<Completable> deleteEmailsCompletable = emails.stream().map(e -> emailTemplateService.delete(e.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteEmailsCompletable);
                                    })
                            )
                            // delete form templates
                            .andThen(formService.findByDomainAndClient(client.getDomain(), client.getId())
                                    .flatMapCompletable(forms -> {
                                        List<Completable> deleteFormsCompletable = forms.stream().map(f -> formService.delete(f.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteFormsCompletable);
                                    })
                            )
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(ClientAuditBuilder.class).principal(principal).type(EventType.CLIENT_DELETED).client(client)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ClientAuditBuilder.class).principal(principal).type(EventType.CLIENT_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete client: {}", clientId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete client: %s", clientId), ex));
                });
    }

    @Override
    public Single<Client> renewClientSecret(String domain, String id, User principal) {
        LOGGER.debug("Renew client secret for client {} in domain {}", id, domain);
        return clientRepository.findById(id)
                .switchIfEmpty(Maybe.error(new ClientNotFoundException(id)))
                .flatMapSingle(client -> {
                    // update client secret
                    client.setClientSecret(SecureRandomString.generate());

                    // update client and reload domain
                    return updateClientAndReloadDomain(domain, client);
                })
                .onErrorResumeNext(this::handleError)
                .doOnSuccess(client -> auditService.report(AuditBuilder.builder(ClientAuditBuilder.class).principal(principal).type(EventType.CLIENT_SECRET_RENEWED).client(client)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ClientAuditBuilder.class).principal(principal).type(EventType.CLIENT_SECRET_RENEWED).throwable(throwable)));
    }

    /**
     * <pre>
     * This function will return an error if :
     * We try to enable Dynamic Client Registration on client side while it is not enabled on domain.
     * The redirect_uris do not respect domain conditions (localhost, scheme and wildcard)
     * </pre>
     * @param domainId domain
     * @param client client to check
     * @return a client only if every conditions are respected.
     */
    private Single<Client> validateClientMetadata(String domainId, Client client) {

        return domainService.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(domain -> {
                    //check redirect_uri
                    if (client.getRedirectUris() != null) {
                        for (String redirectUri : client.getRedirectUris()) {

                            URI uri = UriBuilder.fromURIString(redirectUri).build();

                            if (!domain.isRedirectUriLocalhostAllowed() && UriBuilder.isLocalhost(uri.getHost())) {
                                return Single.error(new InvalidRedirectUriException("localhost is forbidden"));
                            }
                            //check http scheme
                            if (!domain.isRedirectUriUnsecuredHttpSchemeAllowed() && uri.getScheme().equalsIgnoreCase("http")) {
                                return Single.error(new InvalidRedirectUriException("Unsecured http scheme is forbidden"));
                            }
                            //check wildcard
                            if (!domain.isRedirectUriWildcardAllowed() && uri.getPath().contains("*")) {
                                return Single.error(new InvalidRedirectUriException("Wildcard are forbidden"));
                            }
                        }
                    }

                    // check scopes and scope approvals
                    return scopeService.validateScope(domainId, client.getScopes())
                            .map(isValid -> {
                                // scopes are valid, let's check scope approvals
                                if (isValid && client.getScopeApprovals() != null) {
                                    Map<String, Integer> scopeApprovals = client.getScopeApprovals()
                                            .entrySet()
                                            .stream()
                                            .filter(entry -> client.getScopes() != null && client.getScopes().contains(entry.getKey()))
                                            .collect(Collectors.toMap(
                                                    entry -> entry.getKey(),
                                                    entry -> entry.getValue()));
                                    client.setScopeApprovals(scopeApprovals);
                                }
                                return isValid;
                            });
                })
                .flatMap(isValid -> {
                    if (!isValid) {
                        //last boolean come from scopes validation...
                        return Single.error(new InvalidClientMetadataException("non valid scopes"));
                    }
                    //ensure correspondance between response & grant types.
                    GrantTypeUtils.completeGrantTypeCorrespondance(client);

                    return Single.just(client);
                });
    }

    private Single<Client> updateClientAndReloadDomain(String domain, Client client) {
        client.setUpdatedAt(new Date());
        return clientRepository.update(client)
                .flatMap(updatedClient -> {
                    // Reload domain to take care about client update
                    Event event = new Event(Type.CLIENT, new Payload(updatedClient.getId(), client.getDomain(), Action.UPDATE));
                    return domainService.reload(domain, event).flatMap(domain1 -> Single.just(updatedClient));
                });
    }

    private Single<Client> handleError(Throwable ex) {
        if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
            return Single.error(ex);
        }

        LOGGER.error("An error occurs while trying to create or update a client", ex);
        return Single.error(new TechnicalManagementException("An error occurs while trying to create or update a client", ex));
    }
}

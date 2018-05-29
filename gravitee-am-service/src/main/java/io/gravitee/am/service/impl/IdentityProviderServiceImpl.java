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

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderWithClientsException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.gravitee.common.utils.UUID;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class IdentityProviderServiceImpl implements IdentityProviderService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(IdentityProviderServiceImpl.class);

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    @Autowired
    private ClientService clientService;

    @Autowired
    private DomainService domainService;

    @Override
    public Maybe<IdentityProvider> findById(String id) {
        LOGGER.debug("Find identity provider by ID: {}", id);
        return identityProviderRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an identity provider using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an identity provider using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<List<IdentityProvider>> findByDomain(String domain) {
        LOGGER.debug("Find identity providers by domain: {}", domain);
        return identityProviderRepository.findByDomain(domain)
                .map(identityProviders -> (List<IdentityProvider>) new ArrayList<>(identityProviders))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find identity providers by domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find identity providers by domain", ex));
                });
    }

    @Override
    public Single<IdentityProvider> create(String domain, NewIdentityProvider newIdentityProvider) {
        LOGGER.debug("Create a new identity provider {} for domain {}", newIdentityProvider, domain);

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId(UUID.toString(UUID.random()));
        identityProvider.setDomain(domain);
        identityProvider.setName(newIdentityProvider.getName());
        identityProvider.setType(newIdentityProvider.getType());
        identityProvider.setConfiguration(newIdentityProvider.getConfiguration());
        identityProvider.setExternal(newIdentityProvider.isExternal());
        identityProvider.setCreatedAt(new Date());
        identityProvider.setUpdatedAt(identityProvider.getCreatedAt());

        return identityProviderRepository.create(identityProvider)
                .flatMap(identityProvider1 -> {
                    // Reload domain to take care about identity provider creation
                    return domainService.reload(domain).flatMap(domain1 -> Single.just(identityProvider1));
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to create an identity provider", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create an identity provider", ex));
                });
    }

    @Override
    public Single<IdentityProvider> update(String domain, String id, UpdateIdentityProvider updateIdentityProvider) {
        LOGGER.debug("Update an identity provider {} for domain {}", id, domain);

        return identityProviderRepository.findById(id)
                .switchIfEmpty(Maybe.error(new IdentityProviderNotFoundException(id)))
                .flatMapSingle(identityProvider -> {
                    identityProvider.setName(updateIdentityProvider.getName());
                    identityProvider.setConfiguration(updateIdentityProvider.getConfiguration());
                    identityProvider.setMappers(updateIdentityProvider.getMappers());
                    identityProvider.setRoleMapper(updateIdentityProvider.getRoleMapper());
                    identityProvider.setUpdatedAt(new Date());

                    return identityProviderRepository.update(identityProvider)
                            .flatMap(identityProvider1 -> {
                                // Reload domain to take care about identity provider update
                                return domainService.reload(domain).flatMap(domain1 -> Single.just(identityProvider1));
                            });
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update an identity provider", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update an identity provider", ex));
                });
    }

    @Override
    public Completable delete(String identityProviderId) {
        LOGGER.debug("Delete identity provider {}", identityProviderId);

        return identityProviderRepository.findById(identityProviderId)
                .switchIfEmpty(Maybe.error(new IdentityProviderNotFoundException(identityProviderId)))
                .flatMapSingle(identityProvider -> clientService.findByIdentityProvider(identityProviderId)
                        .flatMap(clients -> {
                            if (clients.size() > 0) {
                                throw new IdentityProviderWithClientsException();
                            }
                            return Single.just(clients);
                        }))
                .flatMapCompletable(irrelevant -> identityProviderRepository.delete(identityProviderId))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete identity provider: {}", identityProviderId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete identity provider: %s", identityProviderId), ex));
                });
    }
}

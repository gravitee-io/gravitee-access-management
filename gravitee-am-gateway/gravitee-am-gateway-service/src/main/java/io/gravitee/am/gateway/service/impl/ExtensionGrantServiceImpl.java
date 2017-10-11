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
import io.gravitee.am.gateway.service.DomainService;
import io.gravitee.am.gateway.service.ExtensionGrantService;
import io.gravitee.am.gateway.service.exception.TechnicalManagementException;
import io.gravitee.am.gateway.service.exception.ExtensionGrantAlreadyExistsException;
import io.gravitee.am.gateway.service.exception.ExtensionGrantNotFoundException;
import io.gravitee.am.gateway.service.exception.ExtensionGrantWithClientsException;
import io.gravitee.am.gateway.service.model.NewExtensionGrant;
import io.gravitee.am.gateway.service.model.UpdateExtensionGrant;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.common.utils.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ExtensionGrantServiceImpl implements ExtensionGrantService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(ExtensionGrantServiceImpl.class);

    @Autowired
    private ExtensionGrantRepository extensionGrantRepository;

    @Autowired
    private ClientService clientService;

    @Autowired
    private DomainService domainService;

    @Override
    public ExtensionGrant findById(String id) {
        try {
            LOGGER.debug("Find extension grant by ID: {}", id);
            Optional<ExtensionGrant> tokenGranterOpt = extensionGrantRepository.findById(id);

            if (!tokenGranterOpt.isPresent()) {
                throw new ExtensionGrantNotFoundException(id);
            }

            return tokenGranterOpt.get();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find an extension grant using its ID: {}", id, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find an extension grant using its ID: %s", id), ex);
        }
    }

    @Override
    public List<ExtensionGrant> findByDomain(String domain) {
        try {
            LOGGER.debug("Find extension grants by domain: {}", domain);
            return new ArrayList<>(extensionGrantRepository.findByDomain(domain));
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find extension grants by domain", ex);
            throw new TechnicalManagementException("An error occurs while trying to find extension grants by domain", ex);
        }
    }

    @Override
    public ExtensionGrant create(String domain, NewExtensionGrant newExtensionGrant) {
        try {
            LOGGER.debug("Create a new extension grant {} for domain {}", newExtensionGrant, domain);

            Optional<ExtensionGrant> existingTokenGranter = extensionGrantRepository.findByDomainAndGrantType(domain, newExtensionGrant.getGrantType());
            if (existingTokenGranter.isPresent()) {
                throw new ExtensionGrantAlreadyExistsException(newExtensionGrant.getGrantType());
            }

            String certificateId = UUID.toString(UUID.random());
            ExtensionGrant extensionGrant = new ExtensionGrant();
            extensionGrant.setId(certificateId);
            extensionGrant.setDomain(domain);
            extensionGrant.setName(newExtensionGrant.getName());
            extensionGrant.setGrantType(newExtensionGrant.getGrantType());
            extensionGrant.setIdentityProvider(newExtensionGrant.getIdentityProvider());
            extensionGrant.setType(newExtensionGrant.getType());
            extensionGrant.setConfiguration(newExtensionGrant.getConfiguration());
            extensionGrant.setCreatedAt(new Date());
            extensionGrant.setUpdatedAt(extensionGrant.getCreatedAt());

            ExtensionGrant extensionGrant1 = extensionGrantRepository.create(extensionGrant);

            // Reload domain to take care about extension grant update
            domainService.reload(domain);

            return extensionGrant1;
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to create a extension grant", ex);
            throw new TechnicalManagementException("An error occurs while trying to create a extension grant", ex);
        }
    }

    @Override
    public ExtensionGrant update(String domain, String id, UpdateExtensionGrant updateExtensionGrant) {
        try {
            LOGGER.debug("Update a extension grant {} for domain {}", id, domain);

            Optional<ExtensionGrant> tokenGranterOpt = extensionGrantRepository.findById(id);
            if (!tokenGranterOpt.isPresent()) {
                throw new ExtensionGrantNotFoundException(id);
            }

            Optional<ExtensionGrant> existingTokenGranter = extensionGrantRepository.findByDomainAndGrantType(domain, updateExtensionGrant.getGrantType());
            if (existingTokenGranter.isPresent() && !existingTokenGranter.get().getId().equals(id)) {
                throw new ExtensionGrantAlreadyExistsException(updateExtensionGrant.getGrantType());
            }

            ExtensionGrant oldExtensionGrant = tokenGranterOpt.get();
            oldExtensionGrant.setName(updateExtensionGrant.getName());
            oldExtensionGrant.setGrantType(updateExtensionGrant.getGrantType());
            oldExtensionGrant.setIdentityProvider(updateExtensionGrant.getIdentityProvider());
            oldExtensionGrant.setConfiguration(updateExtensionGrant.getConfiguration());
            oldExtensionGrant.setUpdatedAt(new Date());

            ExtensionGrant extensionGrant = extensionGrantRepository.update(oldExtensionGrant);

            // Reload domain to take care about extension grant update
            domainService.reload(domain);

            return extensionGrant;
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to update a extension grant", ex);
            throw new TechnicalManagementException("An error occurs while trying to update a extension grant", ex);
        }
    }

    @Override
    public void delete(String domain, String extensionGrantId) {
        try {
            LOGGER.debug("Delete extension grant {}", extensionGrantId);

            Optional<ExtensionGrant> optTokenGranter = extensionGrantRepository.findById(extensionGrantId);
            if (! optTokenGranter.isPresent()) {
                throw new ExtensionGrantNotFoundException(extensionGrantId);
            }

            int clients = clientService.findByExtensionGrant(optTokenGranter.get().getGrantType()).size();
            if (clients > 0) {
                throw new ExtensionGrantWithClientsException();
            }

            extensionGrantRepository.delete(extensionGrantId);

            // Reload domain to take care about extension grant update
            domainService.reload(domain);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to extension grant: {}", extensionGrantId, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to delete extension grant: %s", extensionGrantId), ex);
        }
    }

}

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

import io.gravitee.am.gateway.service.IdentityProviderService;
import io.gravitee.am.gateway.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.gateway.service.exception.TechnicalManagementException;
import io.gravitee.am.gateway.service.model.NewIdentityProvider;
import io.gravitee.am.gateway.service.model.UpdateIdentityProvider;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
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
 * @author David BRASSELY (david.brassely at graviteesource.com)
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

    @Override
    public IdentityProvider findById(String id) {
        try {
            LOGGER.debug("Find identity provider by ID: {}", id);
            Optional<IdentityProvider> identityProviderOpt = identityProviderRepository.findById(id);

            if (!identityProviderOpt.isPresent()) {
                throw new IdentityProviderNotFoundException(id);
            }

            return identityProviderOpt.get();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find an identity provider using its ID: {}", id, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find an identity provider using its ID: %s", id), ex);
        }
    }

    @Override
    public List<IdentityProvider> findByClient(String id) {
        try {
            LOGGER.debug("Find identity providers by client: {}", id);
            return new ArrayList<>(identityProviderRepository.findByDomain(id));
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find identity providers by client", ex);
            throw new TechnicalManagementException("An error occurs while trying to find identity providers by client", ex);
        }
    }

    @Override
    public List<IdentityProvider> findByDomain(String domain) {
        try {
            LOGGER.debug("Find identity providers by domain: {}", domain);
            return new ArrayList<>(identityProviderRepository.findByDomain(domain));
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find identity providers by domain", ex);
            throw new TechnicalManagementException("An error occurs while trying to find identity providers by domain", ex);
        }
    }

    @Override
    public IdentityProvider create(String domain, NewIdentityProvider newIdentityProvider) {
        try {
            LOGGER.debug("Create a new identity provider {} for domain {}", newIdentityProvider, domain);

            IdentityProvider identityProvider = new IdentityProvider();
            identityProvider.setId(UUID.toString(UUID.random()));
            identityProvider.setDomain(domain);
            identityProvider.setName(newIdentityProvider.getName());
            identityProvider.setType(newIdentityProvider.getType());
            identityProvider.setConfiguration(newIdentityProvider.getConfiguration());
            identityProvider.setCreatedAt(new Date());
            identityProvider.setUpdatedAt(identityProvider.getCreatedAt());

            return identityProviderRepository.create(identityProvider);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to create an identity provider", ex);
            throw new TechnicalManagementException("An error occurs while trying to create an identity provider", ex);
        }
    }

    @Override
    public IdentityProvider update(String domain, String id, UpdateIdentityProvider updateIdentityProvider) {
        try {
            LOGGER.debug("Update an identity provider {} for domain {}", id, domain);

            Optional<IdentityProvider> identityProviderOpt = identityProviderRepository.findById(id);
            if (!identityProviderOpt.isPresent()) {
                throw new IdentityProviderNotFoundException(id);
            }

            IdentityProvider identityProvider = identityProviderOpt.get();
            identityProvider.setName(updateIdentityProvider.getName());
            identityProvider.setConfiguration(updateIdentityProvider.getConfiguration());
            identityProvider.setUpdatedAt(new Date());

            return identityProviderRepository.update(identityProvider);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to update an identity provider", ex);
            throw new TechnicalManagementException("An error occurs while trying to update an identity provider", ex);
        }
    }
}

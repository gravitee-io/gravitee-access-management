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

import io.gravitee.am.gateway.service.DomainService;
import io.gravitee.am.gateway.service.exception.DomainAlreadyExistsException;
import io.gravitee.am.gateway.service.exception.DomainNotFoundException;
import io.gravitee.am.gateway.service.exception.TechnicalManagementException;
import io.gravitee.am.gateway.service.model.NewDomain;
import io.gravitee.am.gateway.service.model.UpdateDomain;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.common.utils.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DomainServiceImpl implements DomainService {

    private final Logger LOGGER = LoggerFactory.getLogger(DomainServiceImpl.class);

    @Autowired
    private DomainRepository domainRepository;

    @Override
    public Domain findById(String id) {
        try {
            LOGGER.debug("Find domain by ID: {}", id);
            Optional<Domain> domainOpt = domainRepository.findById(id);

            if (!domainOpt.isPresent()) {
                throw new DomainNotFoundException(id);
            }

            return domainOpt.get();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find a domain using its ID: {}", id, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find a domain using its ID: %s", id), ex);
        }
    }

    @Override
    public Set<Domain> findAll() {
        try {
            LOGGER.debug("Find all domains");
            return domainRepository.findAll();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find all domains", ex);
            throw new TechnicalManagementException("An error occurs while trying to find all domains", ex);
        }
    }

    @Override
    public Domain create(NewDomain newDomain) {
        try {
            LOGGER.debug("Create a new domain: {}", newDomain);
            String id = generateContextPath(newDomain.getName());

            Optional<Domain> domainOpt = domainRepository.findById(id);
            if (domainOpt.isPresent()) {
                throw new DomainAlreadyExistsException(newDomain.getName());
            }

            Domain domain = new Domain();
            domain.setId(id);
            domain.setPath(id);
            domain.setName(newDomain.getName());
            domain.setDescription(newDomain.getDescription());
            domain.setEnabled(false);
            domain.setCreatedAt(new Date());
            domain.setUpdatedAt(domain.getCreatedAt());

            return domainRepository.create(domain);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to create a domain", ex);
            throw new TechnicalManagementException("An error occurs while trying to create a domain", ex);
        }
    }

    @Override
    public Domain update(String domainId, UpdateDomain updateDomain) {
        try {
            LOGGER.debug("Update an existing domain: {}", updateDomain);

            Optional<Domain> domainOpt = domainRepository.findById(domainId);
            if (!domainOpt.isPresent()) {
                throw new DomainNotFoundException(domainId);
            }

            Domain oldDomain = domainOpt.get();

            Domain domain = new Domain();
            domain.setId(domainId);
            domain.setPath(updateDomain.getName());
            domain.setName(updateDomain.getName());
            domain.setDescription(updateDomain.getDescription());
            domain.setEnabled(updateDomain.isEnabled());
            domain.setCreatedAt(oldDomain.getCreatedAt());
            domain.setUpdatedAt(new Date());

            return domainRepository.update(domain);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to update a domain", ex);
            throw new TechnicalManagementException("An error occurs while trying to update a domain", ex);
        }
    }

    @Override
    public void delete(String domain) {
        try {
            LOGGER.debug("Delete security domain {}", domain);

            Optional<Domain> optApi = domainRepository.findById(domain);
            if (! optApi.isPresent()) {
                throw new DomainNotFoundException(domain);
            }

            domainRepository.delete(domain);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to delete security domain {}", domain, ex);
            throw new TechnicalManagementException("An error occurs while trying to delete security domain " + domain, ex);
        }
    }

    private String generateContextPath(String domainName) {
        String nfdNormalizedString = Normalizer.normalize(domainName, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        domainName = pattern.matcher(nfdNormalizedString).replaceAll("");
        return domainName.toLowerCase().trim().replaceAll("\\s{1,}", "-");
    }
}

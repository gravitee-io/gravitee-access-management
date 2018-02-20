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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginForm;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.DomainAlreadyExistsException;
import io.gravitee.am.service.exception.DomainDeleteMasterException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.UpdateDomain;
import io.gravitee.am.service.model.UpdateLoginForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Collection;
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

    @Autowired
    private ClientService clientService;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserService userService;

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
    public Set<Domain> findByIdIn(Collection<String> ids) {
        try {
            LOGGER.debug("Find domains by id in {}", ids);
            return domainRepository.findByIdIn(ids);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find domains by id in {}", ids, ex);
            throw new TechnicalManagementException("An error occurs while trying to find domains by id in", ex);
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
            domain.setPath(updateDomain.getPath());
            domain.setName(updateDomain.getName());
            domain.setDescription(updateDomain.getDescription());
            domain.setEnabled(updateDomain.isEnabled());
            // master flag is set programmatically (keep old value)
            domain.setMaster(oldDomain.isMaster());
            domain.setCreatedAt(oldDomain.getCreatedAt());
            domain.setUpdatedAt(new Date());
            domain.setLoginForm(oldDomain.getLoginForm());
            return domainRepository.update(domain);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to update a domain", ex);
            throw new TechnicalManagementException("An error occurs while trying to update a domain", ex);
        }
    }

    @Override
    public Domain reload(String domainId) {
        try {
            LOGGER.debug("Reload a domain: {}", domainId);

            Optional<Domain> domainOpt = domainRepository.findById(domainId);
            if (!domainOpt.isPresent()) {
                throw new DomainNotFoundException(domainId);
            }

            Domain oldDomain = domainOpt.get();

            Domain domain = new Domain();
            domain.setId(domainId);
            domain.setPath(oldDomain.getPath());
            domain.setName(oldDomain.getName());
            domain.setDescription(oldDomain.getDescription());
            domain.setEnabled(oldDomain.isEnabled());
            domain.setMaster(oldDomain.isMaster());
            domain.setCreatedAt(oldDomain.getCreatedAt());
            domain.setUpdatedAt(new Date());
            domain.setLoginForm(oldDomain.getLoginForm());
            return domainRepository.update(domain);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to reload a domain", ex);
            throw new TechnicalManagementException("An error occurs while trying to reload a domain", ex);
        }
    }

    @Override
    public Domain setMasterDomain(String domainId, boolean isMaster) {
        try {
            LOGGER.debug("Set master flag for domain: {}", domainId);

            Optional<Domain> domainOpt = domainRepository.findById(domainId);
            if (!domainOpt.isPresent()) {
                throw new DomainNotFoundException(domainId);
            }

            Domain oldDomain = domainOpt.get();

            Domain domain = new Domain();
            domain.setId(domainId);
            domain.setPath(oldDomain.getPath());
            domain.setName(oldDomain.getName());
            domain.setDescription(oldDomain.getDescription());
            domain.setEnabled(oldDomain.isEnabled());
            domain.setMaster(isMaster);
            domain.setCreatedAt(oldDomain.getCreatedAt());
            domain.setUpdatedAt(new Date());
            domain.setLoginForm(oldDomain.getLoginForm());
            return domainRepository.update(domain);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to set master flag for domain {}", domainId, ex);
            throw new TechnicalManagementException("An error occurs while trying to set master flag for a domain", ex);
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

            if (optApi.get().isMaster()) {
                throw new DomainDeleteMasterException(domain);
            }

            // delete clients
            clientService.findByDomain(domain).forEach(c -> clientService.delete(c.getId()));

            // delete certificates
            certificateService.findByDomain(domain).forEach(c -> certificateService.delete(c.getId()));

            // delete identity providers
            identityProviderService.findByDomain(domain).forEach(i -> identityProviderService.delete(i.getId()));

            // delete roles
            roleService.findByDomain(domain).forEach(r -> roleService.delete(r.getId()));

            // delete users
            userService.findByDomain(domain).forEach(u -> userService.delete(u.getId()));

            domainRepository.delete(domain);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to delete security domain {}", domain, ex);
            throw new TechnicalManagementException("An error occurs while trying to delete security domain " + domain, ex);
        }
    }

    @Override
    public LoginForm updateLoginForm(String domainId, UpdateLoginForm loginForm) {
        try {
            LOGGER.debug("Update login form of an existing domain: {}", domainId);

            Optional<Domain> domainOpt = domainRepository.findById(domainId);
            if (!domainOpt.isPresent()) {
                throw new DomainNotFoundException(domainId);
            }

            LoginForm form = new LoginForm();
            form.setEnabled(loginForm.isEnabled());
            form.setContent(loginForm.getContent());
            form.setAssets(loginForm.getAssets());

            Domain domain = domainOpt.get();
            domain.setLoginForm(form);
            domain.setUpdatedAt(new Date());
            domainRepository.update(domain);

            return form;
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to update login form domain", ex);
            throw new TechnicalManagementException("An error occurs while trying to update a domain", ex);
        }
    }

    @Override
    public void deleteLoginForm(String domainId) {
        try {
            LOGGER.debug("Delete login form of an existing domain: {}", domainId);

            Optional<Domain> domainOpt = domainRepository.findById(domainId);
            if (!domainOpt.isPresent()) {
                throw new DomainNotFoundException(domainId);
            }

            Domain domain = domainOpt.get();
            domain.setLoginForm(null);
            domain.setUpdatedAt(new Date());
            domainRepository.update(domain);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to update login form domain", ex);
            throw new TechnicalManagementException("An error occurs while trying to update a domain", ex);
        }
    }

    private String generateContextPath(String domainName) {
        String nfdNormalizedString = Normalizer.normalize(domainName, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        domainName = pattern.matcher(nfdNormalizedString).replaceAll("");
        return domainName.toLowerCase().trim().replaceAll("\\s{1,}", "-");
    }
}

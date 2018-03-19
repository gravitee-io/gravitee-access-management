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
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.model.login.LoginForm;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.UpdateDomain;
import io.gravitee.am.service.model.UpdateLoginForm;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
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
    public Maybe<Domain> findById(String id) {
        LOGGER.debug("Find domain by ID: {}", id);
        return domainRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a domain using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a domain using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<Set<Domain>> findAll() {
        LOGGER.debug("Find all domains");
        return domainRepository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all domains", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find all domains", ex));
                });
    }

    @Override
    public Single<Set<Domain>> findByIdIn(Collection<String> ids) {
        LOGGER.debug("Find domains by id in {}", ids);
        return domainRepository.findByIdIn(ids)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find domains by id in {}", ids, ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find domains by id in", ex));
                });
    }

    @Override
    public Single<Domain> create(NewDomain newDomain) {
        LOGGER.debug("Create a new domain: {}", newDomain);
        String id = generateContextPath(newDomain.getName());

        return domainRepository.findById(id)
                .isEmpty()
                .flatMap(empty -> {
                    if (!empty) {
                        throw new DomainAlreadyExistsException(newDomain.getName());
                    } else {
                        Domain domain = new Domain();
                        domain.setId(id);
                        domain.setPath(id);
                        domain.setName(newDomain.getName());
                        domain.setDescription(newDomain.getDescription());
                        domain.setEnabled(false);
                        domain.setCreatedAt(new Date());
                        domain.setUpdatedAt(domain.getCreatedAt());
                        return domainRepository.create(domain);
                    }
                })
            .onErrorResumeNext(ex -> {
                if (ex instanceof AbstractManagementException) {
                    return Single.error(ex);
                }

                LOGGER.error("An error occurs while trying to create a domain", ex);
                return Single.error(new TechnicalManagementException("An error occurs while trying to create a domain", ex));
            });
    }

    @Override
    public Single<Domain> update(String domainId, UpdateDomain updateDomain) {
        LOGGER.debug("Update an existing domain: {}", updateDomain);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(oldDomain -> {
                    Domain domain = new Domain();
                    domain.setId(domainId);
                    domain.setPath(updateDomain.getPath());
                    domain.setName(updateDomain.getName());
                    domain.setDescription(updateDomain.getDescription());
                    domain.setEnabled(updateDomain.isEnabled());
                    domain.setIdentities(updateDomain.getIdentities());
                    domain.setOauth2Identities(updateDomain.getOauth2Identities());
                    // master flag is set programmatically (keep old value)
                    domain.setMaster(oldDomain.isMaster());
                    domain.setCreatedAt(oldDomain.getCreatedAt());
                    domain.setUpdatedAt(new Date());
                    domain.setLoginForm(oldDomain.getLoginForm());

                    return domainRepository.update(domain);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a domain", ex));
                });
    }

    @Override
    public Single<Domain> reload(String domainId) {
        LOGGER.debug("Reload a domain: {}", domainId);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(oldDomain -> {
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
                    domain.setIdentities(oldDomain.getIdentities());
                    domain.setOauth2Identities(oldDomain.getOauth2Identities());

                    return domainRepository.update(domain);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to reload a domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to reload a domain", ex));
                });
    }

    @Override
    public Single<Domain> setMasterDomain(String domainId, boolean isMaster) {
        LOGGER.debug("Set master flag for domain: {}", domainId);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(oldDomain -> {
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
                    domain.setIdentities(oldDomain.getIdentities());
                    domain.setOauth2Identities(oldDomain.getOauth2Identities());
                    return domainRepository.update(domain);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to set master flag for domain {}", domainId, ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to set master flag for a domain", ex));
                });
    }

    @Override
    public Single<Irrelevant> delete(String domainId) {
        LOGGER.debug("Delete security domain {}", domainId);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(domain -> {
                    if (domain.isMaster()) {
                        throw new DomainDeleteMasterException(domainId);
                    }
                    return Single.just(domain);
                })
                .flatMap(domain -> {
                    // delete clients
                    return clientService.findByDomain(domainId)
                            .flatMap(clients -> Observable.fromIterable(clients)
                                    .flatMapSingle(c -> clientService.delete(c.getId())).toList());
                })
                .flatMap(irrelevant -> {
                    // delete certificates
                    return certificateService.findByDomain(domainId)
                            .flatMap(certificates -> Observable.fromIterable(certificates)
                                    .flatMapSingle(c -> certificateService.delete(c.getId())).toList());
                })
                .flatMap(irrelevant -> {
                    // delete identity providers
                    return identityProviderService.findByDomain(domainId)
                            .flatMap(idps -> Observable.fromIterable(idps)
                                    .flatMapSingle(i -> identityProviderService.delete(i.getId())).toList());
                })
                .flatMap(irrelevant -> {
                    // delete roles
                    return roleService.findByDomain(domainId)
                            .flatMap(roles -> Observable.fromIterable(roles)
                                    .flatMapSingle(r -> roleService.delete(r.getId())).toList());
                })
                .flatMap(irrelevant -> {
                    // delete users
                    return userService.findByDomain(domainId)
                            .flatMap(users -> Observable.fromIterable(users)
                                    .flatMapSingle(u -> userService.delete(u.getId())).toList());
                })
                .flatMap(irrelevant -> domainRepository.delete(domainId))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete security domain {}", domainId, ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to delete security domain " + domainId, ex));
                });
    }

    @Override
    public Single<LoginForm> updateLoginForm(String domainId, UpdateLoginForm loginForm) {
        LOGGER.debug("Update login form of an existing domain: {}", domainId);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(domain -> {
                    LoginForm form = new LoginForm();
                    form.setEnabled(loginForm.isEnabled());
                    form.setContent(loginForm.getContent());
                    form.setAssets(loginForm.getAssets());

                    domain.setLoginForm(form);
                    domain.setUpdatedAt(new Date());

                    return domainRepository.update(domain).map(domain1 -> form);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update login form domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a domain", ex));
                });
    }

    @Override
    public Single<Domain> deleteLoginForm(String domainId) {
        LOGGER.debug("Delete login form of an existing domain: {}", domainId);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(domain -> {
                    domain.setLoginForm(null);
                    domain.setUpdatedAt(new Date());

                    return domainRepository.update(domain);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update login form domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update login form domain", ex));
                });
    }

    private String generateContextPath(String domainName) {
        String nfdNormalizedString = Normalizer.normalize(domainName, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        domainName = pattern.matcher(nfdNormalizedString).replaceAll("");
        return domainName.toLowerCase().trim().replaceAll("\\s{1,}", "-");
    }
}

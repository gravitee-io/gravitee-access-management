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
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.NewSystemScope;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.am.service.model.UpdateDomain;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.DomainAuditBuilder;
import io.reactivex.Completable;
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
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private ApplicationService applicationService;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private ExtensionGrantService extensionGrantService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserService userService;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private FormService formService;

    @Autowired
    private ReporterService reporterService;

    @Autowired
    private PolicyService policyService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EventService eventService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private FactorService factorService;

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
    public Maybe<Domain> findMaster() {
        LOGGER.debug("Find master domain");
        return domainRepository.findMaster()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find the master domain", ex);
                    return Maybe.error(new TechnicalManagementException("An error occurs while trying to find the master domain", ex));
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
    public Single<Domain> create(String organizationId, String environmentId, NewDomain newDomain, User principal) {
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
                        domain.setOidc(OIDCSettings.defaultSettings());
                        domain.setCreatedAt(new Date());
                        domain.setUpdatedAt(domain.getCreatedAt());
                        return domainRepository.create(domain);
                    }
                })
                // create default system scopes
                .flatMap(this::createSystemScopes)
                // create default certificate
                .flatMap(this::createDefaultCertificate)
                // create owner
                .flatMap(domain -> {
                    if (principal == null) {
                        return Single.just(domain);
                    }
                    return roleService.findSystemRole(SystemRole.PRIMARY_OWNER, RoleScope.DOMAIN)
                            .switchIfEmpty(Single.error(new InvalidRoleException("Cannot assign owner to the domain, owner role does not exist")))
                            .flatMap(role -> {
                                Membership membership = new Membership();
                                membership.setDomain(domain.getId());
                                membership.setMemberId(principal.getId());
                                membership.setMemberType(MemberType.USER);
                                membership.setReferenceId(domain.getId());
                                membership.setReferenceType(ReferenceType.DOMAIN);
                                membership.setRole(role.getId());
                                return membershipService.addOrUpdate(organizationId, membership)
                                        .map(__ -> domain);
                            });
                })
                // create event for sync process
                .flatMap(domain -> {
                    Event event = new Event(Type.DOMAIN, new Payload(domain.getId(), domain.getId(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(domain));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a domain", ex));
                })
                .doOnSuccess(domain -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class).principal(principal).type(EventType.DOMAIN_CREATED).domain(domain).referenceType(ReferenceType.ENVIRONMENT).referenceId(environmentId)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class).principal(principal).type(EventType.DOMAIN_CREATED).referenceType(ReferenceType.ENVIRONMENT).referenceId(environmentId).throwable(throwable)));
    }

    @Override
    public Single<Domain> update(String domainId, UpdateDomain updateDomain, User principal) {
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
                    // master flag is set programmatically (keep old value)
                    domain.setMaster(oldDomain.isMaster());
                    domain.setCreatedAt(oldDomain.getCreatedAt());
                    domain.setUpdatedAt(new Date());
                    //As it is not managed by UpdateDomain, we keep old value
                    domain.setOidc(oldDomain.getOidc());
                    domain.setScim(updateDomain.getScim());
                    domain.setLoginSettings(updateDomain.getLoginSettings());
                    domain.setAccountSettings(updateDomain.getAccountSettings());

                    return domainRepository.update(domain)
                            // create event for sync process
                            .flatMap(domain1 -> {
                                Event event = new Event(Type.DOMAIN, new Payload(domain1.getId(), domain1.getId(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(domain1));
                            })
                            .doOnSuccess(domain1 -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class).principal(principal).type(EventType.DOMAIN_UPDATED).oldValue(oldDomain).domain(domain1)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class).principal(principal).type(EventType.DOMAIN_UPDATED).throwable(throwable)));
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
    public Single<Domain> update(String domainId, Domain domain) {
        LOGGER.debug("Update an existing domain: {}", domain);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(__ -> {
                    domain.setUpdatedAt(new Date());
                    return domainRepository.update(domain);
                })
                // create event for sync process
                .flatMap(domain1 -> {
                    Event event = new Event(Type.DOMAIN, new Payload(domain1.getId(), domain1.getId(), Action.UPDATE));
                    return eventService.create(event).flatMap(__ -> Single.just(domain1));
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
    public Single<Domain> patch(String domainId, PatchDomain patchDomain, User principal) {
        LOGGER.debug("Patching an existing domain ({}) with : {}", domainId, patchDomain);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(oldDomain -> {
                    Domain toPatch = patchDomain.patch(oldDomain);
                    toPatch.setUpdatedAt(new Date());
                    return domainRepository.update(toPatch)
                            // create event for sync process
                            .flatMap(domain1 -> {
                                Event event = new Event(Type.DOMAIN, new Payload(domain1.getId(), domain1.getId(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(domain1));
                            })
                            .doOnSuccess(domain1 -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class).principal(principal).type(EventType.DOMAIN_UPDATED).oldValue(oldDomain).domain(domain1)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class).principal(principal).type(EventType.DOMAIN_UPDATED).throwable(throwable)));

                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to patch a domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to patch a domain", ex));
                });
    }

    @Override
    public Single<Domain> setMasterDomain(String domainId, boolean isMaster) {
        LOGGER.debug("Set master flag for domain: {}", domainId);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(oldDomain -> {
                    oldDomain.setMaster(isMaster);
                    oldDomain.setUpdatedAt(new Date());
                    return domainRepository.update(oldDomain);
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
    public Completable delete(String domainId, User principal) {
        LOGGER.debug("Delete security domain {}", domainId);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(domain -> {
                    if (domain.isMaster()) {
                        throw new DomainDeleteMasterException(domainId);
                    }
                    return Single.just(domain);
                })
                .flatMapCompletable(domain -> {
                    // delete applications
                    return applicationService.findByDomain(domainId)
                            .flatMapCompletable(applications -> {
                                List<Completable> deleteApplicationsCompletable = applications.stream().map(a -> applicationService.delete(a.getId())).collect(Collectors.toList());
                                return Completable.concat(deleteApplicationsCompletable);
                            })
                            // delete certificates
                            .andThen(certificateService.findByDomain(domainId)
                                    .flatMapCompletable(certificates -> {
                                        List<Completable> deleteCertificatesCompletable = certificates.stream().map(c -> certificateService.delete(c.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteCertificatesCompletable);
                                    })
                            )
                            // delete identity providers
                            .andThen(identityProviderService.findByDomain(domainId)
                                    .flatMapCompletable(identityProviders -> {
                                        List<Completable> deleteIdentityProvidersCompletable = identityProviders.stream().map(i -> identityProviderService.delete(domainId, i.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteIdentityProvidersCompletable);
                                    })
                            )
                            // delete extension grants
                            .andThen(extensionGrantService.findByDomain(domainId)
                                    .flatMapCompletable(extensionGrants -> {
                                        List<Completable> deleteExtensionGrantsCompletable = extensionGrants.stream().map(i -> extensionGrantService.delete(domainId, i.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteExtensionGrantsCompletable);
                                    })
                            )
                            // delete roles
                            .andThen(roleService.findByDomain(domainId)
                                    .flatMapCompletable(roles -> {
                                        List<Completable> deleteRolesCompletable = roles.stream().map(r -> roleService.delete(ReferenceType.DOMAIN, domainId, r.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteRolesCompletable);
                                    })
                            )
                            // delete users
                            .andThen(userService.findByDomain(domainId)
                                    .flatMapCompletable(users -> {
                                        List<Completable> deleteUsersCompletable = users.stream().map(u -> userService.delete(u.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteUsersCompletable);
                                    })
                            )
                            // delete groups
                            .andThen(groupService.findByDomain(domainId)
                                    .flatMapCompletable(groups -> {
                                        List<Completable> deleteGroupsCompletable = groups.stream().map(u -> groupService.delete(ReferenceType.DOMAIN, domainId, u.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteGroupsCompletable);
                                    })
                            )
                            // delete scopes
                            .andThen(scopeService.findByDomain(domainId)
                                    .flatMapCompletable(scopes -> {
                                        List<Completable> deleteScopesCompletable = scopes.stream().map(s -> scopeService.delete(s.getId(), true)).collect(Collectors.toList());
                                        return Completable.concat(deleteScopesCompletable);
                                    })
                            )
                            // delete email templates
                            .andThen(emailTemplateService.findAll(ReferenceType.DOMAIN, domainId)
                                    .flatMapCompletable(scopes -> {
                                        List<Completable> deleteEmailsCompletable = scopes.stream().map(e -> emailTemplateService.delete(e.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteEmailsCompletable);
                                    })
                            )
                            // delete form templates
                            .andThen(formService.findByDomain(domainId)
                                    .flatMapCompletable(scopes -> {
                                        List<Completable> deleteFormsCompletable = scopes.stream().map(f -> formService.delete(domainId, f.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteFormsCompletable);
                                    })
                            )
                            // delete reporters
                            .andThen(reporterService.findByDomain(domainId)
                                    .flatMapCompletable(reporters -> {
                                        List<Completable> deleteReportersCompletable = reporters.stream().map(r -> reporterService.delete(r.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteReportersCompletable);
                                    })
                            )
                            // delete policies
                            .andThen(policyService.findByDomain(domainId)
                                    .flatMapCompletable(policies -> {
                                        List<Completable> deletePoliciesCompletable = policies.stream().map(p -> policyService.delete(p.getId())).collect(Collectors.toList());
                                        return Completable.concat(deletePoliciesCompletable);
                                    })
                            )
                            // delete memberships
                            .andThen(membershipService.findByReference(domainId, ReferenceType.DOMAIN)
                                    .flatMapCompletable(memberships -> {
                                        List<Completable> deleteMembershipsCompletable = memberships.stream().map(m -> membershipService.delete(m.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteMembershipsCompletable);
                                    })
                            )
                            // delete factors
                            .andThen(factorService.findByDomain(domainId)
                                    .flatMapCompletable(factors -> {
                                        List<Completable> deleteFactorsCompletable = factors.stream().map(f -> factorService.delete(domainId, f.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteFactorsCompletable);
                                    })
                            )
                            .andThen(domainRepository.delete(domainId))
                            .andThen(Completable.fromSingle(eventService.create(new Event(Type.DOMAIN, new Payload(domainId, domainId, Action.DELETE)))))
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class).principal(principal).type(EventType.DOMAIN_DELETED).domain(domain)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class).principal(principal).type(EventType.DOMAIN_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete security domain {}", domainId, ex);
                    return Completable.error(new TechnicalManagementException("An error occurs while trying to delete security domain " + domainId, ex));
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

    private Single<Domain> createSystemScopes(Domain domain) {
        return Observable.fromArray(io.gravitee.am.common.oidc.Scope.values())
                .flatMapSingle(systemScope -> {
                    final String scopeKey = systemScope.getKey();
                    NewSystemScope scope = new NewSystemScope();
                    scope.setKey(scopeKey);
                    scope.setClaims(systemScope.getClaims());
                    scope.setName(systemScope.getLabel());
                    scope.setDescription(systemScope.getDescription());
                    scope.setDiscovery(systemScope.isDiscovery());
                    return scopeService.create(domain.getId(), scope);
                })
                .lastOrError()
                .map(scope -> domain);
    }

    private Single<Domain> createDefaultCertificate(Domain domain) {
        return certificateService
                .create(domain.getId())
                .map(certificate -> domain);
    }

    private String generateContextPath(String domainName) {
        String nfdNormalizedString = Normalizer.normalize(domainName, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        domainName = pattern.matcher(nfdNormalizedString).replaceAll("");
        return domainName.toLowerCase().trim().replaceAll("\\s{1,}", "-");
    }
}

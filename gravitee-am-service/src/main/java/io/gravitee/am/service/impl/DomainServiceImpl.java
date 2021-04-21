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
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.utils.PathUtils;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.*;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.search.AlertNotifierCriteria;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.NewSystemScope;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.DomainAuditBuilder;
import io.gravitee.am.service.validators.AccountSettingsValidator;
import io.gravitee.am.service.validators.DomainValidator;
import io.gravitee.am.service.validators.VirtualHostValidator;
import io.gravitee.common.utils.IdGenerator;
import io.reactivex.*;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.regex.Matcher;
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

    private static final Pattern SCHEME_PATTERN = Pattern.compile("^(https?://).*$");

    @Value("${gateway.url:http://localhost:8092}")
    private String gatewayUrl;

    @Lazy
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
    private FlowService flowService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EventService eventService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private FactorService factorService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private AlertTriggerService alertTriggerService;

    @Autowired
    private AlertNotifierService alertNotifierService;

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
    public Single<Domain> findByHrid(String environmentId, String hrid) {
        LOGGER.debug("Find domain by hrid: {}", hrid);
        return domainRepository.findByHrid(ReferenceType.ENVIRONMENT, environmentId, hrid)
                .switchIfEmpty(Single.error(new DomainNotFoundException(hrid)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error has occurred when trying to find a domain using its hrid: {}", hrid, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error has occurred when trying to find a domain using its hrid: %s", hrid), ex));
                });
    }

    @Override
    public Flowable<Domain> search(String organizationId, String environmentId, String query) {
        LOGGER.debug("Search domains with query {} for environmentId {}", query, environmentId);
        return environmentService.findById(environmentId, organizationId)
                .map(Environment::getId)
                .flatMapPublisher(envId -> domainRepository.search(environmentId, query))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error has occurred when trying to search domains with query {} for environmentId {}", query, environmentId, ex);
                });
    }

    @Override
    public Flowable<Domain> findAllByEnvironment(String organizationId, String environmentId) {
        LOGGER.debug("Find all domains of environment {} (organization {})", environmentId, organizationId);

        return environmentService.findById(environmentId, organizationId)
                .map(Environment::getId)
                .flatMapPublisher(envId -> domainRepository.findAllByReferenceId(envId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error has occurred when trying to find domains by environment", ex);
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
    public Flowable<Domain> findAllByCriteria(DomainCriteria criteria) {
        LOGGER.debug("Find all domains by criteria");
        return domainRepository.findAllByCriteria(criteria);
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
        // generate hrid
        String hrid = IdGenerator.generate(newDomain.getName());
        return domainRepository.findByHrid(ReferenceType.ENVIRONMENT, environmentId, hrid)
                .isEmpty()
                .flatMap(empty -> {
                    if (!empty) {
                        throw new DomainAlreadyExistsException(newDomain.getName());
                    } else {
                        Domain domain = new Domain();
                        domain.setId(RandomString.generate());
                        domain.setHrid(hrid);
                        domain.setPath(generateContextPath(newDomain.getName()));
                        domain.setName(newDomain.getName());
                        domain.setDescription(newDomain.getDescription());
                        domain.setEnabled(false);
                        domain.setAlertEnabled(false);
                        domain.setOidc(OIDCSettings.defaultSettings());
                        domain.setReferenceType(ReferenceType.ENVIRONMENT);
                        domain.setReferenceId(environmentId);
                        domain.setCreatedAt(new Date());
                        domain.setUpdatedAt(domain.getCreatedAt());

                        return environmentService.findById(domain.getReferenceId())
                                .doOnSuccess(environment -> setDeployMode(domain, environment))
                                .flatMapCompletable(environment -> validateDomain(domain, environment))
                                .andThen(Single.defer(() -> domainRepository.create(domain)));
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
                    return roleService.findSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER, ReferenceType.DOMAIN)
                            .switchIfEmpty(Single.error(new InvalidRoleException("Cannot assign owner to the domain, owner role does not exist")))
                            .flatMap(role -> {
                                Membership membership = new Membership();
                                membership.setDomain(domain.getId());
                                membership.setMemberId(principal.getId());
                                membership.setMemberType(MemberType.USER);
                                membership.setReferenceId(domain.getId());
                                membership.setReferenceType(ReferenceType.DOMAIN);
                                membership.setRoleId(role.getId());
                                return membershipService.addOrUpdate(organizationId, membership)
                                        .map(__ -> domain);
                            });
                })
                // create event for sync process
                .flatMap(domain -> {
                    Event event = new Event(Type.DOMAIN, new Payload(domain.getId(), ReferenceType.DOMAIN, domain.getId(), Action.CREATE));
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
    public Single<Domain> update(String domainId, Domain domain) {
        LOGGER.debug("Update an existing domain: {}", domain);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapSingle(__ -> {
                    domain.setHrid(IdGenerator.generate(domain.getName()));
                    domain.setUpdatedAt(new Date());
                    return validateDomain(domain)
                            .andThen(Single.defer(() -> domainRepository.update(domain)));
                })
                // create event for sync process
                .flatMap(domain1 -> {
                    Event event = new Event(Type.DOMAIN, new Payload(domain1.getId(), ReferenceType.DOMAIN, domain1.getId(), Action.UPDATE));
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
                    final AccountSettings accountSettings = toPatch.getAccountSettings();
                    if (AccountSettingsValidator.hasInvalidResetPasswordFields(accountSettings)) {
                       return Single.error(new InvalidParameterException("Unexpected forgot password field"));
                    }
                    toPatch.setHrid(IdGenerator.generate(toPatch.getName()));
                    toPatch.setUpdatedAt(new Date());
                    return validateDomain(toPatch)
                            .andThen(Single.defer(() -> domainRepository.update(toPatch)))
                            // create event for sync process
                            .flatMap(domain1 -> {
                                Event event = new Event(Type.DOMAIN, new Payload(domain1.getId(), ReferenceType.DOMAIN, domain1.getId(), Action.UPDATE));
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
    public Completable delete(String domainId, User principal) {
        LOGGER.debug("Delete security domain {}", domainId);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
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
                                    .flatMapCompletable(emailTemplates -> {
                                        List<Completable> deleteEmailsCompletable = emailTemplates.stream().map(e -> emailTemplateService.delete(e.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteEmailsCompletable);
                                    })
                            )
                            // delete form templates
                            .andThen(formService.findByDomain(domainId)
                                    .flatMapCompletable(formTemplates -> {
                                        List<Completable> deleteFormsCompletable = formTemplates.stream().map(f -> formService.delete(domainId, f.getId())).collect(Collectors.toList());
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
                            // delete flows
                            .andThen(flowService.findAll(ReferenceType.DOMAIN, domainId)
                                    .flatMapCompletable(flows -> {
                                        List<Completable> deletePoliciesCompletable = flows.stream().filter(f -> f.getId() != null).map(f -> flowService.delete(f.getId())).collect(Collectors.toList());
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
                            // delete uma resources
                            .andThen(resourceService.findByDomain(domainId)
                                    .flatMapCompletable(resources -> {
                                        List<Completable> deletedResourceCompletable = resources.stream().map(resourceService::delete).collect(Collectors.toList());
                                        return Completable.concat(deletedResourceCompletable);
                                    })
                            )
                            // delete alert triggers
                            .andThen(alertTriggerService.findByDomainAndCriteria(domainId, new AlertTriggerCriteria())
                                    .flatMapCompletable(alertTrigger -> alertTriggerService.delete(alertTrigger.getReferenceType(), alertTrigger.getReferenceId(), alertTrigger.getId(), principal)
                                    )
                            )
                            // delete alert notifiers
                            .andThen(alertNotifierService.findByDomainAndCriteria(domainId, new AlertNotifierCriteria())
                                    .flatMapCompletable(alertNotifier -> alertNotifierService.delete(alertNotifier.getReferenceType(), alertNotifier.getReferenceId(), alertNotifier.getId(), principal)
                                    )
                            )
                            .andThen(domainRepository.delete(domainId))
                            .andThen(Completable.fromSingle(eventService.create(new Event(Type.DOMAIN, new Payload(domainId, ReferenceType.DOMAIN, domainId, Action.DELETE)))))
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

    @Override
    public String buildUrl(Domain domain, String path) {
        String entryPoint = gatewayUrl;

        if (entryPoint != null && entryPoint.endsWith("/")) {
            entryPoint = entryPoint.substring(0, entryPoint.length() - 1);
        }

        String uri = null;

        if (domain.isVhostMode()) {
            // Try generate uri using defined virtual hosts.
            Matcher matcher = SCHEME_PATTERN.matcher(entryPoint);
            String scheme = "http";
            if (matcher.matches()) {
                scheme = matcher.group(1);
            }

            for (VirtualHost vhost : domain.getVhosts()) {
                if (vhost.isOverrideEntrypoint()) {
                    uri = scheme + vhost.getHost() + vhost.getPath() + path;
                    break;
                }
            }
        }

        if (uri == null) {
            uri = entryPoint + PathUtils.sanitize(domain.getPath() + path);
        }

        return uri;
    }

    private Single<Domain> createDefaultCertificate(Domain domain) {
        return certificateService
                .create(domain.getId())
                .map(certificate -> domain);
    }

    private String generateContextPath(String domainName) {

        return "/" + IdGenerator.generate(domainName);
    }

    private Completable validateDomain(Domain domain) {
        if (domain.getReferenceType() != ReferenceType.ENVIRONMENT) {
            return Completable.error(new InvalidDomainException("Domain must be attached to an environment"));
        }

        // check the uniqueness of the domain
        return domainRepository.findByHrid(domain.getReferenceType(), domain.getReferenceId(), domain.getHrid())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMapCompletable(optDomain -> {
                    if (optDomain.isPresent() && !optDomain.get().getId().equals(domain.getId())) {
                        return Completable.error(new DomainAlreadyExistsException(domain.getName()));
                    } else {
                        // Get environment domain restrictions and validate all data are correctly defined.
                        return environmentService.findById(domain.getReferenceId())
                                .flatMapCompletable(environment -> validateDomain(domain, environment));
                    }
                });
    }

    private Completable validateDomain(Domain domain, Environment environment) {

        // Get environment domain restrictions and validate all data are correctly defined.
        return DomainValidator.validate(domain, environment.getDomainRestrictions())
                .andThen(findAll()
                        .flatMapCompletable(domains -> VirtualHostValidator.validateDomainVhosts(domain, domains)));
    }

    private void setDeployMode(Domain domain, Environment environment) {

        if (CollectionUtils.isEmpty(environment.getDomainRestrictions())) {
            domain.setVhostMode(false);
        } else {
            // There are some domain restrictions defined at environment level. Switching to domain vhost mode.

            // Creating one vhost per constraint.
            List<VirtualHost> vhosts = environment.getDomainRestrictions().stream().map(domainConstraint -> {
                VirtualHost virtualHost = new VirtualHost();
                virtualHost.setHost(domainConstraint);
                virtualHost.setPath(domain.getPath());

                return virtualHost;
            }).collect(Collectors.toList());

            // The first one will be used as primary displayed entrypoint.
            vhosts.get(0).setOverrideEntrypoint(true);

            domain.setVhostMode(true);
            domain.setVhosts(vhosts);
        }
    }
}

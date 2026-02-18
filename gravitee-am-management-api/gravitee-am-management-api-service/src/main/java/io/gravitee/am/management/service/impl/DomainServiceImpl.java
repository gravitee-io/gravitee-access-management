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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.exception.oauth2.InvalidRequestUriException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.DefaultIdentityProviderService;
import io.gravitee.am.management.service.DomainGroupService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.dataplane.UMAResourceManagementService;
import io.gravitee.am.management.service.dataplane.UserActivityManagementService;
import io.gravitee.am.model.CertificateSettings;
import io.gravitee.am.model.CorsSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.DomainVersion;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.search.AlertNotifierCriteria;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.gravitee.am.service.AlertNotifierService;
import io.gravitee.am.service.AlertTriggerService;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationDeviceNotifierService;
import io.gravitee.am.service.AuthorizationEngineService;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.DeviceIdentifierService;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.EmailTemplateService;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.ExtensionGrantService;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.FlowService;
import io.gravitee.am.service.FormService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.PasswordPolicyService;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.ServiceResourceService;
import io.gravitee.am.service.ThemeService;
import io.gravitee.am.service.CertificateCredentialService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.DomainAlreadyExistsException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.InvalidDataPlaneException;
import io.gravitee.am.service.exception.InvalidDomainException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.InvalidRedirectUriException;
import io.gravitee.am.service.exception.InvalidRoleException;
import io.gravitee.am.service.exception.InvalidTargetUrlException;
import io.gravitee.am.service.exception.InvalidWebAuthnConfigurationException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.NewSystemScope;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.DomainAuditBuilder;
import io.gravitee.am.service.validators.accountsettings.AccountSettingsValidator;
import io.gravitee.am.service.validators.domain.DomainValidator;
import io.gravitee.am.service.validators.virtualhost.VirtualHostValidator;
import io.gravitee.common.utils.IdGenerator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.MultiMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.gravitee.am.common.web.UriBuilder.isHttp;
import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.model.ReferenceType.ORGANIZATION;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Primary
@AllArgsConstructor
@NoArgsConstructor
public class DomainServiceImpl implements DomainService {
    /**
     * According to <a href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#auth_request">Auth request</a>
     * the binding_message value SHOULD be relatively short and use a limited set of plain text characters.
     * Arbitrarily fix the maximum value to 2048 characters that seems enough to display a message on a mobile device.
     */
    private static final int CIBA_MAX_BINDING_MESSAGE_LENGTH = 2048;
    public static final String IS_MALFORMED = " is malformed";

    private final Logger LOGGER = LoggerFactory.getLogger(DomainServiceImpl.class);

    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Lazy
    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private DomainReadService domainReadService;

    @Autowired
    private UserActivityManagementService userActivityService;

    @Autowired
    private DomainValidator domainValidator;

    @Autowired
    private VirtualHostValidator virtualHostValidator;

    @Autowired
    private AccountSettingsValidator accountSettingsValidator;

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
    private ScopeService scopeService;

    @Autowired
    private DomainGroupService domainGroupService;

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
    private UMAResourceManagementService resourceService;

    @Autowired
    private AlertTriggerService alertTriggerService;

    @Autowired
    private AlertNotifierService alertNotifierService;

    @Autowired
    private AuthenticationDeviceNotifierService authenticationDeviceNotifierService;

    @Autowired
    private I18nDictionaryService i18nDictionaryService;

    @Autowired
    private ThemeService themeService;

    @Autowired
    private PasswordHistoryService passwordHistoryService;

    @Autowired
    private PasswordPolicyService passwordPolicyService;

    @Autowired
    private CertificateCredentialService certificateCredentialService;

    @Autowired
    private EntrypointService entrypointService;

    @Autowired
    private DefaultIdentityProviderService defaultIdentityProviderService;

    @Autowired
    private ProtectedResourceService protectedResourceService;

    @With(AccessLevel.PACKAGE) // to make test setup less painful
    @Value("${domains.reporters.default.enabled:true}")
    private boolean createDefaultReporters = true;

    @With(AccessLevel.PACKAGE) // to make test setup less painful
    @Value("${domains.identities.default.enabled:true}")
    private boolean createDefaultIdentityProvider = true;
    @Autowired
    private DeviceIdentifierService deviceIdentifierService;
    @Autowired
    private ServiceResourceService serviceResourceService;
    @Autowired
    private AuthorizationEngineService authorizationEngineService;

    @Value("${" + TokenExchangeSettings.PROPERTY_MAX_DELEGATION_DEPTH_LIMIT + ":" + TokenExchangeSettings.DEFAULT_MAX_DELEGATION_DEPTH_LIMIT + "}")
    private int maxDelegationDepthLimit = TokenExchangeSettings.DEFAULT_MAX_DELEGATION_DEPTH_LIMIT;

    @Override
    public Maybe<Domain> findById(String id) {
        return domainReadService.findById(id);
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
                    return Flowable.empty();
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
                    return Flowable.empty();
                });
    }

    @Override
    public Single<List<Domain>> findAll() {
        return listAll().toList();
    }

    @Override
    public Flowable<Domain> listAll() {
        return domainReadService.listAll();
    }

    @Override
    public Flowable<Domain> findAllByCriteria(DomainCriteria criteria) {
        LOGGER.debug("Find all domains by criteria");
        return domainRepository.findAllByCriteria(criteria);
    }

    @Override
    public Flowable<Domain> findByIdIn(Collection<String> ids) {
        LOGGER.debug("Find domains by id in {}", ids);
        return domainRepository.findByIdIn(ids)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurred while trying to find domains by id in {}", ids, ex);
                    return Flowable.error(new TechnicalManagementException("An error occurred while trying to find domains by id in", ex));
                });
    }

    @Override
    public Single<Domain> create(String organizationId, String environmentId, NewDomain newDomain, User principal) {
        LOGGER.debug("Create a new domain: {}", newDomain);
        // generate hrid
        String hrid = IdGenerator.generate(newDomain.getName());
        if (dataPlaneRegistry.getDataPlanes().stream().map(DataPlaneDescription::id).noneMatch(id -> id.equals(newDomain.getDataPlaneId()))) {
            return Single.error(new InvalidDataPlaneException("An error occurred while trying to create a domain. Data Plane with provided Id doesn't exist."));
        }
        return domainRepository.findByHrid(ReferenceType.ENVIRONMENT, environmentId, hrid)
                .isEmpty()
                .flatMap(empty -> {
                    if (!empty) {
                        throw new DomainAlreadyExistsException(newDomain.getName());
                    } else {
                        Domain domain = new Domain();
                        domain.setVersion(DomainVersion.V2_0);
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
                        domain.setDataPlaneId(newDomain.getDataPlaneId());

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
                    return roleService.findSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER, DOMAIN)
                            .switchIfEmpty(Single.error(new InvalidRoleException("Cannot assign owner to the domain, owner role does not exist")))
                            .flatMap(role -> {
                                Membership membership = new Membership();
                                membership.setDomain(domain.getId());
                                membership.setMemberId(principal.getId());
                                membership.setMemberType(MemberType.USER);
                                membership.setReferenceId(domain.getId());
                                membership.setReferenceType(DOMAIN);
                                membership.setRoleId(role.getId());
                                return membershipService.addOrUpdate(organizationId, membership)
                                        .map(__ -> domain);
                            });
                })
                //create default IdP
                .flatMap(domain -> {
                    if (!createDefaultIdentityProvider) {
                        return Single.just(domain);
                    }
                    return defaultIdentityProviderService.create(domain).map(__ -> domain);
                })
                // create default reporter
                .flatMap(domain -> {
                    if (!createDefaultReporters) {
                        return Single.just(domain);
                    }
                    //default behaviour
                    return reporterService.createDefault(Reference.domain(domain.getId())).map(__ -> domain);
                })
                // create event for sync process
                .flatMap(domain -> {
                    Event event = new Event(Type.DOMAIN, new Payload(domain.getId(), DOMAIN, domain.getId(), Action.CREATE));
                    return eventService.create(event, domain).flatMap(e -> Single.just(domain));
                })
                .flatMap(domain -> reporterService.notifyInheritedReporters(Reference.organization(organizationId), Reference.domain(domain.getId()), Action.CREATE)
                        .andThen(Single.just(domain)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurred while trying to create a domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurred while trying to create a domain", ex));
                })
                .doOnSuccess(domain -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.DOMAIN_CREATED)
                        .domain(domain)
                        .reference(Reference.organization(organizationId))))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.DOMAIN_CREATED)
                        .reference(Reference.organization(organizationId))
                        .throwable(throwable)));
    }

    @Override
    public Single<Domain> update(String domainId, Domain domain) {
        LOGGER.debug("Update an existing domain: {}", domain);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Single.error(new DomainNotFoundException(domainId)))
                .flatMap(existingDomain -> {
                    if(existingDomain.getDataPlaneId() != null &&
                            !existingDomain.getDataPlaneId().equals(domain.getDataPlaneId())){
                        return Single.error(new InvalidParameterException("Once domain is created, [dataPlaneId] cannot be changed."));
                    }
                    domain.setId(existingDomain.getId());
                    domain.setVersion(existingDomain.getVersion());
                    domain.setReferenceId(existingDomain.getReferenceId());
                    domain.setReferenceType(existingDomain.getReferenceType());
                    domain.setHrid(IdGenerator.generate(domain.getName()));
                    domain.setUpdatedAt(new Date());
                    return validateDomain(domain)
                            .andThen(validateCertificateSettings(domain))
                            .andThen(Single.defer(() -> domainRepository.update(domain)));
                })
                // create event for sync process
                .flatMap(domain1 -> {
                    Event event = new Event(Type.DOMAIN, new Payload(domain1.getId(), DOMAIN, domain1.getId(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(domain1));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurred while trying to update a domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurred while trying to update a domain", ex));
                });
    }

    @Override
    public Single<Domain> patch(GraviteeContext graviteeContext, String domainId, PatchDomain patchDomain, User principal) {
        LOGGER.debug("Patching an existing domain ({}) with : {}", domainId, patchDomain);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Single.error(new DomainNotFoundException(domainId)))
                .flatMap(oldDomain -> {
                    Domain toPatch = patchDomain.patch(oldDomain);
                    if(oldDomain.getDataPlaneId() != null &&
                            !oldDomain.getDataPlaneId().equals(toPatch.getDataPlaneId())){
                        return Single.error(new InvalidParameterException("Once domain is created, [dataPlaneId] cannot be changed."));
                    }

                    final AccountSettings accountSettings = toPatch.getAccountSettings();
                    if (Boolean.FALSE.equals(accountSettingsValidator.validate(accountSettings))) {
                        return Single.error(new InvalidParameterException("Unexpected forgot password field"));
                    }
                    toPatch.setHrid(IdGenerator.generate(toPatch.getName()));
                    toPatch.setUpdatedAt(new Date());
                    return validateDomain(toPatch)
                            .andThen(validateCertificateSettings(toPatch))
                            .andThen(Single.defer(() -> domainRepository.update(toPatch)))
                            // create event for sync process
                            .flatMap(domain1 -> {
                                Event event = new Event(Type.DOMAIN, new Payload(domain1.getId(), DOMAIN, domain1.getId(), Action.UPDATE));
                                return eventService.create(event, domain1).flatMap(__ -> Single.just(domain1));
                            })
                            .doOnSuccess(domain1 -> {
                                auditService.report(AuditBuilder.builder(DomainAuditBuilder.class).principal(principal).type(EventType.DOMAIN_UPDATED).oldValue(oldDomain).domain(domain1));
                                if (needOrganizationAudit(oldDomain, toPatch)) {
                                    auditService.report(AuditBuilder.builder(DomainAuditBuilder.class)
                                            .principal(principal)
                                            .type(EventType.DOMAIN_UPDATED)
                                            .oldValue(oldDomain)
                                            .domain(domain1)
                                            .referenceType(ORGANIZATION)
                                            .referenceId(graviteeContext.getOrganizationId()));
                                }
                            })
                            .doOnError(throwable -> {
                                auditService.report(AuditBuilder.builder(DomainAuditBuilder.class).principal(principal).domain(oldDomain).type(EventType.DOMAIN_UPDATED).throwable(throwable));
                                if (needOrganizationAudit(oldDomain, toPatch)) {
                                    auditService.report(AuditBuilder.builder(DomainAuditBuilder.class)
                                            .principal(principal)
                                            .domain(oldDomain)
                                            .type(EventType.DOMAIN_UPDATED)
                                            .throwable(throwable)
                                            .reference(Reference.organization(graviteeContext.getOrganizationId())));
                                }
                            });

                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurred while trying to patch a domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurred while trying to patch a domain", ex));
                });
    }

    private boolean needOrganizationAudit(Domain oldDomain, Domain toPatch) {
        return !Objects.equals(oldDomain.getName(), toPatch.getName()) || oldDomain.isEnabled() != toPatch.isEnabled();
    }

    @Override
    public Single<Domain> updateCertificateSettings(GraviteeContext graviteeContext, String domainId, CertificateSettings certificateSettings, User principal) {
        LOGGER.debug("Updating certificate settings for domain: {}", domainId);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Single.error(new DomainNotFoundException(domainId)))
                .flatMap(oldDomain -> {
                    Domain toUpdate = new Domain(oldDomain);
                    toUpdate.setCertificateSettings(certificateSettings);
                    toUpdate.setUpdatedAt(new Date());
                    return validateCertificateSettings(toUpdate)
                            .andThen(Single.defer(() -> domainRepository.update(toUpdate)))
                            .flatMap(updatedDomain -> {
                                Event event = new Event(Type.DOMAIN_CERTIFICATE_SETTINGS, new Payload(updatedDomain.getId(), DOMAIN, updatedDomain.getId(), Action.UPDATE));
                                return eventService.create(event, updatedDomain).map(__ -> updatedDomain);
                            })
                            .doOnSuccess(updatedDomain -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.DOMAIN_UPDATED)
                                    .oldValue(oldDomain)
                                    .domain(updatedDomain)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class)
                                    .principal(principal)
                                    .domain(oldDomain)
                                    .type(EventType.DOMAIN_UPDATED)
                                    .throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurred while trying to update certificate settings for domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurred while trying to update certificate settings for domain", ex));
                });
    }

    @Override
    public Completable delete(GraviteeContext graviteeContext, String domainId, User principal) {
        LOGGER.debug("Delete security domain {}", domainId);
        return domainRepository.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .flatMapCompletable(domain -> {
                    // delete applications
                    return applicationService.findByDomain(domainId)
                            .flatMapCompletable(applications -> {
                                List<Completable> deleteApplicationsCompletable = applications.stream().map(a -> applicationService.delete(a.getId(), domain)).toList();
                                return Completable.concat(deleteApplicationsCompletable);
                            })
                            // delete identity providers
                            .andThen(identityProviderService.findByDomain(domainId)
                                    .flatMapCompletable(identityProvider ->
                                            identityProviderService.delete(domainId, identityProvider.getId())
                                    )
                            )
                            // delete protected resources
                            .andThen(protectedResourceService.findByDomain(domainId)
                                    .flatMapCompletable(protectedResource -> protectedResourceService.delete(domain, protectedResource.getId(), null, principal))
                            )
                            // delete certificates
                            .andThen(certificateService.findByDomain(domainId)
                                    .flatMapCompletable(certificate -> certificateService.delete(certificate.getId()))
                            )
                            // delete extension grants
                            .andThen(extensionGrantService.findByDomain(domainId)
                                    .flatMapCompletable(extensionGrant -> extensionGrantService.delete(domainId, extensionGrant.getId()))
                            )
                            // delete roles
                            .andThen(roleService.findByDomain(domainId)
                                    .flatMapCompletable(roles -> {
                                        List<Completable> deleteRolesCompletable = roles.stream().map(r -> roleService.delete(DOMAIN, domainId, r.getId())).toList();
                                        return Completable.concat(deleteRolesCompletable);
                                    })
                            )
                            //Delete all trace of activity of users for this domain
                            .andThen(userActivityService.deleteByDomain(domain))
                            // delete users
                            // do not delete one by one for memory consumption issue
                            // https://github.com/gravitee-io/issues/issues/6999
                            .andThen(dataPlaneRegistry.getUserRepository(domain).deleteByReference(domain.asReference()))
                            // delete groups
                            .andThen(domainGroupService.findAll(domain)
                                    .flatMapCompletable(group ->
                                            domainGroupService.delete(domain, group.getId(), principal))
                            )
                            // delete scopes
                            .andThen(scopeService.findByDomain(domainId, 0, Integer.MAX_VALUE)
                                    .flatMapCompletable(scopes -> {
                                        List<Completable> deleteScopesCompletable = scopes.getData().stream().map(s -> scopeService.delete(domain, s.getId(), true)).toList();
                                        return Completable.concat(deleteScopesCompletable);
                                    })
                            )
                            // delete email templates
                            .andThen(emailTemplateService.findAll(DOMAIN, domainId)
                                    .flatMapCompletable(emailTemplate -> emailTemplateService.delete(emailTemplate.getId()))
                            )
                            // delete form templates
                            .andThen(formService.findByDomain(domainId)
                                    .flatMapCompletable(formTemplate -> formService.delete(domainId, formTemplate.getId()))
                            )
                            // delete reporters
                            .andThen(reporterService.findByReference(Reference.domain(domainId))
                                    .flatMapCompletable(reporter ->
                                            reporterService.delete(reporter.getId()))
                            ).andThen(reporterService.notifyInheritedReporters(Reference.organization(graviteeContext.getOrganizationId()),
                                    Reference.domain(domainId),
                                    Action.DELETE))
                            // delete flows
                            .andThen(flowService.findAll(DOMAIN, domainId)
                                    .filter(f -> f.getId() != null)
                                    .flatMapCompletable(flows -> flowService.delete(flows.getId()))
                            )
                            // delete memberships
                            .andThen(membershipService.findByReference(domainId, DOMAIN)
                                    .flatMapCompletable(membership -> membershipService.delete(new Reference(membership.getReferenceType(), membership.getReferenceId()), membership.getId()))
                            )
                            // delete factors
                            .andThen(factorService.findByDomain(domainId)
                                    .flatMapCompletable(factor -> factorService.delete(domainId, factor.getId()))
                            )
                            // delete uma resources
                            .andThen(resourceService.findByDomain(domain)
                                    .flatMapCompletable(resource -> resourceService.delete(domain, resource))
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
                            // delete auth device notifier
                            .andThen(authenticationDeviceNotifierService.findByDomain(domainId)
                                    .flatMapCompletable(authDeviceNotifier -> authenticationDeviceNotifierService.delete(domainId, authDeviceNotifier.getId(), principal)
                                    )
                            )
                            // delete i18n dictionaries
                            .andThen(i18nDictionaryService.findAll(DOMAIN, domainId)
                                    .flatMapCompletable(i18nDictionary -> i18nDictionaryService.delete(DOMAIN, domainId, i18nDictionary.getId(), principal)
                                    )
                            )
                            // delete theme
                            // TODO improve this by implement a deleteThemeByReferenceId method
                            .andThen(themeService.findByReference(ReferenceType.DOMAIN, domainId)
                                    .flatMapCompletable(theme -> themeService.delete(domain, theme.getId(), principal)
                                    )
                            )
                            .andThen(passwordHistoryService.deleteByReference(domain))
                            .andThen(passwordPolicyService.deleteByReference(ReferenceType.DOMAIN, domainId))
                            .andThen(serviceResourceService.deleteByDomain(domainId))
                            .andThen(deviceIdentifierService.deleteByDomain(domainId))
                            // delete certificate credentials
                            .andThen(certificateCredentialService.deleteByDomain(domain))
                            .andThen(authorizationEngineService.deleteByDomain(domainId))
                            .andThen(domainRepository.delete(domainId))
                            .andThen(Completable.fromSingle(eventService.create(new Event(Type.DOMAIN, new Payload(domainId, DOMAIN, domainId, Action.DELETE), domain.getDataPlaneId(), domain.getReferenceId()), domain)))
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.DOMAIN_DELETED)
                                    .domain(domain)
                                    .reference(Reference.organization(graviteeContext.getOrganizationId()))))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(DomainAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.DOMAIN_DELETED)
                                    .throwable(throwable)
                                    .domain(domain)
                                    .reference(Reference.organization(graviteeContext.getOrganizationId()))));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurred while trying to delete security domain {}", domainId, ex);
                    return Completable.error(new TechnicalManagementException("An error occurred while trying to delete security domain " + domainId, ex));
                });
    }

    @Override
    public String buildUrl(Domain domain, String path, MultiMap queryParams) {
        return domainReadService.buildUrl(domain, path, queryParams);
    }

    @Override
    public Single<List<Entrypoint>> listEntryPoint(Domain domain, String organizationId) {
        return entrypointService.findAll(organizationId)
                .filter(entrypoint -> entrypoint.isDefaultEntrypoint()
                        || (entrypoint.getTags() != null && !entrypoint.getTags().isEmpty() && domain.getTags() != null && entrypoint.getTags().stream().anyMatch(tag -> domain.getTags().contains(tag))))
                .toList()
                .map(filteredEntrypoints -> {
                    if (filteredEntrypoints.size() > 1) {
                        // Remove default entrypoint if another entrypoint has matched.
                        filteredEntrypoints.removeIf(Entrypoint::isDefaultEntrypoint);
                    } else if (filteredEntrypoints.get(0).isDefaultEntrypoint()) {
                        // default entrypoint present, we check if the DataPlane description contains a GatewayUrl
                        // if so, we use it otherwise we keep the default entrypoint
                        ofNullable(this.dataPlaneRegistry.getDescription(domain).gatewayUrl())
                                .ifPresent(url -> filteredEntrypoints.get(0).setUrl(url));
                    }

                    return filteredEntrypoints;
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
                    return scopeService.create(domain, scope);
                })
                .lastOrError()
                .map(scope -> domain);
    }


    private Single<Domain> createDefaultCertificate(Domain domain) {
        return certificateService
                .create(domain)
                .map(certificate -> domain);
    }

    private String generateContextPath(String domainName) {

        return "/" + IdGenerator.generate(domainName);
    }

    private Completable validateCertificateSettings(Domain domain) {
        if (domain.getCertificateSettings() == null ||
                domain.getCertificateSettings().getFallbackCertificate() == null ||
                domain.getCertificateSettings().getFallbackCertificate().isEmpty()) {
            return Completable.complete();
        }

        String fallbackCertificateId = domain.getCertificateSettings().getFallbackCertificate();
        return certificateService.findById(fallbackCertificateId)
                .switchIfEmpty(Maybe.error(new InvalidParameterException("Fallback certificate not found: " + fallbackCertificateId)))
                .flatMapCompletable(certificate -> {
                    if (!domain.getId().equals(certificate.getDomain())) {
                        return Completable.error(new InvalidParameterException("Fallback certificate does not belong to this domain"));
                    }
                    return Completable.complete();
                });
    }

    private Completable validateDomain(Domain domain) throws URISyntaxException {
        if (domain.getReferenceType() != ReferenceType.ENVIRONMENT) {
            return Completable.error(new InvalidDomainException("Domain must be attached to an environment"));
        }

        if (domain.useCiba()) {
            if (domain.getOidc().getCibaSettings().getAuthReqExpiry() <= 0) {
                return Completable.error(new InvalidDomainException("CIBA settings are invalid: auth_req_id expiry must be higher than 0"));
            }

            if (domain.getOidc().getCibaSettings().getTokenReqInterval() <= 0) {
                return Completable.error(new InvalidDomainException("CIBA settings are invalid: token request interval must be higher than 0"));
            }

            if (domain.getOidc().getCibaSettings().getBindingMessageLength() <= 0) {
                return Completable.error(new InvalidDomainException("CIBA settings are invalid: maLength of binding_message must be higher than 0"));
            }

            if (domain.getOidc().getCibaSettings().getBindingMessageLength() > CIBA_MAX_BINDING_MESSAGE_LENGTH) {
                return Completable.error(new InvalidDomainException("CIBA settings are invalid: binding_message length too high"));
            }
        }

        if (domain.getOidc() != null) {
            try {
                validateRequestUris(domain);
                validatePostLogoutRedirectUris(domain);
            } catch (InvalidRedirectUriException e) {
                return Completable.error(e);
            } catch (Exception e) {
                return Completable.error(new InvalidRedirectUriException(e.getMessage()));
            }
        }

        if (hasIncorrectOrigins(domain)) {
            return Completable.error(new InvalidDomainException("CORS settings are invalid"));
        }

        if (domain.getTokenExchangeSettings() != null && !domain.getTokenExchangeSettings().isValid(maxDelegationDepthLimit)) {
            var depthMsg = domain.getTokenExchangeSettings().getMaxDelegationDepth() > maxDelegationDepthLimit
                    ? ": maxDelegationDepth must not exceed " + maxDelegationDepthLimit
                    : "";
            return Completable.error(new InvalidDomainException("Token Exchange settings are invalid" + depthMsg));
        }

        if (domain.getWebAuthnSettings() != null) {
            final String origin = domain.getWebAuthnSettings().getOrigin();
            if (origin == null || origin.isBlank()) {
                return Completable.error(new InvalidWebAuthnConfigurationException("Error: Invalid origin. Please provide a valid origin."));
            }

            final URI uri = UriBuilder.fromURIString(origin).build();
            final List<String> schemes = Arrays.asList("http", "https");

            if (!schemes.contains(uri.getScheme())) {
                throw new InvalidRequestUriException("origin : " + origin + " scheme is not https or http");
            }

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

    private boolean hasIncorrectOrigins(Domain domain) {
        var corsSettings = ofNullable(domain.getCorsSettings());
        if (corsSettings.isPresent()) {
            return corsSettings.get().isEnabled() && hasIncorrectOrigins(corsSettings);
        }
        return false;
    }

    private boolean hasIncorrectOrigins(Optional<CorsSettings> corsSettings) {
        var origins = corsSettings.map(CorsSettings::getAllowedOrigins);
        return origins.isEmpty() || origins.get().isEmpty() || origins.get().stream().anyMatch(origin -> {
            if (!origin.equals("*")) {
                try {
                    Pattern.compile(origin);
                } catch (Exception e) {
                    return true;
                }
            }
            return false;
        });
    }

    private void validatePostLogoutRedirectUris(Domain domain) throws Exception {
        if (domain.getOidc() != null && domain.getOidc().getPostLogoutRedirectUris() != null) {
            for (String logoutRedirectUri : domain.getOidc().getPostLogoutRedirectUris()) {
                try {
                    final URI uri = logoutRedirectUri.contains("*") ? new URI(logoutRedirectUri) : UriBuilder.fromURIString(logoutRedirectUri).build();

                    if (uri.getScheme() == null) {
                        throw new InvalidTargetUrlException("post_logout_redirect_uri : " + logoutRedirectUri + IS_MALFORMED);
                    }

                    final String host = isHttp(uri.getScheme()) ? uri.toURL().getHost() : uri.getHost();

                    //check localhost allowed
                    if (!domain.isRedirectUriLocalhostAllowed() && isHttp(uri.getScheme()) && UriBuilder.isLocalhost(host)) {
                        throw new InvalidTargetUrlException("localhost is forbidden");
                    }
                    //check http scheme
                    if (!domain.isRedirectUriUnsecuredHttpSchemeAllowed() && uri.getScheme().equalsIgnoreCase("http")) {
                        throw new InvalidTargetUrlException("Unsecured http scheme is forbidden");
                    }
                    //check wildcard
                    if (!domain.isRedirectUriWildcardAllowed() &&
                            (nonNull(uri.getPath()) && uri.getPath().contains("*") || nonNull(host) && host.contains("*"))) {
                        throw new InvalidTargetUrlException("Wildcard are forbidden");
                    }
                    // check fragment
                    if (uri.getFragment() != null) {
                        throw new InvalidTargetUrlException("post_logout_redirect_uri with fragment is forbidden");
                    }
                } catch (IllegalArgumentException | URISyntaxException ex) {
                    throw new InvalidTargetUrlException("post_logout_redirect_uri : " + logoutRedirectUri + IS_MALFORMED);
                }
            }
        }
    }


    private void validateRequestUris(Domain domain) throws Exception {
        if (domain.getOidc() != null && domain.getOidc().getRequestUris() != null) {
            for (String requestUri : domain.getOidc().getRequestUris()) {
                try {
                    final URI uri = requestUri.contains("*") ? new URI(requestUri) : UriBuilder.fromURIString(requestUri).build();

                    if (uri.getScheme() == null) {
                        throw new InvalidRequestUriException("request_uri : " + requestUri + IS_MALFORMED);
                    }

                    final String host = isHttp(uri.getScheme()) ? uri.toURL().getHost() : uri.getHost();

                    //check localhost allowed
                    if (!domain.isRedirectUriLocalhostAllowed() && isHttp(uri.getScheme()) && UriBuilder.isLocalhost(host)) {
                        throw new InvalidRequestUriException("localhost is forbidden");
                    }
                    //check http scheme
                    if (!domain.isRedirectUriUnsecuredHttpSchemeAllowed() && uri.getScheme().equalsIgnoreCase("http")) {
                        throw new InvalidRequestUriException("Unsecured http scheme is forbidden");
                    }
                    //check wildcard
                    if (!domain.isRedirectUriWildcardAllowed() &&
                            (nonNull(uri.getPath()) && uri.getPath().contains("*") || nonNull(host) && host.contains("*"))) {
                        throw new InvalidRequestUriException("Wildcard are forbidden");
                    }
                } catch (IllegalArgumentException | URISyntaxException ex) {
                    throw new InvalidRequestUriException("request_uri : " + requestUri + IS_MALFORMED);
                }
            }
        }
    }

    private Completable validateDomain(Domain domain, Environment environment) {

        // Get environment domain restrictions and validate all data are correctly defined.
        return domainValidator.validate(domain, environment.getDomainRestrictions())
                .andThen(listAll()
                        .collect(Collectors.toList())
                        .flatMapCompletable(domains -> virtualHostValidator.validateDomainVhosts(domain, domains)));
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
            }).toList();

            // The first one will be used as primary displayed entrypoint.
            vhosts.get(0).setOverrideEntrypoint(true);

            domain.setVhostMode(true);
            domain.setVhosts(vhosts);
        }
    }
}

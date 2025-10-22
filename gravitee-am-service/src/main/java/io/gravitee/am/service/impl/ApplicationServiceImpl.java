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
import io.gravitee.am.common.exception.oauth2.InvalidRequestUriException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSAMLSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.ApplicationTemplateManager;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.EmailTemplateService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.FormService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.InvalidRedirectUriException;
import io.gravitee.am.service.exception.InvalidRoleException;
import io.gravitee.am.service.exception.InvalidTargetUrlException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewApplication;
import io.gravitee.am.service.model.PatchApplication;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ApplicationAuditBuilder;
import io.gravitee.am.service.spring.application.ApplicationSecretConfig;
import io.gravitee.am.service.spring.application.SecretHashAlgorithm;
import io.gravitee.am.service.utils.CertificateTimeComparator;
import io.gravitee.am.service.utils.GrantTypeUtils;
import io.gravitee.am.service.validators.accountsettings.AccountSettingsValidator;
import io.gravitee.am.service.validators.claims.ApplicationTokenCustomClaimsValidator;
import io.gravitee.am.service.validators.claims.ApplicationTokenCustomClaimsValidator.ValidationResult;
import io.gravitee.am.service.validators.dynamicparams.ClientRedirectUrisValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static io.gravitee.am.common.oidc.ClientAuthenticationMethod.CLIENT_SECRET_JWT;
import static io.gravitee.am.common.web.UriBuilder.isHttp;
import static java.util.Objects.nonNull;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasLength;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ApplicationServiceImpl implements ApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationServiceImpl.class);
    private static final String AM_V2_VERSION = "AM_V2_VERSION";
    public static final String ERROR_ON_CREATE = "An error occurs while trying to create an application";
    public static final String ERROR_ON_PATCH = "An error occurs while trying to patch an application";
    public static final String LOCALHOST_IS_FORBIDDEN = "localhost is forbidden";
    public static final String UNSECURED_HTTP_SCHEME = "Unsecured http scheme is forbidden";
    public static final String WILDCARD_ARE_FORBIDDEN = "Wildcard are forbidden";
    public static final String IS_MALFORMED = " is malformed";

    @Lazy
    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationTemplateManager applicationTemplateManager;

    @Autowired
    private AccountSettingsValidator accountSettingsValidator;

    @Autowired
    private AuditService auditService;

    @Autowired
    private DomainReadService domainReadService;

    @Autowired
    private EventService eventService;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private FormService formService;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private ApplicationSecretConfig applicationSecretConfig;

    @Autowired
    private SecretService clientSecretService;

    @Autowired
    private ApplicationTokenCustomClaimsValidator customClaimsValidator;

    @Autowired
    private OAuthClientUniquenessValidator oAuthClientUniquenessValidator;

    private ClientRedirectUrisValidator clientRedirectUrisValidator = new ClientRedirectUrisValidator();

    @Override
    public Flowable<Application> findAll() {
        LOGGER.debug("Find applications");
        return applicationRepository.findAll();
    }

    @Override
    public Single<Page<Application>> findAll(int page, int size) {
        LOGGER.debug("Find applications");
        return applicationRepository.findAll(page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find applications", ex));
                });
    }

    @Override
    public Single<Page<Application>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("Find applications by domain {}", domain);
        return applicationRepository.findByDomain(domain, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications by domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find applications by domain %s", domain), ex));
                });
    }

    @Override
    public Single<Page<Application>> findByDomain(String domain, List<String> applicationIds, int page, int size) {
        LOGGER.debug("Find applications by domain {} and appIds {}", domain, applicationIds);
        return applicationRepository.findByDomain(domain, applicationIds, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications by domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find applications by domain %s", domain), ex));
                });
    }

    @Override
    public Single<Page<Application>> search(String domain, String query, int page, int size) {
        LOGGER.debug("Search applications with query {} for domain {}", query, domain);
        return applicationRepository.search(domain, query, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to search applications with query {} for domain {}", query, domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to search applications with query %s by domain %s", query, domain), ex));
                });
    }

    @Override
    public Single<Page<Application>> search(String domain, List<String> applicationIds, String query, int page, int size) {
        LOGGER.debug("Search applications with query {} for domain {} and appIds={}", query, domain, applicationIds);
        return applicationRepository.search(domain, applicationIds, query, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to search applications with query {} for domain {}", query, domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to search applications with query %s by domain %s", query, domain), ex));
                });
    }

    @Override
    public Flowable<Application> findByCertificate(String certificate) {
        LOGGER.debug("Find applications by certificate : {}", certificate);
        return applicationRepository.findByCertificate(certificate)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications by certificate", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find applications by certificate", ex));
                });
    }

    @Override
    public Flowable<Application> findByIdentityProvider(String identityProvider) {
        LOGGER.debug("Find applications by identity provider : {}", identityProvider);
        return applicationRepository.findByIdentityProvider(identityProvider)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications by identity provider", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find applications by identity provider", ex));
                });
    }

    @Override
    public Flowable<Application> findByFactor(String factor) {
        LOGGER.debug("Find applications by factor : {}", factor);
        return applicationRepository.findByFactor(factor)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications by factor", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find applications by factor", ex));
                });
    }

    @Override
    public Single<Set<Application>> findByDomainAndExtensionGrant(String domain, String extensionGrant) {
        LOGGER.debug("Find applications by domain {} and extension grant : {}", domain, extensionGrant);
        return applicationRepository.findByDomainAndExtensionGrant(domain, extensionGrant)
                .collect(() -> (Set<Application>) new HashSet(), Set::add) // TODO CHECK IF FLOWABLE is useful...
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications by extension grant", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find applications by extension grant", ex));
                });
    }

    @Override
    public Flowable<Application> findByIdIn(List<String> ids) {
        LOGGER.debug("Find applications by ids : {}", ids);
        return applicationRepository.findByIdIn(ids)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications by ids {}", ids, ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find applications by ids", ex));
                });
    }

    @Override
    public Maybe<Application> findById(String id) {
        LOGGER.debug("Find application by ID: {}", id);
        return applicationRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an application using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an application using its ID: %s", id), ex));
                });
    }

    @Override
    public Maybe<Application> findByDomainAndClientId(String domain, String clientId) {
        LOGGER.debug("Find application by domain: {} and client_id {}", domain, clientId);
        return applicationRepository.findByDomainAndClientId(domain, clientId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an application using its domain: {} and client_id : {}", domain, clientId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an application using its domain: %s, and client_id %s", domain, clientId), ex));
                });
    }

    @Override
    public Single<Application> create(Domain domain, NewApplication newApplication, User principal) {
        LOGGER.debug("Create a new application {} for domain {}", newApplication, domain);
        Application application = new Application();
        application.setId(RandomString.generate());
        application.setName(newApplication.getName());
        application.setDescription(newApplication.getDescription());
        application.setType(newApplication.getType());
        application.setDomain(domain.getId());
        application.setMetadata(newApplication.getMetadata());

        // apply default oauth 2.0 settings
        ApplicationSettings applicationSettings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setClientId(hasLength(newApplication.getClientId()) ? newApplication.getClientId() : null);
        oAuthSettings.setClientSecret(hasLength(newApplication.getClientSecret()) ? newApplication.getClientSecret() : null);
        oAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        oAuthSettings.setRedirectUris(newApplication.getRedirectUris());
        applicationSettings.setOauth(oAuthSettings);

        // apply default SAML 2.0 settings
        if (ApplicationType.SERVICE != application.getType() && !ObjectUtils.isEmpty(newApplication.getRedirectUris())) {
            try {
                final String url = newApplication.getRedirectUris().get(0);
                ApplicationSAMLSettings samlSettings = new ApplicationSAMLSettings();
                samlSettings.setEntityId(UriBuilder.fromHttpUrl(url).buildRootUrl());
                samlSettings.setAttributeConsumeServiceUrl(url);
                applicationSettings.setSaml(samlSettings);
            } catch (Exception ex) {
                // silent exception
                // redirect_uri can use custom URI (especially for mobile deep links)
                LOGGER.debug("An error has occurred when generating SAML attribute consume service url", ex);
            }
        }

        application.setSettings(applicationSettings);

        // apply templating
        applicationTemplateManager.apply(application);

        return create0(domain, application, principal)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error(ERROR_ON_CREATE, ex);
                    return Single.error(new TechnicalManagementException(ERROR_ON_CREATE, ex));
                });
    }

    @Override
    public Single<Application> create(Domain domain, Application application) {
        LOGGER.debug("Create a new application {} ", application);

        if (application.getDomain() == null || application.getDomain().trim().isEmpty()) {
            return Single.error(new InvalidClientMetadataException("No domain set on application"));
        }

        return create0(domain, application, null)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error(ERROR_ON_CREATE, ex);
                    return Single.error(new TechnicalManagementException(ERROR_ON_CREATE, ex));
                });
    }

    @Override
    public Single<Application> update(Application application) {
        LOGGER.debug("Update an application {} ", application);

        if (application.getDomain() == null || application.getDomain().trim().isEmpty()) {
            return Single.error(new InvalidClientMetadataException("No domain set on application"));
        }

        return applicationRepository.findById(application.getId())
                .switchIfEmpty(Single.error(new ApplicationNotFoundException(application.getId())))
                .flatMap(application1 -> innerUpdate(application1, application, null))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update an application", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update an application", ex));
                });
    }

    @Override
    public Single<Application> updateType(String domain, String id, ApplicationType type, User principal) {
        LOGGER.debug("Update application {} type to {} for domain {}", id, type, domain);

        return applicationRepository.findById(id)
                .switchIfEmpty(Single.error(new ApplicationNotFoundException(id)))
                .flatMap(existingApplication -> {
                    Application toPatch = new Application(existingApplication);
                    toPatch.setType(type);
                    applicationTemplateManager.changeType(toPatch);
                    return innerUpdate(existingApplication, toPatch, principal, true);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error(ERROR_ON_PATCH, ex);
                    return Single.error(new TechnicalManagementException(ERROR_ON_PATCH, ex));
                });
    }

    @Override
    public Single<Application> patch(Domain domain, String id, PatchApplication patchApplication, User principal, BiFunction<Domain, Application, Completable> revokeTokenProcessor) {
        LOGGER.debug("Patch an application {} for domain {}", id, domain);

        return applicationRepository.findById(id)
                .switchIfEmpty(Single.error(new ApplicationNotFoundException(id)))
                .flatMap(existingApplication -> {
                    Application toPatch = patchApplication.patch(existingApplication);
                    applicationTemplateManager.apply(toPatch);

                    // since we manage hashed value for client secret, we do not initialize the
                    // clientSecret during an update.
                    if (!hasClientSecret(existingApplication)
                            && toPatch.getSettings() != null && toPatch.getSettings().getOauth() != null) {
                        toPatch.getSettings().getOauth().setClientSecret(null);
                    }

                    ValidationResult claimValidation = customClaimsValidator.validate(toPatch);
                    if (claimValidation.isInvalid()) {
                        return Single.error(new InvalidParameterException("Invalid token claims: " + claimValidation.invalidClaims()));
                    }

                    final AccountSettings accountSettings = toPatch.getSettings().getAccount();
                    if (Boolean.FALSE.equals(accountSettingsValidator.validate(accountSettings))) {
                        return Single.error(new InvalidParameterException("Unexpected forgot password field"));
                    }
                    return innerUpdate(existingApplication, toPatch, principal)
                            .flatMap(app -> {
                                if (toPatch.isEnabled() != existingApplication.isEnabled() && !toPatch.isEnabled()) {
                                    return revokeTokenProcessor.apply(domain, app).onErrorComplete().toSingleDefault(app);
                                } else {
                                    return Single.just(app);
                                }
                            });
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error(ERROR_ON_PATCH, ex);
                    return Single.error(new TechnicalManagementException(ERROR_ON_PATCH, ex));
                });
    }

    private static boolean hasClientSecret(Application existingApplication) {
        return existingApplication.getSettings() != null && existingApplication.getSettings().getOauth() != null && existingApplication.getSettings().getOauth().getClientSecret() != null;
    }

    @Override
    public Completable delete(String id, User principal, Domain domain) {
        LOGGER.debug("Delete application {}", id);
        return applicationRepository.findById(id)
                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(id)))
                .flatMapCompletable(application -> {
                    // create event for sync process
                    Event event = new Event(Type.APPLICATION, new Payload(application.getId(), ReferenceType.DOMAIN, application.getDomain(), Action.DELETE));
                    return applicationRepository.delete(id)
                            .andThen(Completable.fromSingle(eventService.create(event, domain)))
                            // delete email templates
                            .andThen(emailTemplateService.findByClient(ReferenceType.DOMAIN, application.getDomain(), application.getId())
                                    .flatMapCompletable(email -> emailTemplateService.delete(email.getId()))
                            )
                            // delete form templates
                            .andThen(formService.findByDomainAndClient(application.getDomain(), application.getId())
                                    .flatMapCompletable(form -> formService.delete(application.getDomain(), form.getId()))
                            )
                            // delete memberships
                            .andThen(membershipService.findByReference(application.getId(), ReferenceType.APPLICATION)
                                    .flatMapCompletable(membership -> membershipService.delete(membership.getId()))
                            )
                            // trigger events to delete client secrets notifiers
                            .andThen(Flowable.fromIterable(application.getSecrets())
                                    .flatMapCompletable(applicationSecret -> Completable.fromSingle(
                                            eventService.create(new Event(Type.APPLICATION_SECRET, new Payload(applicationSecret.getId(), ReferenceType.APPLICATION, id, Action.DELETE))))
                                    )
                            )
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_DELETED).application(application)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).application(application).principal(principal).type(EventType.APPLICATION_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete application: {}", id, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete application: %s", id), ex));
                });
    }

    @Override
    public Single<Long> count() {
        LOGGER.debug("Count applications");
        return applicationRepository.count()
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to count applications", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to count applications", ex));
                });
    }

    @Override
    public Single<Long> countByDomain(String domainId) {
        LOGGER.debug("Count applications for domain {}", domainId);
        return applicationRepository.countByDomain(domainId)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to count applications for domain {}", domainId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to count applications for domain %s", domainId), ex));
                });
    }

    private Single<Application> create0(Domain domain, Application application, User principal) {
        // created and updated date
        application.setCreatedAt(new Date());
        application.setUpdatedAt(application.getCreatedAt());

        var secretSettings = this.applicationSecretConfig.toSecretSettings();
        application.setSecretSettings(List.of(secretSettings));

        // here the client_secret has been generated, we can generate the HashValue here
        // as we do not want to store the clear text value for the client Secret
        // we keep the value and force the value into OAuth setting to null
        // in order to restore it after the creation
        var applicationSettings = application.getSettings();
        final var rawSecret = applicationSettings.getOauth().getClientSecret();
        if (rawSecret != null) {
            // PUBLIC client doesn't need to have secret, so we have to test it before generated the hash
            applicationSettings.getOauth().setClientSecret(null);
            var clientSecret = this.clientSecretService.generateClientSecret("Default", rawSecret, secretSettings, domain.getSecretExpirationSettings(), applicationSettings.getSecretExpirationSettings());
            application.setSecrets(List.of(clientSecret));
        }

        // check uniqueness
        return oAuthClientUniquenessValidator.checkClientIdUniqueness(domain.getId(), application.clientId())
                // validate application metadata
                .andThen(validateApplicationMetadata(application))
                .flatMap(this::validateApplicationAuthMethod)
                // set default certificate
                .flatMap(this::setDefaultCertificate)
                // create the application
                .flatMap(applicationRepository::create).map(app -> {
                    if (app.getSettings() != null && app.getSettings().getOauth() != null) {
                        app.getSettings().getOauth().setClientSecret(rawSecret);
                    } else {
                        LOGGER.warn("Unable to restore the clientSecret into the application settings, settings are null.");
                    }
                    return app;
                })
                // create the owner
                .flatMap(application1 -> {
                    if (principal == null || principal.getAdditionalInformation() == null || !hasText((String) principal.getAdditionalInformation().get(Claims.ORGANIZATION))) {
                        // There is no principal or we can not find the organization the user is attached to. Can't assign role.
                        return Single.just(application1);
                    }

                    return roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)
                            .switchIfEmpty(Single.error(new InvalidRoleException("Cannot assign owner to the application, owner role does not exist")))
                            .flatMap(role -> {
                                Membership membership = new Membership();
                                membership.setDomain(application1.getDomain());
                                membership.setMemberId(principal.getId());
                                membership.setMemberType(MemberType.USER);
                                membership.setReferenceId(application1.getId());
                                membership.setReferenceType(ReferenceType.APPLICATION);
                                membership.setRoleId(role.getId());
                                return membershipService.addOrUpdate((String) principal.getAdditionalInformation().get(Claims.ORGANIZATION), membership)
                                        .map(updatedMembership -> application1);
                            });
                })
                // create event for sync process
                .flatMap(application1 -> {
                    Event event = new Event(Type.APPLICATION, new Payload(application.getId(), ReferenceType.DOMAIN, application.getDomain(), Action.CREATE));
                    return eventService.create(event, domain).flatMap(domain1 -> Single.just(application1));
                })
                .doOnSuccess(application1 -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_CREATED).application(application1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).reference(Reference.domain(application.getDomain())).principal(principal).type(EventType.APPLICATION_CREATED).throwable(throwable)));
    }

    private Single<Application> innerUpdate(Application currentApplication, Application applicationToUpdate, User principal) {
        return innerUpdate(currentApplication, applicationToUpdate, principal, false);
    }

    private Single<Application> innerUpdate(Application currentApplication, Application applicationToUpdate, User principal, boolean fromUpdateType) {
        // updated date
        applicationToUpdate.setUpdatedAt(new Date());

        // validate application metadata
        return validateApplicationMetadata(applicationToUpdate, fromUpdateType)
                // validate identity providers
                .flatMap(this::validateApplicationIdentityProviders)
                .flatMap(appToValidate -> validateApplicationAuthMethodUpdate(appToValidate, currentApplication))
                // update application
                .flatMap(applicationRepository::update)
                // create event for sync process
                .flatMap(application1 -> {
                    Event event = new Event(Type.APPLICATION, new Payload(application1.getId(), ReferenceType.DOMAIN, application1.getDomain(), Action.UPDATE));
                    return eventService.create(event).flatMap(domain1 -> Single.just(application1));
                })
                .doOnSuccess(application -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_UPDATED).oldValue(currentApplication).application(application)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).reference(Reference.domain(currentApplication.getDomain())).principal(principal).type(EventType.APPLICATION_UPDATED).throwable(throwable)));
    }

    /**
     * Set default domain certificate for the application
     *
     * @param application the application to create
     * @return the application with the certificate
     */
    private Single<Application> setDefaultCertificate(Application application) {
        // certificate might have been set via DCR, continue
        if (application.getCertificate() != null) {
            return Single.just(application);
        }

        return certificateService
                .findByDomain(application.getDomain())
                .toList()
                .map(certificates -> {
                    if (certificates == null || certificates.isEmpty()) {
                        return application;
                    }
                    Certificate defaultCertificate = certificates
                            .stream()
                            .filter(Certificate::isSystem)
                            .sorted(new CertificateTimeComparator())
                            .findFirst()
                            .or(() ->
                                    // legacy way to retrieve default certificate before we introduce the system flag
                                    // keep it for backward compatibility in case of issue with the SystemCertificateUpgrader
                                    certificates
                                            .stream()
                                            .filter(certificate -> "Default".equals(certificate.getName()))
                                            .findFirst()
                            )
                            .orElse(certificates.get(0));

                    application.setCertificate(defaultCertificate.getId());
                    return application;
                });
    }

    private Single<Application> validateApplicationIdentityProviders(Application application) {
        if (application.getIdentityProviders() == null || application.getIdentityProviders().isEmpty()) {
            return Single.just(application);
        }
        return Observable.fromIterable(application.getIdentityProviders())
                .flatMapSingle(appIdP -> identityProviderService.findById(appIdP.getIdentity())
                        .map(__ -> Optional.of(appIdP))
                        .defaultIfEmpty(Optional.empty())
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toSingle())
                .toList()
                .map(appIdps -> {
                    application.setIdentityProviders(new TreeSet<>(appIdps));
                    return application;
                });
    }

    private Single<Application> validateApplicationAuthMethodUpdate(Application toUpdateApp, Application existingApplication) {
        if (isNotUsingClientSecretJwt(existingApplication) && clientSecretJwtWithHashedSecret(toUpdateApp)) {
            LOGGER.debug("tokenEndpointAuthMethod can't be {} if the client secret is hashed", CLIENT_SECRET_JWT);
            return Single.error(new InvalidClientMetadataException("client_secret_jwt can't be used by this application"));
        }

        return Single.just(toUpdateApp);
    }

    private static boolean clientSecretJwtWithHashedSecret(Application toUpdateApp) {
        return isUsingClientSecretJwt(toUpdateApp)
                && !isEmpty(toUpdateApp.getSecretSettings()) && toUpdateApp.getSecretSettings().stream().anyMatch(settings -> !SecretHashAlgorithm.NONE.name().equals(settings.getAlgorithm()));
    }

    private static boolean isUsingClientSecretJwt(Application app) {
        return app.getSettings() != null && app.getSettings().getOauth() != null && CLIENT_SECRET_JWT.equals(app.getSettings().getOauth().getTokenEndpointAuthMethod());
    }

    private static boolean isNotUsingClientSecretJwt(Application app) {
        return app.getSettings() != null && app.getSettings().getOauth() != null && !CLIENT_SECRET_JWT.equals(app.getSettings().getOauth().getTokenEndpointAuthMethod());
    }

    private Single<Application> validateApplicationAuthMethod(Application app) {
        if (clientSecretJwtWithHashedSecret(app)) {
            LOGGER.debug("tokenEndpointAuthMethod can't be {} if the client secret is hashed", CLIENT_SECRET_JWT);
            return Single.error(new InvalidClientMetadataException("client_secret_jwt can't be used by this application"));
        }

        return Single.just(app);
    }

    /**
     * <pre>
     * This function will return an error if :
     * We try to enable Dynamic Client Registration on client side while it is not enabled on domain.
     * The redirect_uris do not respect domain conditions (localhost, scheme and wildcard)
     * </pre>
     *
     * @param application application to check
     * @return a client only if every condition are respected.
     */
    private Single<Application> validateApplicationMetadata(Application application) {
        return validateApplicationMetadata(application, false);
    }

    /**
     * <pre>
     * This function will return an error if :
     * We try to enable Dynamic Client Registration on client side while it is not enabled on domain.
     * The redirect_uris do not respect domain conditions (localhost, scheme and wildcard)
     * </pre>
     *
     * @param application    application to check
     * @param updateTypeOnly does the method call comes from the application type update ? (if true, redirect_uri validation is skipped)
     * @return a client only if every condition are respected.
     */
    private Single<Application> validateApplicationMetadata(Application application, boolean updateTypeOnly) {
        // do nothing if application has no settings
        if (application.getSettings() == null) {
            return Single.just(application);
        }
        if (application.getSettings().getOauth() == null) {
            return Single.just(application);
        }
        return GrantTypeUtils.validateGrantTypes(application)
                .flatMap(app -> this.validateRedirectUris(app, updateTypeOnly))
                .flatMap(this::validateScopes)
                .flatMap(this::validateTokenEndpointAuthMethod)
                .flatMap(this::validateTlsClientAuth)
                .flatMap(this::validatePostLogoutRedirectUris)
                .flatMap(this::validateRequestUris);
    }

    private Single<Application> validateRedirectUris(Application application, boolean updateTypeOnly) {
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();

        return domainReadService.findById(application.getDomain())
                .switchIfEmpty(Single.error(new DomainNotFoundException(application.getDomain())))
                .flatMap(domain -> {
                    //check redirect_uri
                    if (GrantTypeUtils.isRedirectUriRequired(oAuthSettings.getGrantTypes()) && isEmpty(oAuthSettings.getRedirectUris())) {
                        // if client type is from V2, it means that the application has been created from an old client without redirect_uri control (via the upgrader)
                        // skip for now since it will be set in the next update operation
                        if (AM_V2_VERSION.equals(oAuthSettings.getSoftwareVersion())) {
                            oAuthSettings.setSoftwareVersion(null);
                        } else {
                            return Single.error(new InvalidRedirectUriException());
                        }
                    }
                    //check redirect_uri content
                    if (!CollectionUtils.isEmpty(oAuthSettings.getRedirectUris())) {
                        for (String redirectUri : oAuthSettings.getRedirectUris()) {
                            try {
                                URI uri = redirectUri.contains("*") ? new URI(redirectUri) : UriBuilder.fromURIString(redirectUri).build();

                                if (uri.getScheme() == null) {
                                    return Single.error(new InvalidRedirectUriException("redirect_uri : " + redirectUri + IS_MALFORMED));
                                }

                                final String host = isHttp(uri.getScheme()) ? uri.toURL().getHost() : uri.getHost();

                                //check localhost allowed
                                if (!domain.isRedirectUriLocalhostAllowed() && isHttp(uri.getScheme()) && UriBuilder.isLocalhost(host)) {
                                    return Single.error(new InvalidRedirectUriException(LOCALHOST_IS_FORBIDDEN));
                                }
                                //check http scheme
                                if (!domain.isRedirectUriUnsecuredHttpSchemeAllowed() && uri.getScheme().equalsIgnoreCase("http")) {
                                    return Single.error(new InvalidRedirectUriException(UNSECURED_HTTP_SCHEME));
                                }
                                //check wildcard
                                if (!domain.isRedirectUriWildcardAllowed() &&
                                        (nonNull(uri.getPath()) && uri.getPath().contains("*") || nonNull(host) && host.contains("*"))) {
                                    return Single.error(new InvalidRedirectUriException(WILDCARD_ARE_FORBIDDEN));
                                }
                                // check fragment
                                if (uri.getFragment() != null) {
                                    return Single.error(new InvalidRedirectUriException("redirect_uri with fragment is forbidden"));
                                }
                            } catch (IllegalArgumentException | URISyntaxException ex) {
                                return Single.error(new InvalidRedirectUriException("redirect_uri : " + redirectUri + IS_MALFORMED));
                            }
                        }
                        if(domain.isRedirectUriExpressionLanguageEnabled()){
                            if(!clientRedirectUrisValidator.validateRedirectUris(oAuthSettings.getRedirectUris())) {
                                return Single.error(new InvalidRedirectUriException("Only one redirect URI with the same hostname and path is allowed."));
                            }
                        }
                    } else if (application.getType() != ApplicationType.SERVICE && !updateTypeOnly) {
                        return Single.error(new InvalidRedirectUriException("At least one redirect_uri is required"));
                    }
                    return Single.just(application);
                });
    }

    private Single<Application> validateScopes(Application application) {
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        // check scope approvals and default scopes coherency
        List<String> scopes = oAuthSettings.getScopeSettings() != null ? oAuthSettings.getScopeSettings().stream().map(ApplicationScopeSettings::getScope).collect(Collectors.toList()) : new ArrayList<>();
        List<String> defaultScopes = oAuthSettings.getScopeSettings() != null ? oAuthSettings.getScopeSettings().stream().filter(ApplicationScopeSettings::isDefaultScope).map(ApplicationScopeSettings::getScope).collect(Collectors.toList()) : new ArrayList<>();
        Set<String> scopeApprovals = oAuthSettings.getScopeSettings() != null ? oAuthSettings.getScopeSettings().stream().filter(s -> s.getScopeApproval() != null).map(ApplicationScopeSettings::getScope).collect(Collectors.toSet()) : new HashSet<>();
        if (!scopes.containsAll(defaultScopes)) {
            return Single.error(new InvalidClientMetadataException("non valid default scopes"));
        }
        if (!scopes.containsAll(scopeApprovals)) {
            return Single.error(new InvalidClientMetadataException("non valid scope approvals"));
        }
        // check scopes against domain scopes
        return scopeService.validateScope(application.getDomain(), scopes)
                .flatMap(isValid -> {
                    if (!isValid) {
                        return Single.error(new InvalidClientMetadataException("non valid scopes"));
                    }
                    return Single.just(application);
                });
    }

    private Single<Application> validateTokenEndpointAuthMethod(Application application) {
        ApplicationOAuthSettings oauthSettings = application.getSettings().getOauth();
        String tokenEndpointAuthMethod = oauthSettings.getTokenEndpointAuthMethod();
        if ((ApplicationType.SERVICE.equals(application.getType()) || (oauthSettings.getGrantTypes() != null && oauthSettings.getGrantTypes().contains(GrantType.CLIENT_CREDENTIALS)))
                && (tokenEndpointAuthMethod != null && ClientAuthenticationMethod.NONE.equals(tokenEndpointAuthMethod))) {
            return Single.error(new InvalidClientMetadataException("Invalid token_endpoint_auth_method for service application (client_credentials grant type)"));
        }
        return Single.just(application);
    }

    /**
     * A client using the "tls_client_auth" authentication method MUST use exactly one of the
     * below metadata parameters to indicate the certificate subject value that the authorization server is
     * to expect when authenticating the respective client.
     */
    private Single<Application> validateTlsClientAuth(Application application) {
        ApplicationOAuthSettings settings = application.getSettings().getOauth();
        if (settings.getTokenEndpointAuthMethod() != null && ClientAuthenticationMethod.TLS_CLIENT_AUTH.equalsIgnoreCase(settings.getTokenEndpointAuthMethod())) {
            if (checkTlsClientAuth(settings)) {
                return Single.error(new InvalidClientMetadataException("Missing TLS parameter for tls_client_auth."));
            }
            if (checkOneClientAuth(settings)) {
                return Single.error(new InvalidClientMetadataException("The tls_client_auth must use exactly one of the TLS parameters."));
            }
        }

        return Single.just(application);
    }

    private static boolean checkOneClientAuth(ApplicationOAuthSettings settings) {
        return (settings.getTlsClientAuthSubjectDn() != null && !settings.getTlsClientAuthSubjectDn().isEmpty() && (
                (settings.getTlsClientAuthSanDns() != null && !settings.getTlsClientAuthSanDns().isEmpty()) ||
                        (settings.getTlsClientAuthSanEmail() != null && !settings.getTlsClientAuthSanEmail().isEmpty()) ||
                        (settings.getTlsClientAuthSanIp() != null && !settings.getTlsClientAuthSanIp().isEmpty()) ||
                        (settings.getTlsClientAuthSanUri() != null && !settings.getTlsClientAuthSanUri().isEmpty())))
                || (settings.getTlsClientAuthSanDns() != null && !settings.getTlsClientAuthSanDns().isEmpty() && (
                (settings.getTlsClientAuthSubjectDn() != null && !settings.getTlsClientAuthSubjectDn().isEmpty()) ||
                        (settings.getTlsClientAuthSanEmail() != null && !settings.getTlsClientAuthSanEmail().isEmpty()) ||
                        (settings.getTlsClientAuthSanIp() != null && !settings.getTlsClientAuthSanIp().isEmpty()) ||
                        (settings.getTlsClientAuthSanUri() != null && !settings.getTlsClientAuthSanUri().isEmpty())))
                || (settings.getTlsClientAuthSanIp() != null && !settings.getTlsClientAuthSanIp().isEmpty() && (
                (settings.getTlsClientAuthSubjectDn() != null && !settings.getTlsClientAuthSubjectDn().isEmpty()) ||
                        (settings.getTlsClientAuthSanDns() != null && !settings.getTlsClientAuthSanDns().isEmpty()) ||
                        (settings.getTlsClientAuthSanEmail() != null && !settings.getTlsClientAuthSanEmail().isEmpty()) ||
                        (settings.getTlsClientAuthSanUri() != null && !settings.getTlsClientAuthSanUri().isEmpty())))
                || (settings.getTlsClientAuthSanEmail() != null && !settings.getTlsClientAuthSanEmail().isEmpty() && (
                (settings.getTlsClientAuthSubjectDn() != null && !settings.getTlsClientAuthSubjectDn().isEmpty()) ||
                        (settings.getTlsClientAuthSanDns() != null && !settings.getTlsClientAuthSanDns().isEmpty()) ||
                        (settings.getTlsClientAuthSanIp() != null && !settings.getTlsClientAuthSanIp().isEmpty()) ||
                        (settings.getTlsClientAuthSanUri() != null && !settings.getTlsClientAuthSanUri().isEmpty())))
                || (settings.getTlsClientAuthSanUri() != null && !settings.getTlsClientAuthSanUri().isEmpty() && (
                (settings.getTlsClientAuthSubjectDn() != null && !settings.getTlsClientAuthSubjectDn().isEmpty()) ||
                        (settings.getTlsClientAuthSanDns() != null && !settings.getTlsClientAuthSanDns().isEmpty()) ||
                        (settings.getTlsClientAuthSanIp() != null && !settings.getTlsClientAuthSanIp().isEmpty()) ||
                        (settings.getTlsClientAuthSanEmail() != null && !settings.getTlsClientAuthSanEmail().isEmpty())));
    }

    private static boolean checkTlsClientAuth(ApplicationOAuthSettings settings) {
        return (settings.getTlsClientAuthSubjectDn() == null || settings.getTlsClientAuthSubjectDn().isEmpty()) &&
                (settings.getTlsClientAuthSanDns() == null || settings.getTlsClientAuthSanDns().isEmpty()) &&
                (settings.getTlsClientAuthSanIp() == null || settings.getTlsClientAuthSanIp().isEmpty()) &&
                (settings.getTlsClientAuthSanEmail() == null || settings.getTlsClientAuthSanEmail().isEmpty()) &&
                (settings.getTlsClientAuthSanUri() == null || settings.getTlsClientAuthSanUri().isEmpty());
    }

    private Single<Application> validatePostLogoutRedirectUris(Application application) {
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();

        return domainReadService.findById(application.getDomain())
                .switchIfEmpty(Single.error(new DomainNotFoundException(application.getDomain())))
                .flatMap(domain -> {
                    if (oAuthSettings.getPostLogoutRedirectUris() != null) {
                        for (String logoutRedirectUri : oAuthSettings.getPostLogoutRedirectUris()) {
                            try {
                                final URI uri = logoutRedirectUri.contains("*") ? new URI(logoutRedirectUri) : UriBuilder.fromURIString(logoutRedirectUri).build();

                                if (uri.getScheme() == null) {
                                    return Single.error(new InvalidTargetUrlException("post_logout_redirect_uri : " + logoutRedirectUri + IS_MALFORMED));
                                }

                                final String host = isHttp(uri.getScheme()) ? uri.toURL().getHost() : uri.getHost();

                                //check localhost allowed
                                if (!domain.isRedirectUriLocalhostAllowed() && isHttp(uri.getScheme()) && UriBuilder.isLocalhost(host)) {
                                    return Single.error(new InvalidTargetUrlException(LOCALHOST_IS_FORBIDDEN));
                                }
                                //check http scheme
                                if (!domain.isRedirectUriUnsecuredHttpSchemeAllowed() && uri.getScheme().equalsIgnoreCase("http")) {
                                    return Single.error(new InvalidTargetUrlException(UNSECURED_HTTP_SCHEME));
                                }
                                //check wildcard
                                if (!domain.isRedirectUriWildcardAllowed() &&
                                        (nonNull(uri.getPath()) && uri.getPath().contains("*") || nonNull(host) && host.contains("*"))) {
                                    return Single.error(new InvalidTargetUrlException(WILDCARD_ARE_FORBIDDEN));
                                }
                                // check fragment
                                if (uri.getFragment() != null) {
                                    return Single.error(new InvalidTargetUrlException("post_logout_redirect_uri with fragment is forbidden"));
                                }
                            } catch (IllegalArgumentException | URISyntaxException ex) {
                                return Single.error(new InvalidTargetUrlException("post_logout_redirect_uri : " + logoutRedirectUri + IS_MALFORMED));
                            }
                        }
                    }
                    return Single.just(application);
                });
    }

    private Single<Application> validateRequestUris(Application application) {
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();

        return domainReadService.findById(application.getDomain())
                .switchIfEmpty(Single.error(new DomainNotFoundException(application.getDomain())))
                .flatMap(domain -> {
                    if (oAuthSettings.getRequestUris() != null) {
                        for (String requestUri : oAuthSettings.getRequestUris()) {
                            try {
                                final URI uri = requestUri.contains("*") ? new URI(requestUri) : UriBuilder.fromURIString(requestUri).build();

                                if (uri.getScheme() == null) {
                                    return Single.error(new InvalidRequestUriException("request_uri : " + requestUri + IS_MALFORMED));
                                }

                                final String host = isHttp(uri.getScheme()) ? uri.toURL().getHost() : uri.getHost();

                                //check localhost allowed
                                if (!domain.isRedirectUriLocalhostAllowed() && isHttp(uri.getScheme()) && UriBuilder.isLocalhost(host)) {
                                    return Single.error(new InvalidRequestUriException(LOCALHOST_IS_FORBIDDEN));
                                }
                                //check http scheme
                                if (!domain.isRedirectUriUnsecuredHttpSchemeAllowed() && uri.getScheme().equalsIgnoreCase("http")) {
                                    return Single.error(new InvalidRequestUriException(UNSECURED_HTTP_SCHEME));
                                }
                                //check wildcard
                                if (!domain.isRedirectUriWildcardAllowed() &&
                                        (nonNull(uri.getPath()) && uri.getPath().contains("*") || nonNull(host) && host.contains("*"))) {
                                    return Single.error(new InvalidRequestUriException(WILDCARD_ARE_FORBIDDEN));
                                }
                            } catch (IllegalArgumentException | URISyntaxException ex) {
                                return Single.error(new InvalidRequestUriException("request_uri : " + requestUri + IS_MALFORMED));
                            }
                        }
                    }
                    return Single.just(application);
                });
    }
}

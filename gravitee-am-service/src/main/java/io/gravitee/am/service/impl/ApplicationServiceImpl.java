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
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewApplication;
import io.gravitee.am.service.model.PatchApplication;
import io.gravitee.am.service.model.TopApplication;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ApplicationAuditBuilder;
import io.gravitee.am.service.utils.GrantTypeUtils;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ApplicationServiceImpl implements ApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationServiceImpl.class);

    @Lazy
    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationTemplateManager applicationTemplateManager;

    @Autowired
    private AuditService auditService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private EventService eventService;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private FormService formService;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MembershipService membershipService;

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
    public Single<Set<Application>> findByCertificate(String certificate) {
        LOGGER.debug("Find applications by certificate : {}", certificate);
        return applicationRepository.findByCertificate(certificate)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications by certificate", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find applications by certificate", ex));
                });
    }

    @Override
    public Single<Set<Application>> findByIdentityProvider(String identityProvider) {
        LOGGER.debug("Find applications by identity provider : {}", identityProvider);
        return applicationRepository.findByIdentityProvider(identityProvider)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications by identity provider", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find applications by identity provider", ex));
                });
    }

    @Override
    public Single<Set<Application>> findByFactor(String factor) {
        LOGGER.debug("Find applications by factor : {}", factor);
        return applicationRepository.findByFactor(factor)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications by factor", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find applications by factor", ex));
                });
    }

    @Override
    public Single<Set<Application>> findByDomainAndExtensionGrant(String domain, String extensionGrant) {
        LOGGER.debug("Find applications by domain {} and extension grant : {}", domain, extensionGrant);
        return applicationRepository.findByDomainAndExtensionGrant(domain, extensionGrant)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find applications by extension grant", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find applications by extension grant", ex));
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
                            String.format("An error occurs while trying to find an application using its domain: %s, and client_id", domain, clientId), ex));
                });
    }

    @Override
    public Single<Application> create(String domain, NewApplication newApplication, User principal) {
        LOGGER.debug("Create a new application {} for domain {}", newApplication, domain);
        Application application = new Application();
        application.setId(RandomString.generate());
        application.setName(newApplication.getName());
        application.setType(newApplication.getType());
        application.setDomain(domain);

        // apply default oauth 2.0 settings
        ApplicationSettings applicationSettings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setClientId(newApplication.getClientId());
        oAuthSettings.setClientSecret(newApplication.getClientSecret());
        oAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        oAuthSettings.setRedirectUris(newApplication.getRedirectUris());
        applicationSettings.setOauth(oAuthSettings);
        application.setSettings(applicationSettings);

        // apply templating
        applicationTemplateManager.apply(application);

        return create0(domain, application, principal)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create an application", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create an application", ex));
                });
    }

    @Override
    public Single<Application> create(Application application) {
        LOGGER.debug("Create a new application {} ", application);

        if (application.getDomain() == null || application.getDomain().trim().isEmpty()) {
            return Single.error(new InvalidClientMetadataException("No domain set on application"));
        }

        return create0(application.getDomain(), application, null)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create an application", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create an application", ex));
                });
    }

    @Override
    public Single<Application> update(Application application) {
        LOGGER.debug("Update an application {} ", application);

        if (application.getDomain() == null || application.getDomain().trim().isEmpty()) {
            return Single.error(new InvalidClientMetadataException("No domain set on application"));
        }

        return applicationRepository.findById(application.getId())
                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application.getId())))
                .flatMapSingle(application1 -> update0(application1.getDomain(), application1, application, null))
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
                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(id)))
                .flatMapSingle(existingApplication -> {
                    Application toPatch = new Application(existingApplication);
                    toPatch.setType(type);
                    applicationTemplateManager.changeType(toPatch);
                    return update0(domain, existingApplication, toPatch, principal);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to patch an application", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to patch an application", ex));
                });
    }

    @Override
    public Single<Application> patch(String domain, String id, PatchApplication patchApplication, User principal) {
        LOGGER.debug("Patch an application {} for domain {}", id, domain);

        return applicationRepository.findById(id)
                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(id)))
                .flatMapSingle(existingApplication -> {
                    Application toPatch = patchApplication.patch(existingApplication);
                    applicationTemplateManager.apply(toPatch);
                    return update0(domain, existingApplication, toPatch, principal);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to patch an application", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to patch an application", ex));
                });
    }

    @Override
    public Single<Application> renewClientSecret(String domain, String id, User principal) {
        LOGGER.debug("Renew client secret for application {} and domain {}", id, domain);
        return applicationRepository.findById(id)
                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(id)))
                .flatMapSingle(application -> {
                    // check application
                    if (application.getSettings() == null) {
                        return Single.error(new IllegalStateException("Application settings is undefined"));
                    }
                    if (application.getSettings().getOauth() == null) {
                        return Single.error(new IllegalStateException("Application OAuth 2.0 settings is undefined"));
                    }
                    // update secret
                    application.getSettings().getOauth().setClientSecret(SecureRandomString.generate());
                    application.setUpdatedAt(new Date());
                    return applicationRepository.update(application);
                })
                // create event for sync process
                .flatMap(application1 -> {
                    Event event = new Event(Type.APPLICATION, new Payload(application1.getId(), ReferenceType.DOMAIN, application1.getDomain(), Action.UPDATE));
                    return eventService.create(event).flatMap(domain1 -> Single.just(application1));
                })
                .doOnSuccess(updatedApplication -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_CLIENT_SECRET_RENEWED).application(updatedApplication)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_CLIENT_SECRET_RENEWED).throwable(throwable)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to renew client secret for application {} and domain {}", id, domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to renew client secret for application %s and domain %s", id, domain), ex));
                });
    }

    @Override
    public Completable delete(String id, User principal) {
        LOGGER.debug("Delete application {}", id);
        return applicationRepository.findById(id)
                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(id)))
                .flatMapCompletable(application -> {
                    // create event for sync process
                    Event event = new Event(Type.APPLICATION, new Payload(application.getId(), ReferenceType.DOMAIN, application.getDomain(), Action.DELETE));
                    return applicationRepository.delete(id)
                            .andThen(eventService.create(event).toCompletable())
                            // delete email templates
                            .andThen(emailTemplateService.findByClient(ReferenceType.DOMAIN, application.getDomain(), application.getId())
                                    .flatMapCompletable(emails -> {
                                        List<Completable> deleteEmailsCompletable = emails.stream().map(e -> emailTemplateService.delete(e.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteEmailsCompletable);
                                    })
                            )
                            // delete form templates
                            .andThen(formService.findByDomainAndClient(application.getDomain(), application.getId())
                                    .flatMapCompletable(forms -> {
                                        List<Completable> deleteFormsCompletable = forms.stream().map(f -> formService.delete(application.getDomain(), f.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteFormsCompletable);
                                    })
                            )
                            // delete memberships
                            .andThen(membershipService.findByReference(application.getId(), ReferenceType.APPLICATION)
                                    .flatMapCompletable(memberships -> {
                                        List<Completable> deleteFormsCompletable = memberships.stream().map(m -> membershipService.delete(m.getId())).collect(Collectors.toList());
                                        return Completable.concat(deleteFormsCompletable);
                                    })
                            )
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_DELETED).application(application)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_DELETED).throwable(throwable)));
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
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to count applications"), ex));
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

    @Override
    public Single<Set<TopApplication>> findTopApplications() {
        LOGGER.debug("Find top applications");
        return applicationRepository.findAll(0, Integer.MAX_VALUE)
                .flatMapObservable(pagedApplications -> Observable.fromIterable(pagedApplications.getData()))
                .flatMapSingle(application -> tokenService.findTotalTokensByApplication(application)
                        .map(totalToken -> {
                            TopApplication topApplication = new TopApplication();
                            topApplication.setApplication(application);
                            topApplication.setAccessTokens(totalToken.getTotalAccessTokens());
                            return topApplication;
                        })
                )
                .toList()
                .map(topApplications -> topApplications.stream().filter(topClient -> topClient.getAccessTokens() > 0).collect(Collectors.toSet()))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find top applications", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find top applications", ex));
                });
    }

    @Override
    public Single<Set<TopApplication>> findTopApplicationsByDomain(String domain) {
        LOGGER.debug("Find top applications for domain: {}", domain);
        return applicationRepository.findByDomain(domain, 0, Integer.MAX_VALUE)
                .flatMapObservable(pagedApplications -> Observable.fromIterable(pagedApplications.getData()))
                .flatMapSingle(application -> tokenService.findTotalTokensByApplication(application)
                        .map(totalToken -> {
                            TopApplication topApplication = new TopApplication();
                            topApplication.setApplication(application);
                            topApplication.setAccessTokens(totalToken.getTotalAccessTokens());
                            return topApplication;
                        })
                )
                .toList()
                .map(topApplications -> topApplications.stream().filter(topClient -> topClient.getAccessTokens() > 0).collect(Collectors.toSet()))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find top applications for domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find top applications for domain %s", domain), ex));
                });
    }

    private Single<Application> create0(String domain, Application application, User principal) {
        // created and updated date
        application.setCreatedAt(new Date());
        application.setUpdatedAt(application.getCreatedAt());

        // check uniqueness
        return checkApplicationUniqueness(domain, application)
                // validate application metadata
                .andThen(validateApplicationMetadata(application))
                // create the application
                .flatMap(applicationRepository::create)
                // create the owner
                .flatMap(application1 -> {
                    if (principal == null) {
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
                                // FIXME: propagate organizationId.
                                return membershipService.addOrUpdate("DEFAULT", membership)
                                        .map(__ -> domain);
                            });
                })
                // create event for sync process
                .flatMap(application1 -> {
                    Event event = new Event(Type.APPLICATION, new Payload(application.getId(), ReferenceType.DOMAIN, application.getDomain(), Action.CREATE));
                    return eventService.create(event).flatMap(domain1 -> Single.just(application));
                })
                .doOnSuccess(application1 -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_CREATED).application(application1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_CREATED).throwable(throwable)));
    }

    private Single<Application> update0(String domain, Application currentApplication, Application applicationToUpdate, User principal) {
        // updated date
        applicationToUpdate.setUpdatedAt(new Date());

        // validate application metadata
        return validateApplicationMetadata(applicationToUpdate)
                // validate identity providers
                .flatMap(this::validateApplicationIdentityProviders)
                // update application
                .flatMap(applicationRepository::update)
                // create event for sync process
                .flatMap(application1 -> {
                    Event event = new Event(Type.APPLICATION, new Payload(application1.getId(), ReferenceType.DOMAIN, application1.getDomain(), Action.UPDATE));
                    return eventService.create(event).flatMap(domain1 -> Single.just(application1));
                })
                .doOnSuccess(application -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_UPDATED).oldValue(currentApplication).application(application)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_UPDATED).throwable(throwable)));
    }

    private Completable checkApplicationUniqueness(String domain, Application application) {
        final String clientId = application.getSettings() != null && application.getSettings().getOauth() != null ? application.getSettings().getOauth().getClientId() : null;
        return findByDomainAndClientId(domain, clientId)
                .isEmpty()
                .flatMapCompletable(isEmpty -> {
                    if (!isEmpty) {
                        return Completable.error(new ApplicationAlreadyExistsException(clientId, domain));
                    }
                    return Completable.complete();
                });
    }

    private Single<Application> validateApplicationIdentityProviders(Application application) {
        if (application.getIdentities() == null || application.getIdentities().isEmpty()) {
            return Single.just(application);
        }
        return Observable.fromIterable(application.getIdentities())
                .flatMapSingle(identity -> identityProviderService.findById(identity)
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .toSingle())
                .toList()
                .map(optionalIdentities -> {
                    if (optionalIdentities == null || optionalIdentities.isEmpty()) {
                        application.setIdentities(Collections.emptySet());
                    } else {
                        Set<String> identities = optionalIdentities
                                .stream()
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .map(IdentityProvider::getId)
                                .collect(Collectors.toSet());
                        application.setIdentities(identities);
                    }
                    return application;
                });
    }

    /**
     * <pre>
     * This function will return an error if :
     * We try to enable Dynamic Client Registration on client side while it is not enabled on domain.
     * The redirect_uris do not respect domain conditions (localhost, scheme and wildcard)
     * </pre>
     * @param application application to check
     * @return a client only if every conditions are respected.
     */
    private Single<Application> validateApplicationMetadata(Application application) {
        // do nothing if application has no settings
        if (application.getSettings() == null) {
            return Single.just(application);
        }
        if (application.getSettings().getOauth() == null) {
            return Single.just(application);
        }
        return GrantTypeUtils.validateGrantTypes(application)
                .flatMap(this::validateRedirectUris)
                .flatMap(this::validateScopes)
                .flatMap(this::validateTokenEndpointAuthMethod)
                .flatMap(this::validateTlsClientAuth);
    }

    private Single<Application> validateRedirectUris(Application application) {
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();

        return domainService.findById(application.getDomain())
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(application.getDomain())))
                .flatMapSingle(domain -> {
                    //check redirect_uri
                    if (GrantTypeUtils.isRedirectUriRequired(oAuthSettings.getGrantTypes()) && CollectionUtils.isEmpty(oAuthSettings.getRedirectUris())) {
                        return Single.error(new InvalidRedirectUriException());
                    }

                    //check redirect_uri content
                    if (oAuthSettings.getRedirectUris() != null) {
                        for (String redirectUri : oAuthSettings.getRedirectUris()) {

                            try {
                                URI uri = UriBuilder.fromURIString(redirectUri).build();

                                if (uri.getScheme() == null) {
                                    return Single.error(new InvalidRedirectUriException("redirect_uri : " + redirectUri + " is malformed"));
                                }

                                if (!domain.isRedirectUriLocalhostAllowed() && UriBuilder.isHttp(uri.getScheme()) && UriBuilder.isLocalhost(uri.getHost())) {
                                    return Single.error(new InvalidRedirectUriException("localhost is forbidden"));
                                }
                                //check http scheme
                                if (!domain.isRedirectUriUnsecuredHttpSchemeAllowed() && uri.getScheme().equalsIgnoreCase("http")) {
                                    return Single.error(new InvalidRedirectUriException("Unsecured http scheme is forbidden"));
                                }
                                //check wildcard
                                if (!domain.isRedirectUriWildcardAllowed() && uri.getPath().contains("*")) {
                                    return Single.error(new InvalidRedirectUriException("Wildcard are forbidden"));
                                }
                                // check fragment
                                if (uri.getFragment() != null) {
                                    return Single.error(new InvalidRedirectUriException("redirect_uri with fragment is forbidden"));
                                }
                            } catch (IllegalArgumentException | URISyntaxException ex) {
                                return Single.error(new InvalidRedirectUriException("redirect_uri : " + redirectUri + " is malformed"));
                            }
                        }
                    }
                    return Single.just(application);
                });
    }

    private Single<Application> validateScopes(Application application) {
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        // check scopes and scope approvals
        return scopeService.validateScope(application.getDomain(), oAuthSettings.getScopes())
                .map(isValid -> {
                    // scopes are valid, let's check scope approvals
                    if (isValid && oAuthSettings.getScopeApprovals() != null) {
                        Map<String, Integer> scopeApprovals = oAuthSettings.getScopeApprovals()
                                .entrySet()
                                .stream()
                                .filter(entry -> oAuthSettings.getScopes() != null && oAuthSettings.getScopes().contains(entry.getKey()))
                                .collect(Collectors.toMap(
                                        entry -> entry.getKey(),
                                        entry -> entry.getValue()));
                        oAuthSettings.setScopeApprovals(scopeApprovals);
                    }
                    return isValid;
                })
                .flatMap(isValid -> {
                    if (!isValid) {
                        //last boolean come from scopes validation...
                        return Single.error(new InvalidClientMetadataException("non valid scopes"));
                    }
                    return Single.just(application);
                });
    }

    private Single<Application> validateTokenEndpointAuthMethod(Application application) {
        String tokenEndpointAuthMethod = application.getSettings().getOauth().getTokenEndpointAuthMethod();
        if (ApplicationType.SERVICE.equals(application.getType()) && (tokenEndpointAuthMethod != null && ClientAuthenticationMethod.NONE.equals(tokenEndpointAuthMethod))) {
            return Single.error(new InvalidClientMetadataException("Invalid token_endpoint_auth_method for service application"));
        }
        return Single.just(application);
    }

    /**
     *    A client using the "tls_client_auth" authentication method MUST use exactly one of the
     *    below metadata parameters to indicate the certificate subject value that the authorization server is
     *    to expect when authenticating the respective client.
     */
    private Single<Application> validateTlsClientAuth(Application application) {
        ApplicationOAuthSettings settings = application.getSettings().getOauth();
        if (settings.getTokenEndpointAuthMethod() != null &&
                ClientAuthenticationMethod.TLS_CLIENT_AUTH.equalsIgnoreCase(settings.getTokenEndpointAuthMethod())) {

            if ((settings.getTlsClientAuthSubjectDn() == null || settings.getTlsClientAuthSubjectDn().isEmpty()) &&
                    (settings.getTlsClientAuthSanDns() == null || settings.getTlsClientAuthSanDns().isEmpty()) &&
                    (settings.getTlsClientAuthSanIp() == null || settings.getTlsClientAuthSanIp().isEmpty()) &&
                    (settings.getTlsClientAuthSanEmail() == null || settings.getTlsClientAuthSanEmail().isEmpty()) &&
                    (settings.getTlsClientAuthSanUri() == null || settings.getTlsClientAuthSanUri().isEmpty())) {
                return Single.error(new InvalidClientMetadataException("Missing TLS parameter for tls_client_auth."));
            }

            if (settings.getTlsClientAuthSubjectDn() != null && !settings.getTlsClientAuthSubjectDn().isEmpty() && (
                    (settings.getTlsClientAuthSanDns() != null && !settings.getTlsClientAuthSanDns().isEmpty()) ||
                            (settings.getTlsClientAuthSanEmail() != null && !settings.getTlsClientAuthSanEmail().isEmpty()) ||
                            (settings.getTlsClientAuthSanIp() != null && !settings.getTlsClientAuthSanIp().isEmpty()) ||
                            (settings.getTlsClientAuthSanUri() != null && !settings.getTlsClientAuthSanUri().isEmpty()))) {
                return Single.error(new InvalidClientMetadataException("The tls_client_auth must use exactly one of the TLS parameters."));
            } else if (settings.getTlsClientAuthSanDns() != null && !settings.getTlsClientAuthSanDns().isEmpty() && (
                    (settings.getTlsClientAuthSubjectDn() != null && !settings.getTlsClientAuthSanDns().isEmpty()) ||
                            (settings.getTlsClientAuthSanEmail() != null && !settings.getTlsClientAuthSanEmail().isEmpty()) ||
                            (settings.getTlsClientAuthSanIp() != null && !settings.getTlsClientAuthSanIp().isEmpty()) ||
                            (settings.getTlsClientAuthSanUri() != null && !settings.getTlsClientAuthSanUri().isEmpty()))) {
                return Single.error(new InvalidClientMetadataException("The tls_client_auth must use exactly one of the TLS parameters."));
            } else if (settings.getTlsClientAuthSanIp() != null && !settings.getTlsClientAuthSanIp().isEmpty() && (
                    (settings.getTlsClientAuthSubjectDn() != null && !settings.getTlsClientAuthSubjectDn().isEmpty()) ||
                            (settings.getTlsClientAuthSanDns() != null && !settings.getTlsClientAuthSanDns().isEmpty()) ||
                            (settings.getTlsClientAuthSanEmail() != null && !settings.getTlsClientAuthSanEmail().isEmpty()) ||
                            (settings.getTlsClientAuthSanUri() != null && !settings.getTlsClientAuthSanUri().isEmpty()))) {
                return Single.error(new InvalidClientMetadataException("The tls_client_auth must use exactly one of the TLS parameters."));
            } else if (settings.getTlsClientAuthSanEmail() != null && !settings.getTlsClientAuthSanEmail().isEmpty() && (
                    (settings.getTlsClientAuthSubjectDn() != null && !settings.getTlsClientAuthSubjectDn().isEmpty()) ||
                            (settings.getTlsClientAuthSanDns() != null && !settings.getTlsClientAuthSanDns().isEmpty()) ||
                            (settings.getTlsClientAuthSanIp() != null && !settings.getTlsClientAuthSanIp().isEmpty()) ||
                            (settings.getTlsClientAuthSanUri() != null && !settings.getTlsClientAuthSanUri().isEmpty()))) {
                return Single.error(new InvalidClientMetadataException("The tls_client_auth must use exactly one of the TLS parameters."));
            } else if (settings.getTlsClientAuthSanUri() != null && !settings.getTlsClientAuthSanUri().isEmpty() && (
                    (settings.getTlsClientAuthSubjectDn() != null && !settings.getTlsClientAuthSubjectDn().isEmpty()) ||
                            (settings.getTlsClientAuthSanDns() != null && !settings.getTlsClientAuthSanDns().isEmpty()) ||
                            (settings.getTlsClientAuthSanIp() != null && !settings.getTlsClientAuthSanIp().isEmpty()) ||
                            (settings.getTlsClientAuthSanEmail() != null && !settings.getTlsClientAuthSanEmail().isEmpty()))) {
                return Single.error(new InvalidClientMetadataException("The tls_client_auth must use exactly one of the TLS parameters."));
            }
        }

        return Single.just(application);
    }}

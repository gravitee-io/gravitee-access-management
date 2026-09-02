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
package io.gravitee.am.service;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.service.exception.ApplicationTemplateInUseException;
import io.gravitee.am.service.impl.ApplicationServiceImpl;
import io.gravitee.am.service.impl.OAuthClientUniquenessValidator;
import io.gravitee.am.service.model.PatchApplication;
import io.gravitee.am.service.validators.accountsettings.AccountSettingsValidator;
import io.gravitee.am.service.validators.claims.ApplicationTokenCustomClaimsValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the CIMD-template guard in ApplicationServiceImpl:
 *  - An application referenced as the domain's CIMD templateId must not be deletable.
 *  - Its template flag must not be unset via patch while it is the CIMD template.
 *
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ApplicationServiceCimdGuardTest {

    private static final String DOMAIN_ID = "domain1";
    private static final String CIMD_TEMPLATE_APP_ID = "cimd-template-app";
    private static final String OTHER_APP_ID = "other-app";

    @InjectMocks
    private final ApplicationService applicationService = new ApplicationServiceImpl();

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationTemplateManager applicationTemplateManager;

    @Mock
    private AccountSettingsValidator accountSettingsValidator;

    @Mock
    private OAuthClientUniquenessValidator oAuthClientUniquenessValidator;

    @Mock
    private DomainReadService domainReadService;

    @Mock
    private EventService eventService;

    @Mock
    private ScopeService scopeService;

    @Mock
    private FormService formService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private AuditService auditService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private BiFunction<Domain, Application, Completable> revokeToken;

    @Mock
    private User principal;

    @Spy
    private ApplicationTokenCustomClaimsValidator customClaimsValidator = new ApplicationTokenCustomClaimsValidator();

    @Before
    public void setUp() {
        // lenient: delete-guard tests never reach the uniqueness check
        Mockito.lenient().when(oAuthClientUniquenessValidator.checkClientIdUniqueness(any(), any())).thenReturn(Completable.complete());
    }

    // -------------------------------------------------------------------------
    // delete() guard tests
    // -------------------------------------------------------------------------

    @Test
    public void shouldNotDelete_whenApplicationIsCimdTemplate() {
        Application existingApp = appWithId(CIMD_TEMPLATE_APP_ID);
        when(applicationRepository.findById(CIMD_TEMPLATE_APP_ID)).thenReturn(Maybe.just(existingApp));

        TestObserver<Void> observer = applicationService.delete(CIMD_TEMPLATE_APP_ID, principal, domainWithCimdTemplate(CIMD_TEMPLATE_APP_ID)).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(ApplicationTemplateInUseException.class);
        observer.assertNotComplete();
        verify(applicationRepository, never()).delete(CIMD_TEMPLATE_APP_ID);
    }

    @Test
    public void shouldDelete_whenApplicationIsNotCimdTemplate() {
        Application existingApp = appWithId(OTHER_APP_ID);
        when(applicationRepository.findById(OTHER_APP_ID)).thenReturn(Maybe.just(existingApp));
        when(applicationRepository.delete(OTHER_APP_ID)).thenReturn(Completable.complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(formService.findByDomainAndClient(DOMAIN_ID, OTHER_APP_ID)).thenReturn(Flowable.empty());
        when(emailTemplateService.findByClient(ReferenceType.DOMAIN, DOMAIN_ID, OTHER_APP_ID)).thenReturn(Flowable.empty());
        when(membershipService.findByReference(OTHER_APP_ID, ReferenceType.APPLICATION)).thenReturn(Flowable.empty());

        // CIMD template points to a different application
        TestObserver<Void> observer = applicationService.delete(OTHER_APP_ID, principal, domainWithCimdTemplate(CIMD_TEMPLATE_APP_ID)).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        verify(applicationRepository, times(1)).delete(OTHER_APP_ID);
    }

    @Test
    public void shouldDelete_whenDomainHasNoCimdSettings() {
        Application existingApp = appWithId(OTHER_APP_ID);
        when(applicationRepository.findById(OTHER_APP_ID)).thenReturn(Maybe.just(existingApp));
        when(applicationRepository.delete(OTHER_APP_ID)).thenReturn(Completable.complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(formService.findByDomainAndClient(DOMAIN_ID, OTHER_APP_ID)).thenReturn(Flowable.empty());
        when(emailTemplateService.findByClient(ReferenceType.DOMAIN, DOMAIN_ID, OTHER_APP_ID)).thenReturn(Flowable.empty());
        when(membershipService.findByReference(OTHER_APP_ID, ReferenceType.APPLICATION)).thenReturn(Flowable.empty());

        // Domain with no OIDC/CIMD settings at all
        Domain domainNoOidc = new Domain(DOMAIN_ID);

        TestObserver<Void> observer = applicationService.delete(OTHER_APP_ID, principal, domainNoOidc).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        verify(applicationRepository, times(1)).delete(OTHER_APP_ID);
    }

    @Test
    public void shouldDelete_whenDomainHasCimdSettingsDisabled() {
        Application existingApp = appWithId(OTHER_APP_ID);
        when(applicationRepository.findById(OTHER_APP_ID)).thenReturn(Maybe.just(existingApp));
        when(applicationRepository.delete(OTHER_APP_ID)).thenReturn(Completable.complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(formService.findByDomainAndClient(DOMAIN_ID, OTHER_APP_ID)).thenReturn(Flowable.empty());
        when(emailTemplateService.findByClient(ReferenceType.DOMAIN, DOMAIN_ID, OTHER_APP_ID)).thenReturn(Flowable.empty());
        when(membershipService.findByReference(OTHER_APP_ID, ReferenceType.APPLICATION)).thenReturn(Flowable.empty());

        // Domain with no OIDC/CIMD settings at all
        Domain domainNoOidc = domainWithCimdTemplateDisabled(OTHER_APP_ID);

        TestObserver<Void> observer = applicationService.delete(OTHER_APP_ID, principal, domainNoOidc).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        verify(applicationRepository, times(1)).delete(OTHER_APP_ID);
    }

    @Test
    public void shouldDelete_whenCimdTemplateIdIsNull() {
        Application existingApp = appWithId(OTHER_APP_ID);
        when(applicationRepository.findById(OTHER_APP_ID)).thenReturn(Maybe.just(existingApp));
        when(applicationRepository.delete(OTHER_APP_ID)).thenReturn(Completable.complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(formService.findByDomainAndClient(DOMAIN_ID, OTHER_APP_ID)).thenReturn(Flowable.empty());
        when(emailTemplateService.findByClient(ReferenceType.DOMAIN, DOMAIN_ID, OTHER_APP_ID)).thenReturn(Flowable.empty());
        when(membershipService.findByReference(OTHER_APP_ID, ReferenceType.APPLICATION)).thenReturn(Flowable.empty());

        // OIDC and CIMD settings exist but templateId is null
        TestObserver<Void> observer = applicationService.delete(OTHER_APP_ID, principal, domainWithCimdTemplate(null)).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        verify(applicationRepository, times(1)).delete(OTHER_APP_ID);
    }

    // -------------------------------------------------------------------------
    // patch() guard tests (template flag)
    // -------------------------------------------------------------------------

    @Test
    public void shouldNotPatch_whenUnsettingTemplateOnCimdApplication() {
        Application existingApp = appWithId(CIMD_TEMPLATE_APP_ID);
        existingApp.setTemplate(true);
        when(applicationRepository.findById(CIMD_TEMPLATE_APP_ID)).thenReturn(Maybe.just(existingApp));

        PatchApplication patch = new PatchApplication();
        patch.setTemplate(Optional.of(false));

        TestObserver<Application> observer = applicationService.patch(
                domainWithCimdTemplate(CIMD_TEMPLATE_APP_ID), CIMD_TEMPLATE_APP_ID, patch, principal, revokeToken).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(ApplicationTemplateInUseException.class);
        observer.assertNotComplete();
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void shouldPatch_whenUnsettingTemplateOnApplicationNotReferencedByCimd() {
        Application existingApp = appWithId(OTHER_APP_ID);
        existingApp.setTemplate(true);
        withServiceSettings(existingApp);

        when(applicationRepository.findById(OTHER_APP_ID)).thenReturn(Maybe.just(existingApp));
        when(applicationRepository.update(any(Application.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(domainReadService.findById(DOMAIN_ID)).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN_ID, Collections.emptyList())).thenReturn(Single.just(true));

        PatchApplication patch = new PatchApplication();
        patch.setTemplate(Optional.of(false));

        // CIMD references a different app — unsetting template must succeed
        TestObserver<Application> observer = applicationService.patch(
                domainWithCimdTemplate(CIMD_TEMPLATE_APP_ID), OTHER_APP_ID, patch, principal, revokeToken).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        verify(applicationRepository, times(1)).update(argThat(app -> !app.isTemplate()));
    }

    @Test
    public void shouldPatch_whenSettingTemplateTrue() {
        Application existingApp = appWithId(OTHER_APP_ID);
        existingApp.setTemplate(false);
        withServiceSettings(existingApp);

        when(applicationRepository.findById(OTHER_APP_ID)).thenReturn(Maybe.just(existingApp));
        when(applicationRepository.update(any(Application.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(domainReadService.findById(DOMAIN_ID)).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN_ID, Collections.emptyList())).thenReturn(Single.just(true));

        PatchApplication patch = new PatchApplication();
        patch.setTemplate(Optional.of(true));

        // Setting template=true never conflicts with CIMD — always allowed
        TestObserver<Application> observer = applicationService.patch(
                domainWithCimdTemplate(CIMD_TEMPLATE_APP_ID), OTHER_APP_ID, patch, principal, revokeToken).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        verify(applicationRepository, times(1)).update(argThat(Application::isTemplate));
    }

    @Test
    public void shouldPatch_whenTemplateFieldNotPresentInPatch() {
        Application existingApp = appWithId(CIMD_TEMPLATE_APP_ID);
        existingApp.setTemplate(true);
        withServiceSettings(existingApp);

        when(applicationRepository.findById(CIMD_TEMPLATE_APP_ID)).thenReturn(Maybe.just(existingApp));
        when(applicationRepository.update(any(Application.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(domainReadService.findById(DOMAIN_ID)).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN_ID, Collections.emptyList())).thenReturn(Single.just(true));

        // Patch that does not touch the template flag at all — guard must not fire
        PatchApplication patch = new PatchApplication();

        TestObserver<Application> observer = applicationService.patch(
                domainWithCimdTemplate(CIMD_TEMPLATE_APP_ID), CIMD_TEMPLATE_APP_ID, patch, principal, revokeToken).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        verify(applicationRepository, times(1)).update(any(Application.class));
    }

    @Test
    public void shouldPatch_whenUnsettingTemplateAndDomainHasNoCimdSettings() {
        Application existingApp = appWithId(OTHER_APP_ID);
        existingApp.setTemplate(true);
        withServiceSettings(existingApp);

        when(applicationRepository.findById(OTHER_APP_ID)).thenReturn(Maybe.just(existingApp));
        when(applicationRepository.update(any(Application.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(domainReadService.findById(DOMAIN_ID)).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN_ID, Collections.emptyList())).thenReturn(Single.just(true));

        PatchApplication patch = new PatchApplication();
        patch.setTemplate(Optional.of(false));

        // Domain with no OIDC settings at all — guard must not fire
        Domain domainNoOidc = new Domain(DOMAIN_ID);

        TestObserver<Application> observer = applicationService.patch(domainNoOidc, OTHER_APP_ID, patch, principal, revokeToken).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        verify(applicationRepository, times(1)).update(argThat(app -> !app.isTemplate()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Application appWithId(String id) {
        Application app = new Application();
        app.setId(id);
        app.setDomain(DOMAIN_ID);
        app.setType(ApplicationType.SERVICE);
        return app;
    }

    private static void withServiceSettings(Application app) {
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setGrantTypes(Collections.singletonList("client_credentials"));
        settings.setOauth(oauth);
        app.setSettings(settings);
    }

    private static Domain domainWithCimdTemplateDisabled(String templateId) {
        return domainWithCimd(templateId, false);
    }

    private static Domain domainWithCimdTemplate(String templateId) {
        return domainWithCimd(templateId, true);
    }

    private static Domain domainWithCimd(String templateId, boolean enabled) {
        CIMDSettings cimdSettings = new CIMDSettings();
        cimdSettings.setTemplateId(templateId);
        cimdSettings.setEnabled(enabled);
        OIDCSettings oidcSettings = new OIDCSettings();
        oidcSettings.setCimdSettings(cimdSettings);
        Domain domain = new Domain(DOMAIN_ID);
        domain.setOidc(oidcSettings);
        return domain;
    }
}

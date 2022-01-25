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

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.*;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.account.FormField;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.gravitee.am.service.exception.DomainAlreadyExistsException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.DomainServiceImpl;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.NewSystemScope;
import io.gravitee.am.service.model.PatchDomain;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DomainServiceTest {

    private static final String DOMAIN_ID = "id-domain";
    public static final String ORGANIZATION_ID = "orga#1";
    public static final String ENVIRONMENT_ID = "env#1";
    private static final String ALERT_TRIGGER_ID = "alertTrigger#1";
    private static final String ALERT_NOTIFIER_ID = "alertNotifier#1";

    @InjectMocks
    private DomainService domainService = new DomainServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private Certificate certificate;

    @Mock
    private IdentityProvider identityProvider;

    @Mock
    private ExtensionGrant extensionGrant;

    @Mock
    private Role role;

    @Mock
    private User user;

    @Mock
    private Scope scope;

    @Mock
    private Group group;

    @Mock
    private Form form;

    @Mock
    private Email email;

    @Mock
    private Reporter reporter;

    @Mock
    private Flow flow;

    @Mock
    private Membership membership;

    @Mock
    private Factor factor;

    @Mock
    private Resource resource;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private CertificateService certificateService;

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private ExtensionGrantService extensionGrantService;

    @Mock
    private UserService userService;

    @Mock
    private RoleService roleService;

    @Mock
    private ScopeService scopeService;

    @Mock
    private GroupService groupService;

    @Mock
    private FormService formService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private AuditService auditService;

    @Mock
    private ReporterService reporterService;

    @Mock
    private FlowService flowService;

    @Mock
    private EventService eventService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private FactorService factorService;

    @Mock
    private ResourceService resourceService;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private AlertTriggerService alertTriggerService;

    @Mock
    private AlertNotifierService alertNotifierService;

    @Mock
    private BotDetectionService botDetectionService;

    @Mock
    private ServiceResourceService serviceResourceService;

    @Test
    public void shouldFindById() {
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(new Domain()));
        TestObserver testObserver = domainService.findById("my-domain").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingDomain() {
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.empty());
        TestObserver testObserver = domainService.findById("my-domain").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        domainService.findById("my-domain").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindAll() {
        when(domainRepository.findAll()).thenReturn(Flowable.just(new Domain()));
        TestObserver<List<Domain>> testObserver = domainService.findAll().test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(domains -> domains.size() == 1);
    }

    @Test
    public void shouldFindAll_technicalException() {
        when(domainRepository.findAll()).thenReturn(Flowable.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        domainService.findAll().subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByIdsIn() {
        when(domainRepository.findByIdIn(Arrays.asList("1", "2"))).thenReturn(Flowable.just(new Domain()));
        TestSubscriber<Domain> testSubscriber = domainService.findByIdIn(Arrays.asList("1", "2")).test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindByIdsIn_technicalException() {
        when(domainRepository.findByIdIn(Arrays.asList("1", "2"))).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = domainService.findByIdIn(Arrays.asList("1", "2")).test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewDomain newDomain = Mockito.mock(NewDomain.class);
        when(newDomain.getName()).thenReturn("my-domain");
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainRepository.findAll()).thenReturn(Flowable.empty());
        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setId("domain-id");
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain")).thenReturn(Maybe.empty());
        when(domainRepository.create(any(Domain.class))).thenReturn(Single.just(domain));
        when(scopeService.create(anyString(), any(NewSystemScope.class))).thenReturn(Single.just(new Scope()));
        when(certificateService.create(eq(domain.getId()))).thenReturn(Single.just(new Certificate()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(membershipService.addOrUpdate(eq(ORGANIZATION_ID), any())).thenReturn(Single.just(new Membership()));
        when(roleService.findSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER, ReferenceType.DOMAIN)).thenReturn(Maybe.just(new Role()));

        TestObserver testObserver = domainService.create(ORGANIZATION_ID, ENVIRONMENT_ID, newDomain, new DefaultUser("username")).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(domainRepository, times(1)).findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain");
        verify(domainRepository, times(1)).create(any(Domain.class));
        verify(scopeService, times(io.gravitee.am.common.oidc.Scope.values().length)).create(anyString(), any(NewSystemScope.class));
        verify(certificateService).create(eq(domain.getId()));
        verify(eventService).create(any());
        verify(membershipService).addOrUpdate(eq(ORGANIZATION_ID), any());
    }

    @Test
    public void shouldCreate_technicalException() {
        NewDomain newDomain = Mockito.mock(NewDomain.class);
        when(newDomain.getName()).thenReturn("my-domain");
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver<Domain> testObserver = new TestObserver<>();
        domainService.create(ORGANIZATION_ID, ENVIRONMENT_ID, newDomain).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(domainRepository, never()).create(any(Domain.class));
    }

    @Test
    public void shouldCreate2_technicalException() {
        NewDomain newDomain = Mockito.mock(NewDomain.class);
        when(newDomain.getName()).thenReturn("my-domain");
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain")).thenReturn(Maybe.empty());
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when((domainRepository.findAll())).thenReturn(Flowable.empty());
        when(domainRepository.create(any(Domain.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver<Domain> testObserver = new TestObserver<>();
        domainService.create(ORGANIZATION_ID, ENVIRONMENT_ID, newDomain).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(domainRepository, times(1)).findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain");
    }

    @Test
    public void shouldCreate_existingDomain() {
        NewDomain newDomain = Mockito.mock(NewDomain.class);
        when(newDomain.getName()).thenReturn("my-domain");
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain")).thenReturn(Maybe.just(new Domain()));

        TestObserver<Domain> testObserver = new TestObserver<>();
        domainService.create(ORGANIZATION_ID, ENVIRONMENT_ID, newDomain).subscribe(testObserver);

        testObserver.assertError(DomainAlreadyExistsException.class);
        testObserver.assertNotComplete();

        verify(domainRepository, never()).create(any(Domain.class));
    }

    @Test
    public void shouldPatch_domainNotFound() {
        PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.empty());

        TestObserver testObserver = domainService.patch("my-domain", patchDomain).test();
        testObserver.assertError(DomainNotFoundException.class);
        testObserver.assertNotComplete();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, never()).update(any(Domain.class));
    }

    @Test
    public void shouldPatch() {
        PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        Domain domain = new Domain();
        domain.setId("my-domain");
        domain.setHrid("my-domain");
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName("my-domain");
        domain.setPath("/test");
        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, domain.getHrid())).thenReturn(Maybe.just(domain));
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainRepository.findAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(domain));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = domainService.patch("my-domain", patchDomain).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldPatch_NoResetPasswordMultiFields() {
        PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        Domain domain = new Domain();
        domain.setId("my-domain");
        domain.setHrid("my-domain");
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName("my-domain");
        domain.setPath("/test");
        final AccountSettings accountSettings = new AccountSettings();
        accountSettings.setResetPasswordCustomForm(false);
        domain.setAccountSettings(accountSettings);
        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, domain.getHrid())).thenReturn(Maybe.just(domain));
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainRepository.findAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(domain));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = domainService.patch("my-domain", patchDomain).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldPatch_ResetPasswordMultiFields_ValidFields() {
        PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        Domain domain = new Domain();
        domain.setId("my-domain");
        domain.setHrid("my-domain");
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName("my-domain");
        domain.setPath("/test");
        final AccountSettings accountSettings = new AccountSettings();
        final FormField formField = new FormField();
        formField.setKey("username");
        accountSettings.setResetPasswordCustomFormFields(Arrays.asList(formField));
        accountSettings.setResetPasswordCustomForm(true);
        domain.setAccountSettings(accountSettings);
        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, domain.getHrid())).thenReturn(Maybe.just(domain));
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainRepository.findAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(domain));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = domainService.patch("my-domain", patchDomain).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldNoPatch_ResetPasswordMultiFields_InvalidFields() {
        PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        Domain domain = new Domain();
        domain.setId("my-domain");
        domain.setHrid("my-domain");
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName("my-domain");
        domain.setPath("/test");
        final AccountSettings accountSettings = new AccountSettings();
        final FormField formField = new FormField();
        formField.setKey("unknown");
        accountSettings.setResetPasswordCustomFormFields(Arrays.asList(formField));
        accountSettings.setResetPasswordCustomForm(true);
        domain.setAccountSettings(accountSettings);
        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));

        TestObserver testObserver = domainService.patch("my-domain", patchDomain).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidParameterException.class);

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, never()).update(any(Domain.class));
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldPatch_hrid_already_exists() {
        PatchDomain patchDomain = Mockito.mock(PatchDomain.class);

        Domain domain = new Domain();
        domain.setId("my-domain");
        domain.setHrid("my-domain");
        domain.setName("my-domain");
        domain.setPath("/test");
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Domain otherDomain = new Domain();
        otherDomain.setId("my-domain-2");
        otherDomain.setHrid("my-domain");
        otherDomain.setName("my-domain");
        otherDomain.setPath("/test2");
        otherDomain.setReferenceType(ReferenceType.ENVIRONMENT);
        otherDomain.setReferenceId(ENVIRONMENT_ID);

        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, domain.getHrid())).thenReturn(Maybe.just(otherDomain));

        TestObserver testObserver = domainService.patch("my-domain", patchDomain).test();
        testObserver.assertError(DomainAlreadyExistsException.class);
        testObserver.assertNotComplete();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, never()).update(any(Domain.class));
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldPatch_technicalException() {
        PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = domainService.patch("my-domain", patchDomain).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, never()).update(any(Domain.class));
    }

    @Test
    public void shouldDelete() {
        Application mockApp1 = new Application();
        mockApp1.setId("client-1");

        Application mockApp2 = new Application();
        mockApp2.setId("client-2");

        Set<Application> mockApplications = new HashSet<>();
        mockApplications.add(mockApp1);
        mockApplications.add(mockApp2);

        final AlertTrigger alertTrigger = new AlertTrigger();
        alertTrigger.setId(ALERT_TRIGGER_ID);
        alertTrigger.setReferenceType(ReferenceType.DOMAIN);
        alertTrigger.setReferenceId(DOMAIN_ID);

        final AlertNotifier alertNotifier = new AlertNotifier();
        alertNotifier.setId(ALERT_NOTIFIER_ID);
        alertNotifier.setReferenceType(ReferenceType.DOMAIN);
        alertNotifier.setReferenceId(DOMAIN_ID);

        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(domainRepository.delete(DOMAIN_ID)).thenReturn(Completable.complete());
        when(applicationService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(certificateService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(identityProviderService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(extensionGrantService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(roleService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(userService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(scopeService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(groupService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(formService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(emailTemplateService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(reporterService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(flowService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(membershipService.deleteByReference(DOMAIN_ID, ReferenceType.DOMAIN)).thenReturn(Completable.complete());
        when(factorService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(resourceService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(alertTriggerService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(alertNotifierService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(botDetectionService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(serviceResourceService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = domainService.delete(DOMAIN_ID).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoErrors();
        testObserver.assertComplete();

        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldDeleteWithoutRelatedData() {
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(domainRepository.delete(DOMAIN_ID)).thenReturn(Completable.complete());
        when(applicationService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(certificateService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(identityProviderService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(extensionGrantService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(roleService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(userService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(scopeService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(groupService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(formService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(emailTemplateService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(reporterService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(flowService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(membershipService.deleteByReference(DOMAIN_ID, ReferenceType.DOMAIN)).thenReturn(Completable.complete());
        when(factorService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(resourceService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(alertTriggerService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(alertNotifierService.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Completable.complete());
        when(botDetectionService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(serviceResourceService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = domainService.delete(DOMAIN_ID).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldNotDeleteBecauseDoesntExist() {
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.empty());

        TestObserver testObserver = domainService.delete(DOMAIN_ID).test();
        testObserver.assertError(DomainNotFoundException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_technicalException() {
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = domainService.delete(DOMAIN_ID).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete2_technicalException() {
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(applicationService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.error(TechnicalException::new));

        TestObserver testObserver = domainService.delete(DOMAIN_ID).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldBuildUrl_contextPathMode() {

        ReflectionTestUtils.setField(domainService, "gatewayUrl", "http://localhost:8092");

        Domain domain = new Domain();
        domain.setPath("/testPath");
        domain.setVhostMode(false);

        String url = domainService.buildUrl(domain, "/mySubPath?myParam=param1");

        assertEquals("http://localhost:8092/testPath/mySubPath?myParam=param1", url);
    }

    @Test
    public void shouldBuildUrl_vhostMode() {

        ReflectionTestUtils.setField(domainService, "gatewayUrl", "http://localhost:8092");

        Domain domain = new Domain();
        domain.setPath("/testPath");
        domain.setVhostMode(true);
        ArrayList<VirtualHost> vhosts = new ArrayList<>();
        VirtualHost firstVhost = new VirtualHost();
        firstVhost.setHost("test1.gravitee.io");
        firstVhost.setPath("/test1");
        vhosts.add(firstVhost);
        VirtualHost secondVhost = new VirtualHost();
        secondVhost.setHost("test2.gravitee.io");
        secondVhost.setPath("/test2");
        secondVhost.setOverrideEntrypoint(true);
        vhosts.add(secondVhost);
        domain.setVhosts(vhosts);

        String url = domainService.buildUrl(domain, "/mySubPath?myParam=param1");

        assertEquals("http://test2.gravitee.io/test2/mySubPath?myParam=param1", url);
    }

    @Test
    public void shouldBuildUrl_vhostModeAndHttps() {

        ReflectionTestUtils.setField(domainService, "gatewayUrl", "https://localhost:8092");

        Domain domain = new Domain();
        domain.setPath("/testPath");
        domain.setVhostMode(true);
        ArrayList<VirtualHost> vhosts = new ArrayList<>();
        VirtualHost firstVhost = new VirtualHost();
        firstVhost.setHost("test1.gravitee.io");
        firstVhost.setPath("/test1");
        vhosts.add(firstVhost);
        VirtualHost secondVhost = new VirtualHost();
        secondVhost.setHost("test2.gravitee.io");
        secondVhost.setPath("/test2");
        secondVhost.setOverrideEntrypoint(true);
        vhosts.add(secondVhost);
        domain.setVhosts(vhosts);

        String url = domainService.buildUrl(domain, "/mySubPath?myParam=param1");

        assertEquals("https://test2.gravitee.io/test2/mySubPath?myParam=param1", url);
    }

    @Test
    public void shouldFindByCriteria() {

        final DomainCriteria criteria = new DomainCriteria();
        final Domain domain = new Domain();

        when(domainRepository.findAllByCriteria(eq(criteria))).thenReturn(Flowable.just(domain));

        final TestSubscriber<Domain> obs = domainService.findAllByCriteria(criteria).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(domain);
    }
}

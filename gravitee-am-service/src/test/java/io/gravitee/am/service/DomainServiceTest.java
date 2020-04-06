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
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.service.exception.DomainAlreadyExistsException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.DomainServiceImpl;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.NewSystemScope;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.am.service.model.UpdateDomain;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DomainServiceTest {

    private static final String DOMAIN_ID = "id-domain";
    private static final String IDP_ID = "id-idp";
    private static final String CERTIFICATE_ID = "id-certificate";
    private static final String EXTENSION_GRANT_ID = "id-extension-grant";
    private static final String ROLE_ID = "id-role";
    private static final String USER_ID = "id-user";
    private static final String SCOPE_ID = "id-scope";
    private static final String GROUP_ID = "id-group";
    private static final String FORM_ID = "id-form";
    private static final String EMAIL_ID = "id-email";
    private static final String REPORTER_ID = "id-reporter";
    private static final String POLICY_ID = "id-policy";
    private static final String MEMBERSHIP_ID = "id-membership";
    private static final String FACTOR_ID = "id-factor";
    public static final String ORGANIZATION_ID = "orga#1";
    public static final String ENVIRONMENT_ID = "env#1";

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
    private Policy policy;

    @Mock
    private Membership membership;

    @Mock
    private Factor factor;

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
    private PolicyService policyService;

    @Mock
    private EventService eventService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private FactorService factorService;

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
        when(domainRepository.findAll()).thenReturn(Single.just(Collections.singleton(new Domain())));
        TestObserver<Set<Domain>> testObserver = domainService.findAll().test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(domains -> domains.size() == 1);
    }

    @Test
    public void shouldFindAll_technicalException() {
        when(domainRepository.findAll()).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        domainService.findAll().subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByIdsIn() {
        when(domainRepository.findByIdIn(Arrays.asList("1", "2"))).thenReturn(Single.just(Collections.singleton(new Domain())));
        TestObserver<Set<Domain>> testObserver = domainService.findByIdIn(Arrays.asList("1", "2")).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(domains -> domains.size() == 1);
    }

    @Test
    public void shouldFindByIdsIn_technicalException() {
        when(domainRepository.findByIdIn(Arrays.asList("1", "2"))).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        domainService.findByIdIn(Arrays.asList("1", "2")).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewDomain newDomain = Mockito.mock(NewDomain.class);
        when(newDomain.getName()).thenReturn("my-domain");
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.empty());
        Domain domain = new Domain();
        domain.setId("domain-id");
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

        verify(domainRepository, times(1)).findById(anyString());
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
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.error(TechnicalException::new));

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
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.empty());
        when(domainRepository.create(any(Domain.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver<Domain> testObserver = new TestObserver<>();
        domainService.create(ORGANIZATION_ID, ENVIRONMENT_ID, newDomain).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(domainRepository, times(1)).findById(anyString());
    }

    @Test
    public void shouldCreate_existingDomain() {
        NewDomain newDomain = Mockito.mock(NewDomain.class);
        when(newDomain.getName()).thenReturn("my-domain");
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(new Domain()));

        TestObserver<Domain> testObserver = new TestObserver<>();
        domainService.create(ORGANIZATION_ID, ENVIRONMENT_ID, newDomain).subscribe(testObserver);

        testObserver.assertError(DomainAlreadyExistsException.class);
        testObserver.assertNotComplete();

        verify(domainRepository, never()).create(any(Domain.class));
    }

    @Test
    public void shouldUpdate() {
        UpdateDomain updateDomain = Mockito.mock(UpdateDomain.class);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(new Domain()));
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = domainService.update("my-domain", updateDomain).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateDomain updateDomain = Mockito.mock(UpdateDomain.class);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = domainService.update("my-domain", updateDomain).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, never()).update(any(Domain.class));
    }

    @Test
    public void shouldUpdate2_technicalException() {
        UpdateDomain updateDomain = Mockito.mock(UpdateDomain.class);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(new Domain()));
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = domainService.update("my-domain", updateDomain).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(domainRepository, times(1)).findById(anyString());
    }

    @Test
    public void shouldUpdate_domainNotFound() {
        UpdateDomain updateDomain = Mockito.mock(UpdateDomain.class);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.empty());

        TestObserver testObserver = domainService.update("my-domain", updateDomain).test();
        testObserver.assertError(DomainNotFoundException.class);
        testObserver.assertNotComplete();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, never()).update(any(Domain.class));
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
        when(patchDomain.patch(any())).thenReturn(new Domain());
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(new Domain()));
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(new Domain()));
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

        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(domainRepository.delete(DOMAIN_ID)).thenReturn(Completable.complete());
        when(applicationService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(mockApplications));
        when(applicationService.delete(anyString())).thenReturn(Completable.complete());
        when(certificate.getId()).thenReturn(CERTIFICATE_ID);
        when(certificateService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singletonList(certificate)));
        when(certificateService.delete(anyString())).thenReturn(Completable.complete());
        when(identityProvider.getId()).thenReturn(IDP_ID);
        when(identityProviderService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singletonList(identityProvider)));
        when(identityProviderService.delete(eq(DOMAIN_ID), anyString())).thenReturn(Completable.complete());
        when(extensionGrant.getId()).thenReturn(EXTENSION_GRANT_ID);
        when(extensionGrantService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singletonList(extensionGrant)));
        when(extensionGrantService.delete(eq(DOMAIN_ID), anyString())).thenReturn(Completable.complete());
        when(role.getId()).thenReturn(ROLE_ID);
        when(roleService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singleton(role)));
        when(roleService.delete(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), anyString())).thenReturn(Completable.complete());
        when(user.getId()).thenReturn(USER_ID);
        when(userService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singleton(user)));
        when(userService.delete(anyString())).thenReturn(Completable.complete());
        when(scope.getId()).thenReturn(SCOPE_ID);
        when(scopeService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singleton(scope)));
        when(scopeService.delete(SCOPE_ID, true)).thenReturn(Completable.complete());
        when(group.getId()).thenReturn(GROUP_ID);
        when(groupService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singletonList(group)));
        when(groupService.delete(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), anyString())).thenReturn(Completable.complete());
        when(form.getId()).thenReturn(FORM_ID);
        when(formService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singletonList(form)));
        when(formService.delete(eq(DOMAIN_ID), anyString())).thenReturn(Completable.complete());
        when(email.getId()).thenReturn(EMAIL_ID);
        when(emailTemplateService.findAll(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Single.just(Collections.singletonList(email)));
        when(emailTemplateService.delete(anyString())).thenReturn(Completable.complete());
        when(reporter.getId()).thenReturn(REPORTER_ID);
        when(reporterService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singletonList(reporter)));
        when(reporterService.delete(anyString())).thenReturn(Completable.complete());
        when(policy.getId()).thenReturn(POLICY_ID);
        when(policyService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singletonList(policy)));
        when(policyService.delete(anyString())).thenReturn(Completable.complete());
        when(membership.getId()).thenReturn(MEMBERSHIP_ID);
        when(membershipService.findByReference(DOMAIN_ID, ReferenceType.DOMAIN)).thenReturn(Single.just(Collections.singletonList(membership)));
        when(membershipService.delete(anyString())).thenReturn(Completable.complete());
        when(factor.getId()).thenReturn(FACTOR_ID);
        when(factorService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singletonList(factor)));
        when(factorService.delete(DOMAIN_ID, FACTOR_ID)).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = domainService.delete(DOMAIN_ID).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoErrors();
        testObserver.assertComplete();

        verify(applicationService, times(2)).delete(anyString());
        verify(certificateService, times(1)).delete(CERTIFICATE_ID);
        verify(identityProviderService, times(1)).delete(DOMAIN_ID, IDP_ID);
        verify(extensionGrantService, times(1)).delete(DOMAIN_ID, EXTENSION_GRANT_ID);
        verify(roleService, times(1)).delete(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(ROLE_ID));
        verify(userService, times(1)).delete(USER_ID);
        verify(scopeService, times(1)).delete(SCOPE_ID, true);
        verify(groupService, times(1)).delete(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(GROUP_ID));
        verify(formService, times(1)).delete(eq(DOMAIN_ID), eq(FORM_ID));
        verify(emailTemplateService, times(1)).delete(EMAIL_ID);
        verify(reporterService, times(1)).delete(REPORTER_ID);
        verify(policyService, times(1)).delete(POLICY_ID);
        verify(membershipService, times(1)).delete(MEMBERSHIP_ID);
        verify(factorService, times(1)).delete(DOMAIN_ID, FACTOR_ID);
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldDeleteWithoutRelatedData() {
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(domainRepository.delete(DOMAIN_ID)).thenReturn(Completable.complete());
        when(applicationService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptySet()));
        when(certificateService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptyList()));
        when(identityProviderService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptyList()));
        when(extensionGrantService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptyList()));
        when(roleService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptySet()));
        when(userService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptySet()));
        when(scopeService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptySet()));
        when(groupService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptyList()));
        when(formService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptyList()));
        when(emailTemplateService.findAll(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Single.just(Collections.emptyList()));
        when(reporterService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptyList()));
        when(policyService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptyList()));
        when(membershipService.findByReference(DOMAIN_ID, ReferenceType.DOMAIN)).thenReturn(Single.just(Collections.emptyList()));
        when(factorService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptyList()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = domainService.delete(DOMAIN_ID).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, never()).delete(anyString());
        verify(certificateService, never()).delete(anyString());
        verify(identityProviderService, never()).delete(anyString(), anyString());
        verify(extensionGrantService, never()).delete(anyString(), anyString());
        verify(roleService, never()).delete(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), anyString());
        verify(userService, never()).delete(anyString());
        verify(scopeService, never()).delete(anyString(), anyBoolean());
        verify(formService, never()).delete(anyString(), anyString());
        verify(emailTemplateService, never()).delete(anyString());
        verify(reporterService, never()).delete(anyString());
        verify(policyService, never()).delete(anyString());
        verify(membershipService, never()).delete(anyString());
        verify(factorService, never()).delete(anyString(), anyString());
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
        when(applicationService.findByDomain(DOMAIN_ID)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = domainService.delete(DOMAIN_ID).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }
}

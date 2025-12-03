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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.service.DefaultIdentityProviderService;
import io.gravitee.am.management.service.DomainGroupService;
import io.gravitee.am.management.service.dataplane.UMAResourceManagementService;
import io.gravitee.am.management.service.dataplane.UserActivityManagementService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.CorsSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.DomainVersion;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.I18nDictionary;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.account.FormField;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.exceptions.TechnicalException;
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
import io.gravitee.am.service.CertificateCredentialService;
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
import io.gravitee.am.service.exception.DomainAlreadyExistsException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.InvalidDomainException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.InvalidWebAuthnConfigurationException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.NewSystemScope;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.am.service.validators.accountsettings.AccountSettingsValidator;
import io.gravitee.am.service.validators.domain.DomainValidator;
import io.gravitee.am.service.validators.virtualhost.VirtualHostValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.model.ReferenceType.ORGANIZATION;
import static io.reactivex.rxjava3.core.Completable.complete;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class DomainServiceTest {

    public static final String ENTRYPOINT_ID1 = "entrypoint-1";
    public static final String TAG_ID2 = "tag#2";
    public static final String TAG_ID1 = "tag#1";
    public static final String ENTRYPOINT_ID2 = "entrypoint-2";
    public static final String ENTRYPOINT_ID_DEFAULT = "DEFAULT";
    private static final String DOMAIN_ID = "id-domain";
    private static final String IDP_ID = "id-idp";
    private static final String CERTIFICATE_ID = "id-certificate";
    private static final String EXTENSION_GRANT_ID = "id-extension-grant";
    private static final String ROLE_ID = "id-role";
    private static final String SCOPE_ID = "id-scope";
    private static final String GROUP_ID = "id-group";
    private static final String FORM_ID = "id-form";
    private static final String EMAIL_ID = "id-email";
    private static final String REPORTER_ID = "id-reporter";
    private static final String FLOW_ID = "id-flow";
    private static final String MEMBERSHIP_ID = "id-membership";
    private static final String FACTOR_ID = "id-factor";
    public static final String ORGANIZATION_ID = "orga#1";
    public static final String ENVIRONMENT_ID = "env#1";
    private static final String ALERT_TRIGGER_ID = "alertTrigger#1";
    private static final String ALERT_NOTIFIER_ID = "alertNotifier#1";
    private static final String AUTH_DEVICE_ID = "authdevice-Notifier#1";
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @InjectMocks
    private DomainServiceImpl domainService = new DomainServiceImpl();

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private DomainValidator domainValidator;

    @Mock
    private VirtualHostValidator virtualHostValidator;

    @Mock
    private AccountSettingsValidator accountSettingsValidator;

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
    private UserRepository userRepository;

    @Mock
    private UserActivityManagementService userActivityService;

    @Mock
    private RoleService roleService;

    @Mock
    private ScopeService scopeService;

    @Mock
    private DomainGroupService domainGroupService;

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
    private UMAResourceManagementService resourceService;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private AlertTriggerService alertTriggerService;

    @Mock
    private AlertNotifierService alertNotifierService;

    @Mock
    private AuthenticationDeviceNotifierService authenticationDeviceNotifierService;

    @Mock
    private I18nDictionaryService i18nDictionaryService;

    @Mock
    private ThemeService themeService;

    @Mock
    private PasswordHistoryService passwordHistoryService;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @Mock
    private DomainReadService domainReadService;

    @Mock
    private EntrypointService entrypointService;

    @Mock
    private DefaultIdentityProviderService defaultIdentityProviderService;

    @Mock
    private DeviceIdentifierService deviceIdentifierService;

    @Mock
    private ServiceResourceService serviceResourceService;

    @Mock
    private ProtectedResourceService protectedResourceService;

    @Mock
    private CertificateCredentialService certificateCredentialService;

    @Mock
    private AuthorizationEngineService authorizationEngineService;

    @Test
    public void shouldDelegateFindById() {
        domainService.findById("some-id");
        verify(domainReadService).findById("some-id");
    }

    @Test
    public void shouldDelegateListAll() {
        domainService.listAll();
        verify(domainReadService).listAll();
    }

    @Test
    public void shouldDelegateBuildUrl() {
        domainService.buildUrl(any(), any(), any());
        verify(domainReadService).buildUrl(any(), any(), any());
    }

    @Test
    public void shouldFindByIdsIn() {
        when(domainRepository.findByIdIn(Arrays.asList("1", "2"))).thenReturn(Flowable.just(new Domain()));
        TestSubscriber<Domain> testSubscriber = domainService.findByIdIn(Arrays.asList("1", "2")).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindByIdsIn_technicalException() {
        when(domainRepository.findByIdIn(Arrays.asList("1", "2"))).thenReturn(Flowable.error(TechnicalException::new));

        domainService.findByIdIn(Arrays.asList("1", "2"))
                .test()
                .assertError(TechnicalManagementException.class)
                .assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewDomain newDomain = new NewDomain();
        newDomain.setName("my-domain");
        newDomain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setId("domain-id");
        domain.setVersion(DomainVersion.V2_0);
        domain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);
        when(dataPlaneRegistry.getDataPlanes()).thenReturn(List.of(new DataPlaneDescription(DataPlaneDescription.DEFAULT_DATA_PLANE_ID,"default","mongodb","test", "http://localhost:8092")));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain")).thenReturn(Maybe.empty());
        when(domainRepository.create(any(Domain.class))).thenReturn(Single.just(domain));
        when(scopeService.create(any(), any(NewSystemScope.class))).thenReturn(Single.just(new Scope()));
        when(certificateService.create(any())).thenReturn(Single.just(new Certificate()));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(membershipService.addOrUpdate(eq(ORGANIZATION_ID), any())).thenReturn(Single.just(new Membership()));
        when(roleService.findSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER, DOMAIN)).thenReturn(Maybe.just(new Role()));
        when(reporterService.notifyInheritedReporters(any(),any(),any())).thenReturn(Completable.complete());
        when(reporterService.createDefault(any())).thenReturn(Single.just(new Reporter()));
        when(defaultIdentityProviderService.create(any())).thenReturn(Single.just(new IdentityProvider()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());

        domainService.create(ORGANIZATION_ID, ENVIRONMENT_ID, newDomain, new DefaultUser("username"))
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        verify(domainRepository, times(1)).findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain");
        verify(domainRepository, times(1)).create(argThat(argDomain -> argDomain.getVersion().equals(domain.getVersion())));
        verify(scopeService, times(io.gravitee.am.common.oidc.Scope.values().length)).create(any(), any(NewSystemScope.class));
        verify(certificateService).create(any());
        verify(eventService).create(any(), any());
        verify(membershipService).addOrUpdate(eq(ORGANIZATION_ID), any());
        verify(reporterService).createDefault(Reference.domain(domain.getId()));
        verify(reporterService).notifyInheritedReporters(any(), any(), any());
        verify(defaultIdentityProviderService).create(any());
        verify(auditService).report(argThat(builder -> {
            var audit = builder.build(OBJECT_MAPPER);
            return audit.getReferenceType().equals(ORGANIZATION) && audit.getReferenceId().equals(ORGANIZATION_ID);
        }));
    }

    @Test
    public void shouldCreate_withoutDefaultReporter() {
        var underTest = domainService.withCreateDefaultReporters(false);

        NewDomain newDomain = new NewDomain();
        newDomain.setName("my-domain");
        newDomain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setId("domain-id");
        domain.setVersion(DomainVersion.V2_0);
        domain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);
        when(dataPlaneRegistry.getDataPlanes()).thenReturn(List.of(new DataPlaneDescription(DataPlaneDescription.DEFAULT_DATA_PLANE_ID,"default","mongodb","test", "http://localhost:8092")));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain")).thenReturn(Maybe.empty());
        when(domainRepository.create(any(Domain.class))).thenReturn(Single.just(domain));
        when(scopeService.create(any(), any(NewSystemScope.class))).thenReturn(Single.just(new Scope()));
        when(certificateService.create(any())).thenReturn(Single.just(new Certificate()));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(membershipService.addOrUpdate(eq(ORGANIZATION_ID), any())).thenReturn(Single.just(new Membership()));
        when(roleService.findSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER, DOMAIN)).thenReturn(Maybe.just(new Role()));
        when(reporterService.notifyInheritedReporters(any(),any(),any())).thenReturn(Completable.complete());
        when(defaultIdentityProviderService.create(any())).thenReturn(Single.just(new IdentityProvider()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());

        underTest.create(ORGANIZATION_ID, ENVIRONMENT_ID, newDomain, new DefaultUser("username"))
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        verify(domainRepository, times(1)).findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain");
        verify(domainRepository, times(1)).create(argThat(argDomain -> argDomain.getVersion().equals(domain.getVersion())));
        verify(scopeService, times(io.gravitee.am.common.oidc.Scope.values().length)).create(any(), any(NewSystemScope.class));
        verify(certificateService).create(any());
        verify(eventService).create(any(), any());
        verify(membershipService).addOrUpdate(eq(ORGANIZATION_ID), any());
        verify(reporterService, never()).createDefault(any());
        verify(reporterService).notifyInheritedReporters(any(), any(), any());

        verify(auditService).report(argThat(builder -> {
            var audit = builder.build(OBJECT_MAPPER);
            return audit.getReferenceType().equals(ORGANIZATION) && audit.getReferenceId().equals(ORGANIZATION_ID);
        }));
    }

    @Test
    public void shouldCreate_withoutDefaultIdp() {
        var underTest = domainService.withCreateDefaultIdentityProvider(false);

        NewDomain newDomain = new NewDomain();
        newDomain.setName("my-domain");
        newDomain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setId("domain-id");
        domain.setVersion(DomainVersion.V2_0);
        domain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);
        when(dataPlaneRegistry.getDataPlanes()).thenReturn(List.of(new DataPlaneDescription(DataPlaneDescription.DEFAULT_DATA_PLANE_ID,"default","mongodb","test", "http://localhost:8092")));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain")).thenReturn(Maybe.empty());
        when(domainRepository.create(any(Domain.class))).thenReturn(Single.just(domain));
        when(scopeService.create(any(), any(NewSystemScope.class))).thenReturn(Single.just(new Scope()));
        when(certificateService.create(any())).thenReturn(Single.just(new Certificate()));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(membershipService.addOrUpdate(eq(ORGANIZATION_ID), any())).thenReturn(Single.just(new Membership()));
        when(reporterService.createDefault(any())).thenReturn(Single.just(new Reporter()));
        when(reporterService.notifyInheritedReporters(any(),any(),any())).thenReturn(Completable.complete());
        when(roleService.findSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER, DOMAIN)).thenReturn(Maybe.just(new Role()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());

        underTest.create(ORGANIZATION_ID, ENVIRONMENT_ID, newDomain, new DefaultUser("username"))
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        verify(domainRepository, times(1)).findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain");
        verify(domainRepository, times(1)).create(argThat(argDomain -> argDomain.getVersion().equals(domain.getVersion())));
        verify(scopeService, times(io.gravitee.am.common.oidc.Scope.values().length)).create(any(), any(NewSystemScope.class));
        verify(certificateService).create(any());
        verify(eventService).create(any(), any());
        verify(membershipService).addOrUpdate(eq(ORGANIZATION_ID), any());
        verify(reporterService).createDefault(Reference.domain(domain.getId()));
        verify(reporterService).notifyInheritedReporters(any(), any(), any());
        verify(defaultIdentityProviderService, never()).create(any());

        verify(auditService).report(argThat(builder -> {
            var audit = builder.build(OBJECT_MAPPER);
            return audit.getReferenceType().equals(ORGANIZATION) && audit.getReferenceId().equals(ORGANIZATION_ID);
        }));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewDomain newDomain = Mockito.mock(NewDomain.class);
        when(newDomain.getName()).thenReturn("my-domain");
        when(newDomain.getDataPlaneId()).thenReturn("default");
        when(dataPlaneRegistry.getDataPlanes()).thenReturn(List.of(new DataPlaneDescription(DataPlaneDescription.DEFAULT_DATA_PLANE_ID,"default","mongodb","test", "http://localhost:8092")));
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
        when(newDomain.getDataPlaneId()).thenReturn("default");
        when(dataPlaneRegistry.getDataPlanes()).thenReturn(List.of(new DataPlaneDescription(DataPlaneDescription.DEFAULT_DATA_PLANE_ID,"default","mongodb","test", "http://localhost:8092")));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, "my-domain")).thenReturn(Maybe.empty());
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));

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
        when(newDomain.getDataPlaneId()).thenReturn("default");
        when(dataPlaneRegistry.getDataPlanes()).thenReturn(List.of(new DataPlaneDescription(DataPlaneDescription.DEFAULT_DATA_PLANE_ID,"default","mongodb","test", "http://localhost:8092")));
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

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, "my-domain"), "my-domain", patchDomain, null)
                .test()
                .assertError(DomainNotFoundException.class)
                .assertNotComplete();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, never()).update(any(Domain.class));
    }

    @Test
    public void shouldPatch() {
        PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        Domain domain = new Domain();
        final var DOMAIN_ID = "my-domain";
        domain.setId(DOMAIN_ID);
        domain.setHrid(DOMAIN_ID);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName(DOMAIN_ID);
        domain.setPath("/test");
        domain.setVersion(DomainVersion.V2_0);
        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, domain.getHrid())).thenReturn(Maybe.just(domain));
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(domain));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, DOMAIN_ID), DOMAIN_ID, patchDomain, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(argThat(domainArg ->
                domainArg.getVersion().equals(domain.getVersion()) &&
                        domainArg.getReferenceId().equals(domain.getReferenceId()) &&
                        domainArg.getReferenceType().equals(domain.getReferenceType())
        ));
        verify(eventService, times(1)).create(any(), any());
        verify(auditService).report(argThat(builder -> {
            var audit = builder.build(OBJECT_MAPPER);
            return audit.getReferenceType().equals(DOMAIN) && audit.getReferenceId().equals(DOMAIN_ID);
        }));
        verify(auditService, never()).report(argThat(builder -> {
            var audit = builder.build(OBJECT_MAPPER);
            return audit.getReferenceType().equals(ORGANIZATION);
        }));
    }

    @Test
    public void shouldPatch_NameUpdated() {
        PatchDomain patchDomain = Mockito.mock(PatchDomain.class);

        Domain domain = new Domain();
        final var DOMAIN_ID = "my-domain";
        domain.setId(DOMAIN_ID);
        domain.setHrid(DOMAIN_ID);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName(DOMAIN_ID);
        domain.setPath("/test");

        Domain updatedDomain = new Domain(domain);
        updatedDomain.setName(UUID.randomUUID().toString());

        when(patchDomain.patch(any())).thenReturn(updatedDomain);
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(any(), anyString(), anyString())).thenReturn(Maybe.just(domain));
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(updatedDomain));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());
        doReturn(true).when(accountSettingsValidator).validate(any());

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, DOMAIN_ID), DOMAIN_ID, patchDomain, null)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any(), any());
        verify(auditService).report(argThat(builder -> {
            var audit = builder.build(OBJECT_MAPPER);
            return audit.getReferenceType().equals(DOMAIN) && audit.getReferenceId().equals(DOMAIN_ID);
        }));
        verify(auditService).report(argThat(builder -> {
            var audit = builder.build(OBJECT_MAPPER);
            return audit.getReferenceType().equals(ORGANIZATION) && audit.getReferenceId().equals(ORGANIZATION_ID);
        }));
    }

    @Test
    public void shouldPatch_EnabledChange() {
        PatchDomain patchDomain = Mockito.mock(PatchDomain.class);

        Domain domain = new Domain();
        final var DOMAIN_ID = "my-domain";
        domain.setId(DOMAIN_ID);
        domain.setHrid(DOMAIN_ID);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName(DOMAIN_ID);
        domain.setPath("/test");
        domain.setEnabled(true);

        Domain updatedDomain = new Domain(domain);
        updatedDomain.setEnabled(false);

        when(patchDomain.patch(any())).thenReturn(updatedDomain);
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(any(), anyString(), anyString())).thenReturn(Maybe.just(domain));
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(updatedDomain));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());
        doReturn(true).when(accountSettingsValidator).validate(any());

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, DOMAIN_ID), DOMAIN_ID, patchDomain, null)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any(), any());
        verify(auditService).report(argThat(builder -> {
            var audit = builder.build(OBJECT_MAPPER);
            return audit.getReferenceType().equals(DOMAIN) && audit.getReferenceId().equals(DOMAIN_ID);
        }));
        verify(auditService).report(argThat(builder -> {
            var audit = builder.build(OBJECT_MAPPER);
            return audit.getReferenceType().equals(ORGANIZATION) && audit.getReferenceId().equals(ORGANIZATION_ID);
        }));
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
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(domain));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());
        doReturn(true).when(accountSettingsValidator).validate(any());

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, "my-domain"), "my-domain", patchDomain, null)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any(), any());
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
        accountSettings.setResetPasswordCustomFormFields(List.of(formField));
        accountSettings.setResetPasswordCustomForm(true);
        domain.setAccountSettings(accountSettings);
        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, domain.getHrid())).thenReturn(Maybe.just(domain));
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(domain));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, "my-domain"), "my-domain", patchDomain, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any(), any());
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
        accountSettings.setResetPasswordCustomFormFields(List.of(formField));
        accountSettings.setResetPasswordCustomForm(true);
        domain.setAccountSettings(accountSettings);
        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));
        doReturn(false).when(accountSettingsValidator).validate(any());

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, "my-domain"), "my-domain", patchDomain, null)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertNotComplete()
                .assertError(InvalidParameterException.class);

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
        doReturn(true).when(accountSettingsValidator).validate(any());

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, "my-domain"), "my-domain", patchDomain, null)
                .test()
                .assertError(DomainAlreadyExistsException.class)
                .assertNotComplete();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, never()).update(any(Domain.class));
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldPatch_technicalException() {
        PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.error(TechnicalException::new));

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, "my-domain"), "my-domain", patchDomain, null)
                .test()
                .assertError(TechnicalManagementException.class)
                .assertNotComplete();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, never()).update(any(Domain.class));
    }

    @Test
    public void shouldNotPatch_newDataPlaneId() {
        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setDataPlaneId(Optional.of("new-data-plane-id"));
        Domain domain = new Domain();
        final var DOMAIN_ID = "my-domain";
        domain.setId(DOMAIN_ID);
        domain.setHrid(DOMAIN_ID);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName(DOMAIN_ID);
        domain.setPath("/test");
        domain.setVersion(DomainVersion.V2_0);
        domain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);

        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));

        TestObserver<Domain> testObserver = domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, DOMAIN_ID), DOMAIN_ID, patchDomain, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidParameterException.class);
    }

    @Test
    public void shouldPatch_theSameDataPlaneId(){
        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setDataPlaneId(Optional.of("default"));

        Domain domain = new Domain();
        final var DOMAIN_ID = "my-domain";
        domain.setId(DOMAIN_ID);
        domain.setHrid(DOMAIN_ID);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName(DOMAIN_ID);
        domain.setPath("/test");
        domain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);

        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(any(), anyString(), anyString())).thenReturn(Maybe.just(domain));
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());
        doReturn(true).when(accountSettingsValidator).validate(any());

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, DOMAIN_ID), DOMAIN_ID, patchDomain, null)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any(), any());
        verify(auditService).report(argThat(builder -> {
            var audit = builder.build(OBJECT_MAPPER);
            return audit.getReferenceType().equals(DOMAIN) && audit.getReferenceId().equals(DOMAIN_ID);
        }));
    }

    @Test
    public void shouldNotUpdate_newDataPlaneId() {
        Domain updateDomain = Mockito.mock(Domain.class);
        when(updateDomain.getDataPlaneId()).thenReturn("new-data-plane-id");
        Domain domain = new Domain();
        final var DOMAIN_ID = "my-domain";
        domain.setId(DOMAIN_ID);
        domain.setHrid(DOMAIN_ID);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName(DOMAIN_ID);
        domain.setPath("/test");
        domain.setVersion(DomainVersion.V2_0);
        domain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);

        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));

        TestObserver<Domain> testObserver = domainService.update(DOMAIN_ID, updateDomain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidParameterException.class);
    }

    @Test
    public void shouldUpdate_theSameDataPlaneId(){
        final var DOMAIN_ID = "my-domain";
        Domain updateDomain = new Domain();

        updateDomain.setId(DOMAIN_ID);
        updateDomain.setHrid(DOMAIN_ID);
        updateDomain.setReferenceType(ReferenceType.ENVIRONMENT);
        updateDomain.setReferenceId(ENVIRONMENT_ID);
        updateDomain.setName(DOMAIN_ID + "1");
        updateDomain.setPath("/test");
        updateDomain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);

        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setHrid(DOMAIN_ID);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName(DOMAIN_ID);
        domain.setPath("/test");
        domain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);

        Domain updatedDomain = new Domain(domain);
        updatedDomain.setName(UUID.randomUUID().toString());
        updatedDomain.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);

        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(any(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(updatedDomain));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());

        domainService.update(DOMAIN_ID, updateDomain)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any(), any());
    }

    @Test
    public void shouldDelete() {
        // protected resources
        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId("pr-1");
        when(protectedResourceService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(protectedResource));
        when(protectedResourceService.delete(any(Domain.class), eq("pr-1"), isNull(), any())).thenReturn(complete());
        Application mockApp1 = new Application();
        mockApp1.setId("client-1");

        Application mockApp2 = new Application();
        mockApp2.setId("client-2");

        Set<Application> mockApplications = new HashSet<>();
        mockApplications.add(mockApp1);
        mockApplications.add(mockApp2);

        final AlertTrigger alertTrigger = new AlertTrigger();
        alertTrigger.setId(ALERT_TRIGGER_ID);
        alertTrigger.setReferenceType(DOMAIN);
        alertTrigger.setReferenceId(DOMAIN_ID);

        final AlertNotifier alertNotifier = new AlertNotifier();
        alertNotifier.setId(ALERT_NOTIFIER_ID);
        alertNotifier.setReferenceType(DOMAIN);
        alertNotifier.setReferenceId(DOMAIN_ID);

        final AuthenticationDeviceNotifier authDeviceNotifier = new AuthenticationDeviceNotifier();
        authDeviceNotifier.setId(AUTH_DEVICE_ID);

        when(dataPlaneRegistry.getUserRepository(any())).thenReturn(userRepository);
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(domainRepository.delete(DOMAIN_ID)).thenReturn(complete());
        when(applicationService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(mockApplications));
        when(applicationService.delete(anyString(), any())).thenReturn(complete());
        when(certificate.getId()).thenReturn(CERTIFICATE_ID);
        when(certificateService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(certificate));
        when(certificateService.delete(anyString())).thenReturn(complete());
        when(identityProvider.getId()).thenReturn(IDP_ID);
        when(identityProviderService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(identityProvider));
        when(identityProviderService.delete(eq(DOMAIN_ID), anyString())).thenReturn(complete());
        when(extensionGrant.getId()).thenReturn(EXTENSION_GRANT_ID);
        when(extensionGrantService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(extensionGrant));
        when(extensionGrantService.delete(eq(DOMAIN_ID), anyString())).thenReturn(complete());
        when(role.getId()).thenReturn(ROLE_ID);
        when(roleService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.singleton(role)));
        when(roleService.delete(eq(DOMAIN), eq(DOMAIN_ID), anyString())).thenReturn(complete());
        when(userRepository.deleteByReference(any())).thenReturn(complete());
        when(userActivityService.deleteByDomain(any())).thenReturn(complete());
        when(scope.getId()).thenReturn(SCOPE_ID);
        when(scopeService.findByDomain(DOMAIN_ID, 0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page<>(Collections.singleton(scope), 0, 1)));
        when(scopeService.delete(any(), eq(SCOPE_ID), eq(true))).thenReturn(complete());
        when(group.getId()).thenReturn(GROUP_ID);
        when(domainGroupService.findAll(any())).thenReturn(Flowable.just(group));
        when(domainGroupService.delete(any(), anyString(), any())).thenReturn(complete());
        when(form.getId()).thenReturn(FORM_ID);
        when(formService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(form));
        when(formService.delete(eq(DOMAIN_ID), anyString())).thenReturn(complete());
        when(email.getId()).thenReturn(EMAIL_ID);
        when(emailTemplateService.findAll(DOMAIN, DOMAIN_ID)).thenReturn(Flowable.just(email));
        when(emailTemplateService.delete(anyString())).thenReturn(complete());
        when(reporter.getId()).thenReturn(REPORTER_ID);
        when(reporterService.findByReference(Reference.domain(DOMAIN_ID))).thenReturn(Flowable.just(reporter));
        when(reporterService.delete(anyString())).thenReturn(complete());
        when(flow.getId()).thenReturn(FLOW_ID);
        when(flowService.findAll(DOMAIN, DOMAIN_ID)).thenReturn(Flowable.just(flow));
        when(flowService.delete(anyString())).thenReturn(complete());
        when(membership.getId()).thenReturn(MEMBERSHIP_ID);
        when(membershipService.findByReference(DOMAIN_ID, DOMAIN)).thenReturn(Flowable.just(membership));
        when(membershipService.delete(anyString())).thenReturn(complete());
        when(factor.getId()).thenReturn(FACTOR_ID);
        when(factorService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(factor));
        when(factorService.delete(DOMAIN_ID, FACTOR_ID)).thenReturn(complete());
        when(resourceService.findByDomain(any())).thenReturn(Flowable.just(resource));
        when(resourceService.delete(any(), any())).thenReturn(complete());
        when(alertTriggerService.findByDomainAndCriteria(DOMAIN_ID, new AlertTriggerCriteria())).thenReturn(Flowable.just(alertTrigger));
        when(alertTriggerService.delete(eq(DOMAIN), eq(DOMAIN_ID), eq(ALERT_TRIGGER_ID), isNull())).thenReturn(complete());
        when(alertNotifierService.findByDomainAndCriteria(DOMAIN_ID, new AlertNotifierCriteria())).thenReturn(Flowable.just(alertNotifier));
        when(alertNotifierService.delete(eq(DOMAIN), eq(DOMAIN_ID), eq(ALERT_NOTIFIER_ID), isNull())).thenReturn(complete());
        when(authenticationDeviceNotifierService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(authDeviceNotifier));
        when(authenticationDeviceNotifierService.delete(any(), eq(AUTH_DEVICE_ID), any())).thenReturn(complete());
        when(i18nDictionaryService.findAll(DOMAIN, DOMAIN_ID)).thenReturn(Flowable.just(new I18nDictionary()));
        when(i18nDictionaryService.delete(eq(DOMAIN), eq(DOMAIN_ID), any(), any())).thenReturn(complete());
        when(passwordHistoryService.deleteByReference(any())).thenReturn(complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(themeService.findByReference(any(), any())).thenReturn(Maybe.empty());
        when(passwordPolicyService.deleteByReference(any(), any())).thenReturn(complete());
        when(reporterService.notifyInheritedReporters(any(), any(), any())).thenReturn(Completable.complete());
        when(deviceIdentifierService.deleteByDomain(any())).thenReturn(Completable.complete());
        when(serviceResourceService.deleteByDomain(any())).thenReturn(Completable.complete());
        when(certificateCredentialService.deleteByDomain(any())).thenReturn(Completable.complete());
        when(authorizationEngineService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());

        final var graviteeContext = GraviteeContext.defaultContext(DOMAIN_ID);
        final var testObserver = domainService.delete(graviteeContext, DOMAIN_ID, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNoErrors();
        testObserver.assertComplete();

        verify(applicationService, times(2)).delete(anyString(), any());
        verify(certificateService, times(1)).delete(CERTIFICATE_ID);
        verify(identityProviderService, times(1)).delete(DOMAIN_ID, IDP_ID);
        verify(extensionGrantService, times(1)).delete(DOMAIN_ID, EXTENSION_GRANT_ID);
        verify(roleService, times(1)).delete(eq(DOMAIN), eq(DOMAIN_ID), eq(ROLE_ID));
        verify(userRepository, times(1)).deleteByReference(any());
        verify(userActivityService, times(1)).deleteByDomain(any());
        verify(deviceIdentifierService, times(1)).deleteByDomain(DOMAIN_ID);
        verify(serviceResourceService, times(1)).deleteByDomain(DOMAIN_ID);
        verify(authorizationEngineService, times(1)).deleteByDomain(DOMAIN_ID);
        verify(scopeService, times(1)).delete(any(), eq(SCOPE_ID), eq(true));
        verify(domainGroupService, times(1)).delete(any(), eq(GROUP_ID), any());
        verify(formService, times(1)).delete(eq(DOMAIN_ID), eq(FORM_ID));
        verify(emailTemplateService, times(1)).delete(EMAIL_ID);
        verify(reporterService, times(1)).delete(REPORTER_ID);
        verify(flowService, times(1)).delete(FLOW_ID);
        verify(membershipService, times(1)).delete(MEMBERSHIP_ID);
        verify(factorService, times(1)).delete(DOMAIN_ID, FACTOR_ID);
        verify(eventService, times(1)).create(any(), any());
        verify(auditService).report(argThat(builder -> {
            var audit = builder.build(OBJECT_MAPPER);
            return audit.getReferenceType().equals(ORGANIZATION) && audit.getReferenceId().equals(graviteeContext.getOrganizationId());
        }));
    }

    @Test
    public void shouldDeleteWithoutRelatedData() {
        when(protectedResourceService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.empty());
        when(dataPlaneRegistry.getUserRepository(any())).thenReturn(userRepository);
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(domainRepository.delete(DOMAIN_ID)).thenReturn(complete());
        when(applicationService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptySet()));
        when(certificateService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.empty());
        when(identityProviderService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.empty());
        when(extensionGrantService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.empty());
        when(roleService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptySet()));
        when(scopeService.findByDomain(DOMAIN_ID, 0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page<>(Collections.emptySet(), 0, 1)));
        when(userRepository.deleteByReference(any())).thenReturn(complete());
        when(userActivityService.deleteByDomain(any())).thenReturn(complete());
        when(domainGroupService.findAll(any())).thenReturn(Flowable.empty());
        when(formService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.empty());
        when(emailTemplateService.findAll(DOMAIN, DOMAIN_ID)).thenReturn(Flowable.empty());
        when(reporterService.findByReference(Reference.domain(DOMAIN_ID))).thenReturn(Flowable.empty());
        when(flowService.findAll(DOMAIN, DOMAIN_ID)).thenReturn(Flowable.empty());
        when(membershipService.findByReference(DOMAIN_ID, DOMAIN)).thenReturn(Flowable.empty());
        when(factorService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.empty());
        when(resourceService.findByDomain(any())).thenReturn(Flowable.empty());
        when(alertTriggerService.findByDomainAndCriteria(DOMAIN_ID, new AlertTriggerCriteria())).thenReturn(Flowable.empty());
        when(alertNotifierService.findByDomainAndCriteria(DOMAIN_ID, new AlertNotifierCriteria())).thenReturn(Flowable.empty());
        when(authenticationDeviceNotifierService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.empty());
        when(i18nDictionaryService.findAll(DOMAIN, DOMAIN_ID)).thenReturn(Flowable.empty());
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(themeService.findByReference(any(), any())).thenReturn(Maybe.empty());
        when(passwordHistoryService.deleteByReference(any())).thenReturn(complete());
        when(passwordPolicyService.deleteByReference(any(), any())).thenReturn(complete());
        when(reporterService.notifyInheritedReporters(any(), any(), any())).thenReturn(Completable.complete());
        when(deviceIdentifierService.deleteByDomain(any())).thenReturn(Completable.complete());
        when(serviceResourceService.deleteByDomain(any())).thenReturn(Completable.complete());
        when(certificateCredentialService.deleteByDomain(any())).thenReturn(Completable.complete());
        when(authorizationEngineService.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());

        var testObserver = domainService.delete(GraviteeContext.defaultContext(DOMAIN_ID), DOMAIN_ID, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, never()).delete(anyString(), any());
        verify(certificateService, never()).delete(anyString());
        verify(identityProviderService, never()).delete(anyString(), anyString());
        verify(extensionGrantService, never()).delete(anyString(), anyString());
        verify(roleService, never()).delete(eq(DOMAIN), eq(DOMAIN_ID), anyString());
        verify(scopeService, never()).delete(any(), anyString(), anyBoolean());
        verify(formService, never()).delete(anyString(), anyString());
        verify(emailTemplateService, never()).delete(anyString());
        verify(reporterService, never()).delete(anyString());
        verify(flowService, never()).delete(anyString());
        verify(membershipService, never()).delete(anyString());
        verify(factorService, never()).delete(anyString(), anyString());
        verify(alertTriggerService, never()).delete(any(ReferenceType.class), anyString(), anyString(), any(io.gravitee.am.identityprovider.api.User.class));
        verify(alertNotifierService, never()).delete(any(ReferenceType.class), anyString(), anyString(), any(io.gravitee.am.identityprovider.api.User.class));
        verify(eventService, times(1)).create(any(), any());
    }

    @Test
    public void shouldNotDeleteBecauseDoesntExist() {
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.empty());

        domainService.delete(GraviteeContext.defaultContext(DOMAIN_ID), DOMAIN_ID, null).test().assertError(DomainNotFoundException.class).assertNotComplete();
    }

    @Test
    public void shouldDelete_technicalException() {
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.error(TechnicalException::new));

        domainService.delete(GraviteeContext.defaultContext(DOMAIN_ID), DOMAIN_ID, null).test().assertError(TechnicalManagementException.class).assertNotComplete();
    }

    @Test
    public void shouldDelete2_technicalException() {
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(domain));
        when(applicationService.findByDomain(DOMAIN_ID)).thenReturn(Single.error(TechnicalException::new));

        domainService.delete(GraviteeContext.defaultContext(DOMAIN_ID), DOMAIN_ID, null).test().assertError(TechnicalManagementException.class).assertNotComplete();
    }

    @Test
    public void shouldFindByCriteria() {

        final DomainCriteria criteria = new DomainCriteria();
        final Domain domain = new Domain();

        when(domainRepository.findAllByCriteria(eq(criteria))).thenReturn(Flowable.just(domain));

        domainService.findAllByCriteria(criteria).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertValue(domain);
    }

    @Test
    public void shouldPatch_corsSettings() {
        final PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        final Domain domain = new Domain();
        domain.setId("my-domain");
        domain.setHrid("my-domain");
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName("my-domain");
        domain.setPath("/test");

        domain.setCorsSettings(getCorsSettings(Set.of("*")));

        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, domain.getHrid())).thenReturn(Maybe.just(domain));
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(domain));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());
        doReturn(true).when(accountSettingsValidator).validate(any());

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, "my-domain"), "my-domain", patchDomain, null).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any(), any());
    }

    @Test
    public void shouldPatchCorsSettingsWithMultipleAllowedOrigins() {
        final PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        final Domain domain = new Domain();
        domain.setId("my-domain");
        domain.setHrid("my-domain");
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName("my-domain");
        domain.setPath("/test");

        domain.setCorsSettings(getCorsSettings(Set.of("https://mydomain.com", "(http|https).*.mydomain.com")));

        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, domain.getHrid())).thenReturn(Maybe.just(domain));
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(domain));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());
        doReturn(true).when(accountSettingsValidator).validate(any());

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, "my-domain"), "my-domain", patchDomain, null).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any(), any());
    }

    @Test
    public void shouldPathDomainWithEmptyAllowedOriginsWhenCorsIsDisabled() {
        final PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        final Domain domain = new Domain();
        domain.setId("my-domain");
        domain.setHrid("my-domain");
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName("my-domain");
        domain.setCorsSettings(getCorsSettings(Set.of(""), false));

        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, domain.getHrid())).thenReturn(Maybe.just(domain));
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(new Environment()));
        when(domainReadService.listAll()).thenReturn(Flowable.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(domain));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());
        doReturn(true).when(accountSettingsValidator).validate(any());

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, "my-domain"), "my-domain", patchDomain, null).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, times(1)).update(any(Domain.class));
        verify(eventService, times(1)).create(any(), any());
    }

    @Test
    public void shouldThrowOnPathDomainWithEmptyAllowedOrigins() {
        final PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        final Domain domain = new Domain();
        domain.setId("my-domain");
        domain.setHrid("my-domain");
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName("my-domain");
        domain.setPath("/test");

        domain.setCorsSettings(getCorsSettings(Set.of()));

        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));
        doReturn(true).when(accountSettingsValidator).validate(any());

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, "my-domain"), "my-domain", patchDomain, null).test().awaitDone(10, TimeUnit.SECONDS)
                .assertFailure(InvalidDomainException.class);

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, never()).update(any(Domain.class));
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldThrowOnPathDomainWithIncorrectAllowedOriginPattern() {
        final PatchDomain patchDomain = Mockito.mock(PatchDomain.class);
        final Domain domain = new Domain();
        domain.setId("my-domain");
        domain.setHrid("my-domain");
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        domain.setName("my-domain");
        domain.setPath("/test");

        domain.setCorsSettings(getCorsSettings(Set.of("[")));

        when(patchDomain.patch(any())).thenReturn(domain);
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(domain));

        domainService.patch(new GraviteeContext(ORGANIZATION_ID, ENVIRONMENT_ID, "my-domain"), "my-domain", patchDomain, null).test().awaitDone(10, TimeUnit.SECONDS)
                .assertFailure(InvalidDomainException.class);

        verify(domainRepository, times(1)).findById(anyString());
        verify(domainRepository, never()).update(any(Domain.class));
        verify(eventService, never()).create(any());
    }

    @Test
    public void should_InvalidWebAuthnConfigurationException_origin_null() {
        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setName("test");
        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        when(domainRepository.findById(anyString())).thenReturn(Maybe.just(domain));

        domain.setWebAuthnSettings(webAuthnSettings);
        domainService.update("any-id", domain).test().assertError(InvalidWebAuthnConfigurationException.class).assertNotComplete();
    }

    @Test
    public void should_InvalidWebAuthnConfigurationException_origin_empty_string() {
        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setName("test");
        final String emptyString = "    ";
        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        webAuthnSettings.setOrigin(emptyString);
        when(domainRepository.findById(anyString())).thenReturn(Maybe.just(domain));

        domain.setWebAuthnSettings(webAuthnSettings);
        domainService.update("any-id", domain).test().assertError(InvalidWebAuthnConfigurationException.class).assertNotComplete();
    }

    @Test
    public void shouldNot_InvalidWebAuthnConfigurationException() {
        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setName("test");
        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        domain.setWebAuthnSettings(webAuthnSettings);
        webAuthnSettings.setOrigin("http://someorigin.com");
        when(domainRepository.findById(anyString())).thenReturn(Maybe.just(domain));
        when(domainRepository.findByHrid(any(), any(), any())).thenReturn(Maybe.empty());
        when(domainRepository.update(any(Domain.class))).thenReturn(Single.just(domain));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(environmentService.findById(any())).thenReturn(Single.just(new Environment()));
        doReturn(Single.just(List.of()).ignoreElement()).when(domainValidator).validate(any(), any());
        doReturn(Single.just(List.of()).ignoreElement()).when(virtualHostValidator).validateDomainVhosts(any(), any());
        when(domainReadService.listAll()).thenReturn(Flowable.just(domain));

        domainService.update("any-id", domain).test().assertComplete().assertNoErrors();

        verify(domainRepository, times(1)).update(any(Domain.class));
    }


    @Test
    public void shouldGetEntrypoint_entrypoint1() {

        final Entrypoint entrypoint = new Entrypoint();
        entrypoint.setId(ENTRYPOINT_ID1);
        entrypoint.setName("entrypoint-1-name");
        entrypoint.setTags(Arrays.asList(TAG_ID2));
        entrypoint.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint entrypoint2 = new Entrypoint();
        entrypoint2.setId(ENTRYPOINT_ID2);
        entrypoint2.setName("entrypoint-2-name");
        entrypoint2.setTags(Collections.emptyList());
        entrypoint2.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint defaultEntrypoint = new Entrypoint();
        defaultEntrypoint.setId(ENTRYPOINT_ID_DEFAULT);
        defaultEntrypoint.setName("Default");
        defaultEntrypoint.setTags(Collections.emptyList());
        defaultEntrypoint.setDefaultEntrypoint(true);
        defaultEntrypoint.setOrganizationId(ORGANIZATION_ID);

        Domain mockDomain = new Domain();
        mockDomain.setId(DOMAIN_ID);
        mockDomain.setTags(new HashSet<>(Arrays.asList(TAG_ID1, TAG_ID2)));

        doReturn(Flowable.just(entrypoint, entrypoint2, defaultEntrypoint)).when(entrypointService).findAll(ORGANIZATION_ID);

        final var subscriber = domainService.listEntryPoint(mockDomain, ORGANIZATION_ID).test();
        subscriber.assertValue(entrypoints -> entrypoints.size() == 1 &&
                entrypoints.stream().anyMatch(e -> e.getId().equals(ENTRYPOINT_ID1)));
    }

    @Test
    public void shouldGetEntrypoint_default() {

        final Entrypoint entrypoint = new Entrypoint();
        entrypoint.setId(ENTRYPOINT_ID1);
        entrypoint.setName("entrypoint-1-name");
        entrypoint.setTags(Collections.emptyList());
        entrypoint.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint entrypoint2 = new Entrypoint();
        entrypoint2.setId(ENTRYPOINT_ID2);
        entrypoint2.setName("entrypoint-2-name");
        entrypoint2.setTags(Collections.emptyList());
        entrypoint2.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint defaultEntrypoint = new Entrypoint();
        defaultEntrypoint.setId(ENTRYPOINT_ID_DEFAULT);
        defaultEntrypoint.setName("Default");
        defaultEntrypoint.setTags(Collections.emptyList());
        defaultEntrypoint.setDefaultEntrypoint(true);
        defaultEntrypoint.setOrganizationId(ORGANIZATION_ID);
        defaultEntrypoint.setUrl("http://localhost:8092");

        Domain mockDomain = new Domain();
        mockDomain.setId(DOMAIN_ID);
        mockDomain.setTags(new HashSet<>());

        when(dataPlaneRegistry.getDescription(mockDomain)).thenReturn(new DataPlaneDescription("dp1", "legacy", "mongodb", "baseProp", null));
        doReturn(Flowable.just(entrypoint, entrypoint2, defaultEntrypoint)).when(entrypointService).findAll(ORGANIZATION_ID);

        final var subscriber = domainService.listEntryPoint(mockDomain, ORGANIZATION_ID).test();
        subscriber.assertValue(entrypoints -> entrypoints.size() == 1 &&
                entrypoints.stream().anyMatch(e -> e.getId().equals(ENTRYPOINT_ID_DEFAULT)) &&
                entrypoints.get(0).getUrl().equals(defaultEntrypoint.getUrl()));
    }

    @Test
    public void shouldGetEntrypoint_default_overrideUrl() {

        final Entrypoint entrypoint = new Entrypoint();
        entrypoint.setId(ENTRYPOINT_ID1);
        entrypoint.setName("entrypoint-1-name");
        entrypoint.setTags(Collections.emptyList());
        entrypoint.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint entrypoint2 = new Entrypoint();
        entrypoint2.setId(ENTRYPOINT_ID2);
        entrypoint2.setName("entrypoint-2-name");
        entrypoint2.setTags(Collections.emptyList());
        entrypoint2.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint defaultEntrypoint = new Entrypoint();
        defaultEntrypoint.setId(ENTRYPOINT_ID_DEFAULT);
        defaultEntrypoint.setName("Default");
        defaultEntrypoint.setTags(Collections.emptyList());
        defaultEntrypoint.setDefaultEntrypoint(true);
        defaultEntrypoint.setOrganizationId(ORGANIZATION_ID);
        defaultEntrypoint.setUrl("http://localhost:8092");

        Domain mockDomain = new Domain();
        mockDomain.setId(DOMAIN_ID);
        mockDomain.setTags(new HashSet<>());

        DataPlaneDescription dataPlaneDescription = new DataPlaneDescription("dp1", "legacy", "mongodb", "baseProp", "http://dataplane:8092");
        when(dataPlaneRegistry.getDescription(mockDomain)).thenReturn(dataPlaneDescription);
        doReturn(Flowable.just(entrypoint, entrypoint2, defaultEntrypoint)).when(entrypointService).findAll(ORGANIZATION_ID);

        final var subscriber = domainService.listEntryPoint(mockDomain, ORGANIZATION_ID).test();
        subscriber.assertValue(entrypoints -> entrypoints.size() == 1 &&
                entrypoints.stream().anyMatch(e -> e.getId().equals(ENTRYPOINT_ID_DEFAULT)) &&
                entrypoints.get(0).getUrl().equals(dataPlaneDescription.gatewayUrl()));
    }

    @Test
    public void shouldGetEntrypoints_Entrypoint1And2() {

        final Entrypoint entrypoint = new Entrypoint();
        entrypoint.setId(ENTRYPOINT_ID1);
        entrypoint.setName("entrypoint-1-name");
        entrypoint.setTags(Arrays.asList(TAG_ID1));
        entrypoint.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint entrypoint2 = new Entrypoint();
        entrypoint2.setId(ENTRYPOINT_ID2);
        entrypoint2.setName("entrypoint-2-name");
        entrypoint2.setTags(Arrays.asList(TAG_ID2));
        entrypoint2.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint defaultEntrypoint = new Entrypoint();
        defaultEntrypoint.setId(ENTRYPOINT_ID_DEFAULT);
        defaultEntrypoint.setName("Default");
        defaultEntrypoint.setTags(Collections.emptyList());
        defaultEntrypoint.setDefaultEntrypoint(true);
        defaultEntrypoint.setOrganizationId(ORGANIZATION_ID);

        Domain mockDomain = new Domain();
        mockDomain.setId(DOMAIN_ID);
        mockDomain.setTags(new HashSet<>(Arrays.asList(TAG_ID1, TAG_ID2)));

        doReturn(Flowable.just(entrypoint, entrypoint2, defaultEntrypoint)).when(entrypointService).findAll(ORGANIZATION_ID);

        final var subscriber = domainService.listEntryPoint(mockDomain, ORGANIZATION_ID).test();
        subscriber.assertValue(entrypoints -> entrypoints.size() == 2 &&
                entrypoints.stream().anyMatch(e -> e.getId().equals(ENTRYPOINT_ID1)) &&
                entrypoints.stream().anyMatch(e -> e.getId().equals(ENTRYPOINT_ID2)));
    }

    private static CorsSettings getCorsSettings(Set<String> allowedOrigins) {
        return getCorsSettings(allowedOrigins, true);
    }

    private static CorsSettings getCorsSettings(Set<String> allowedOrigins, boolean enabled) {
        final CorsSettings corsSettings = new CorsSettings();
        corsSettings.setEnabled(enabled);
        corsSettings.setMaxAge(50);
        corsSettings.setAllowedMethods(Set.of("GET", "POST"));
        corsSettings.setAllowedOrigins(allowedOrigins);
        return corsSettings;
    }
}

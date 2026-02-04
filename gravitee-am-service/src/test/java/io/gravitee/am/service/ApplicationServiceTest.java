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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.ClientType;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.account.FormField;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.service.exception.ClientAlreadyExistsException;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.InvalidRedirectUriException;
import io.gravitee.am.service.exception.InvalidTargetUrlException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.OAuthClientUniquenessValidator;
import io.gravitee.am.service.impl.SecretService;
import io.gravitee.am.service.impl.ApplicationServiceImpl;
import io.gravitee.am.service.model.NewApplication;
import io.gravitee.am.service.model.PatchApplication;
import io.gravitee.am.service.model.PatchApplicationFactorSettings;
import io.gravitee.am.service.model.PatchApplicationIdentityProvider;
import io.gravitee.am.service.model.PatchApplicationOAuthSettings;
import io.gravitee.am.service.model.PatchApplicationSettings;
import io.gravitee.am.service.model.PatchFactorSettings;
import io.gravitee.am.service.model.PatchMFASettings;
import io.gravitee.am.service.spring.application.ApplicationSecretConfig;
import io.gravitee.am.service.spring.application.SecretHashAlgorithm;
import io.gravitee.am.service.validators.accountsettings.AccountSettingsValidator;
import io.gravitee.am.service.validators.claims.ApplicationTokenCustomClaimsValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.ConfigurableEnvironment;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ApplicationServiceTest {

    public static final String ORGANIZATION_ID = "DEFAULT";
    public static final String CLIENT_ID = "client_id";

    @InjectMocks
    private final ApplicationService applicationService = new ApplicationServiceImpl();

    @Mock
    private OAuthClientUniquenessValidator oAuthClientUniquenessValidator;

    @Mock
    private AccountSettingsValidator accountSettingsValidator;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationTemplateManager applicationTemplateManager;

    @Mock
    private DomainReadService domainService;

    @Mock
    private EventService eventService;

    @Mock
    private ScopeService scopeService;

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private FormService formService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private AuditService auditService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private RoleService roleService;

    @Mock
    private CertificateService certificateService;

    @Spy
    private ApplicationSecretConfig applicationSecretConfig = new ApplicationSecretConfig("BCrypt", mock(ConfigurableEnvironment.class));

    @Spy
    private SecretService secretService = new SecretService();

    @Mock
    private BiFunction<Domain, Application, Completable> revokeToken;

    @Mock
    private User principal;

    @Spy
    private ApplicationTokenCustomClaimsValidator customClaimsValidator = new ApplicationTokenCustomClaimsValidator();

    private final static Domain DOMAIN = new Domain("domain1");

    @Before
    public void setUp() throws Exception {
        when(oAuthClientUniquenessValidator.checkClientIdUniqueness(any(), any())).thenReturn(Completable.complete());
    }

    @Test
    public void shouldFindById() {
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(new Application()));
        TestObserver testObserver = applicationService.findById("my-client").test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingClient() {
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.empty());
        TestObserver testObserver = applicationService.findById("my-client").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        applicationService.findById("my-client").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainAndClientId() {
        when(applicationRepository.findByDomainAndClientId(DOMAIN.getId(), "my-client")).thenReturn(Maybe.just(new Application()));
        TestObserver testObserver = applicationService.findByDomainAndClientId(DOMAIN.getId(), "my-client").test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindByDomainAndClientId_noApp() {
        when(applicationRepository.findByDomainAndClientId(DOMAIN.getId(), "my-client")).thenReturn(Maybe.empty());
        TestObserver testObserver = applicationService.findByDomainAndClientId(DOMAIN.getId(), "my-client").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindByDomainAndClientId_technicalException() {
        when(applicationRepository.findByDomainAndClientId(DOMAIN.getId(), "my-client")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        applicationService.findByDomainAndClientId(DOMAIN.getId(), "my-client").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(applicationRepository.findByDomain(DOMAIN.getId(), 0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page<>(Collections.singleton(new Application()), 0, 1)));
        TestObserver<Set<Application>> testObserver = applicationService.findByDomain(DOMAIN.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(applications -> applications.size() == 1);
    }

    @Test
    public void shouldFindByDomainAndApplicationIds() {
        when(applicationRepository.findByDomain(eq(DOMAIN.getId()), any(), eq(0), eq(10)))
                .thenReturn(Single.just(new Page<>(Collections.singleton(new Application()), 0, 1)));
        TestObserver<Page<Application>> testObserver = applicationService.findByDomain(DOMAIN.getId(), List.of("id1"),0, 10).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(applications -> applications.getData().size() == 1);
    }

    @Test
    public void shouldSearchByDomainAndApplicationIds() {
        when(applicationRepository.search(eq(DOMAIN.getId()), any(), eq("query"), eq(0), eq(10)))
                .thenReturn(Single.just(new Page<>(Collections.singleton(new Application()), 0, 1)));
        TestObserver<Page<Application>> testObserver = applicationService.search(DOMAIN.getId(), List.of("id1"), "query", 0, 10).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(applications -> applications.getData().size() == 1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(applicationRepository.findByDomain(DOMAIN.getId(), 0, Integer.MAX_VALUE)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        applicationService.findByDomain(DOMAIN.getId()).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainPagination() {
        Page pageClients = new Page(Collections.singleton(new Application()), 1, 1);
        when(applicationRepository.findByDomain(DOMAIN.getId(), 1, 1)).thenReturn(Single.just(pageClients));
        TestObserver<Page<Application>> testObserver = applicationService.findByDomain(DOMAIN.getId(), 1, 1).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.getData().size() == 1);
    }

    @Test
    public void shouldFindByDomainPagination_technicalException() {
        when(applicationRepository.findByDomain(DOMAIN.getId(), 1, 1)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        applicationService.findByDomain(DOMAIN.getId(), 1, 1).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByIdentityProvider() {
        when(applicationRepository.findByIdentityProvider("client-idp")).thenReturn(Flowable.just(new Application()));
        TestSubscriber<Application> testSubscriber = applicationService.findByIdentityProvider("client-idp").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindByIdentityProvider_technicalException() {
        when(applicationRepository.findByIdentityProvider("client-idp")).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = applicationService.findByIdentityProvider("client-idp").test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldFindByCertificate() {
        when(applicationRepository.findByCertificate("client-certificate")).thenReturn(Flowable.just(new Application()));
        TestSubscriber<Application> testObserver = applicationService.findByCertificate("client-certificate").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindByCertificate_technicalException() {
        when(applicationRepository.findByCertificate("client-certificate")).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSub = applicationService.findByCertificate("client-certificate").test();

        testSub.assertError(TechnicalManagementException.class);
        testSub.assertNotComplete();
    }

    @Test
    public void shouldFindByExtensionGrant() {
        when(applicationRepository.findByDomainAndExtensionGrant(DOMAIN.getId(), "client-extension-grant")).thenReturn(Flowable.just(new Application()));
        TestObserver<Set<Application>> testObserver = applicationService.findByDomainAndExtensionGrant(DOMAIN.getId(), "client-extension-grant").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.size() == 1);
    }

    @Test
    public void shouldFindByExtensionGrant_technicalException() {
        when(applicationRepository.findByDomainAndExtensionGrant(DOMAIN.getId(), "client-extension-grant")).thenReturn(Flowable.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        applicationService.findByDomainAndExtensionGrant(DOMAIN.getId(), "client-extension-grant").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFetchAll() {
        when(applicationRepository.findAll(0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page(Collections.singleton(new Application()), 0, 1)));
        TestObserver<Set<Application>> testObserver = applicationService.fetchAll().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.size() == 1);
    }

    @Test
    public void shouldFetchAll_technicalException() {
        when(applicationRepository.findAll(0, Integer.MAX_VALUE)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        applicationService.fetchAll().subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFetchAllPagination() {
        Page pageClients = new Page(Collections.singleton(new Application()), 1, 1);
        when(applicationRepository.findAll(1, 1)).thenReturn(Single.just(pageClients));
        TestObserver<Page<Application>> testObserver = applicationService.findAll(1, 1).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.getData().size() == 1);
    }

    @Test
    public void shouldFetchAllPagination_technicalException() {
        when(applicationRepository.findAll(1, 1)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        applicationService.findAll(1, 1).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalClientsByDomain() {
        when(applicationRepository.countByDomain(DOMAIN.getId())).thenReturn(Single.just(1l));
        TestObserver<Long> testObserver = applicationService.countByDomain(DOMAIN.getId()).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(totalClient -> totalClient.longValue() == 1l);
    }

    @Test
    public void shouldFindTotalClientsByDomain_technicalException() {
        when(applicationRepository.countByDomain(DOMAIN.getId())).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        applicationService.countByDomain(DOMAIN.getId()).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalClients() {
        when(applicationRepository.count()).thenReturn(Single.just(1l));
        TestObserver<Long> testObserver = applicationService.count().test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(totalClient -> totalClient.longValue() == 1l);
    }

    @Test
    public void shouldFindTotalClients_technicalException() {
        when(applicationRepository.count()).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        applicationService.count().subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate_noCertificate() {
        NewApplication newClient = prepareCreateServiceApp();
        when(certificateService.findByDomain(DOMAIN.getId())).thenReturn(Flowable.empty());
        doAnswer(invocation -> {
            Application mock = invocation.getArgument(0);
            mock.getSettings().getOauth().setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            mock.getSettings().getOauth().setClientId(CLIENT_ID);
            mock.getSettings().getOauth().setClientSecret("client_secret");
            return mock;
        }).when(applicationTemplateManager).apply(any());

        DefaultUser user = new DefaultUser("username");
        user.setAdditionalInformation(Collections.singletonMap(Claims.ORGANIZATION, ORGANIZATION_ID));

        TestObserver<Application> testObserver = applicationService.create(DOMAIN, newClient, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).create(any(Application.class));
        verify(membershipService).addOrUpdate(eq(ORGANIZATION_ID), any());
    }

    @Test
    public void shouldCreate_WithClientSecretHash() {
        NewApplication newClient = prepareCreateApp(true);
        when(certificateService.findByDomain(DOMAIN.getId())).thenReturn(Flowable.empty());
        doAnswer(invocation -> {
            Application mock = invocation.getArgument(0);
            mock.getSettings().getOauth().setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            mock.getSettings().getOauth().setClientId(CLIENT_ID);
            mock.getSettings().getOauth().setClientSecret("client_secret");
            return mock;
        }).when(applicationTemplateManager).apply(any());

        DefaultUser user = new DefaultUser("username");
        user.setAdditionalInformation(Collections.singletonMap(Claims.ORGANIZATION, ORGANIZATION_ID));

        TestObserver<Application> testObserver = applicationService.create(DOMAIN, newClient, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).create(argThat(app ->
                app.getSecretSettings() != null &&
                        app.getSecretSettings().size() == 1 &&
                        !isEmpty(app.getSecretSettings().get(0).getId()) &&
                        app.getSecretSettings().get(0).getAlgorithm().equals(SecretHashAlgorithm.BCRYPT.name()) &&
                        app.getSecretSettings().get(0).getProperties().containsKey("rounds")
        ));

        verify(membershipService).addOrUpdate(eq(ORGANIZATION_ID), any());
    }

    public void shouldCreate_AppWithRedirectUri() {
        NewApplication newClient = prepareCreateApp(true);
        when(certificateService.findByDomain(DOMAIN.getId())).thenReturn(Flowable.empty());

        DefaultUser user = new DefaultUser("username");
        user.setAdditionalInformation(Collections.singletonMap(Claims.ORGANIZATION, ORGANIZATION_ID));

        TestObserver<Application> testObserver = applicationService.create(DOMAIN, newClient, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).findByDomainAndClientId(DOMAIN.getId(), null);
        verify(applicationRepository, times(1)).create(any(Application.class));

        verify(membershipService).addOrUpdate(eq(ORGANIZATION_ID), any());
    }

    @Test
    public void shouldCreate_AppWithoutRedirectUri() {
        NewApplication newClient = prepareCreateApp(false);

        DefaultUser user = new DefaultUser("username");
        user.setAdditionalInformation(Collections.singletonMap(Claims.ORGANIZATION, ORGANIZATION_ID));

        TestObserver<Application> testObserver = applicationService.create(DOMAIN, newClient, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidRedirectUriException.class);

        verify(applicationRepository, never()).create(any(Application.class));
        verify(membershipService, never()).addOrUpdate(eq(ORGANIZATION_ID), any());
    }

    @Test
    public void shouldCreate_withSystemCertificate() {
        NewApplication newClient = prepareCreateServiceApp();

        final LocalDateTime now = LocalDateTime.now();

        final Certificate firstDefaultCert = new Certificate();
        firstDefaultCert.setId("first-cert");
        firstDefaultCert.setSystem(true);
        firstDefaultCert.setName("Default");
        firstDefaultCert.setCreatedAt(new Date(now.plusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli()));

        final Certificate lastestDefaultCert = new Certificate();
        lastestDefaultCert.setId("latest-cert");
        lastestDefaultCert.setSystem(true);
        lastestDefaultCert.setName("Default 123456");
        lastestDefaultCert.setCreatedAt(new Date(now.plusDays(2).toInstant(ZoneOffset.UTC).toEpochMilli()));

        final Certificate customCert = new Certificate();
        customCert.setId("custom-cert");
        customCert.setSystem(false);
        customCert.setName("Custom");
        customCert.setCreatedAt(new Date(now.plusDays(3).toInstant(ZoneOffset.UTC).toEpochMilli()));

        when(certificateService.findByDomain(DOMAIN.getId())).thenReturn(Flowable.just(lastestDefaultCert, firstDefaultCert, customCert));

        DefaultUser user = new DefaultUser("username");
        user.setAdditionalInformation(Collections.singletonMap(Claims.ORGANIZATION, ORGANIZATION_ID));

        doAnswer(invocation -> {
            Application mock = invocation.getArgument(0);
            mock.getSettings().getOauth().setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            mock.getSettings().getOauth().setClientId(CLIENT_ID);
            mock.getSettings().getOauth().setClientSecret("client_secret");
            return mock;
        }).when(applicationTemplateManager).apply(any());

        TestObserver<Application> testObserver = applicationService.create(DOMAIN, newClient, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).create(any(Application.class));
        verify(applicationRepository, times(1)).create(argThat(app -> app.getCertificate().equalsIgnoreCase(lastestDefaultCert.getId())));
        verify(membershipService).addOrUpdate(eq(ORGANIZATION_ID), any());
    }

    @Test
    public void shouldCreate_withLegacyCertificate() {
        NewApplication newClient = prepareCreateServiceApp();

        final LocalDateTime now = LocalDateTime.now();

        final Certificate firstDefaultCert = new Certificate();
        firstDefaultCert.setId("first-cert");
        firstDefaultCert.setSystem(false);
        firstDefaultCert.setName("Default");
        firstDefaultCert.setExpiresAt(new Date(now.plusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli()));

        final Certificate lastestDefaultCert = new Certificate();
        lastestDefaultCert.setId("latest-cert");
        lastestDefaultCert.setSystem(false);
        lastestDefaultCert.setName("Default 123456");
        lastestDefaultCert.setExpiresAt(new Date(now.plusDays(2).toInstant(ZoneOffset.UTC).toEpochMilli()));

        final Certificate customCert = new Certificate();
        customCert.setId("custom-cert");
        customCert.setSystem(false);
        customCert.setName("Custom");
        customCert.setExpiresAt(new Date(now.plusDays(3).toInstant(ZoneOffset.UTC).toEpochMilli()));

        when(certificateService.findByDomain(DOMAIN.getId())).thenReturn(Flowable.just(lastestDefaultCert, firstDefaultCert, customCert));

        DefaultUser user = new DefaultUser("username");
        user.setAdditionalInformation(Collections.singletonMap(Claims.ORGANIZATION, ORGANIZATION_ID));

        doAnswer(invocation -> {
            Application mock = invocation.getArgument(0);
            mock.getSettings().getOauth().setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            mock.getSettings().getOauth().setClientId(CLIENT_ID);
            mock.getSettings().getOauth().setClientSecret("client_secret");
            return mock;
        }).when(applicationTemplateManager).apply(any());
        when(applicationRepository.create(any(Application.class))).thenAnswer(args -> Single.just(args.getArguments()[0]));

        TestObserver<Application> testObserver = applicationService.create(DOMAIN, newClient, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).create(any(Application.class));
        verify(applicationRepository, times(1)).create(argThat(app -> app.getCertificate().equalsIgnoreCase(firstDefaultCert.getId())));
        verify(membershipService).addOrUpdate(eq(ORGANIZATION_ID), any());
    }

    @Test
    public void shouldCreate_withFirstCertificate() {
        NewApplication newClient = prepareCreateServiceApp();

        final LocalDateTime now = LocalDateTime.now();

        final Certificate firstDefaultCert = new Certificate();
        firstDefaultCert.setId("first-cert");
        firstDefaultCert.setSystem(false);
        firstDefaultCert.setName("Custom name for default");
        firstDefaultCert.setExpiresAt(new Date(now.plusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli()));

        final Certificate customCert = new Certificate();
        customCert.setId("custom-cert");
        customCert.setSystem(false);
        customCert.setName("Custom");
        customCert.setExpiresAt(new Date(now.plusDays(3).toInstant(ZoneOffset.UTC).toEpochMilli()));

        when(certificateService.findByDomain(DOMAIN.getId())).thenReturn(Flowable.just(customCert, firstDefaultCert));

        DefaultUser user = new DefaultUser("username");
        user.setAdditionalInformation(Collections.singletonMap(Claims.ORGANIZATION, ORGANIZATION_ID));

        doAnswer(invocation -> {
            Application mock = invocation.getArgument(0);
            mock.getSettings().getOauth().setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            mock.getSettings().getOauth().setClientId(CLIENT_ID);
            mock.getSettings().getOauth().setClientSecret("client_secret");
            return mock;
        }).when(applicationTemplateManager).apply(any());

        TestObserver<Application> testObserver = applicationService.create(DOMAIN, newClient, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).create(any(Application.class));
        verify(applicationRepository, times(1)).create(argThat(app -> app.getCertificate().equalsIgnoreCase(customCert.getId())));
        verify(membershipService).addOrUpdate(eq(ORGANIZATION_ID), any());
    }

    private NewApplication prepareCreateServiceApp() {
        NewApplication newClient = Mockito.mock(NewApplication.class);
        when(newClient.getName()).thenReturn("my-client");
        when(newClient.getType()).thenReturn(ApplicationType.SERVICE);
        when(applicationRepository.create(any(Application.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(domainService.findById(anyString())).thenReturn(Maybe.just(new Domain()));
        when(scopeService.validateScope(anyString(), any())).thenReturn(Single.just(true));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(membershipService.addOrUpdate(eq(ORGANIZATION_ID), any())).thenReturn(Single.just(new Membership()));
        when(roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)).thenReturn(Maybe.just(new Role()));
        return newClient;
    }

    private NewApplication prepareCreateApp(boolean withRedirectUri) {
        NewApplication newClient = Mockito.mock(NewApplication.class);
        when(newClient.getName()).thenReturn("my-client");
        when(newClient.getType()).thenReturn(Stream.of(ApplicationType.values()).filter(type -> type != ApplicationType.SERVICE).toList().get(new Random().nextInt(0, ApplicationType.values().length - 1)));
        if (withRedirectUri) {
            when(newClient.getRedirectUris()).thenReturn(List.of("https://redirect"));
        } else {
            when(newClient.getRedirectUris()).thenReturn(List.of());
        }
        when(applicationRepository.create(any(Application.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(domainService.findById(anyString())).thenReturn(Maybe.just(new Domain()));
        when(scopeService.validateScope(anyString(), any())).thenReturn(Single.just(true));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doAnswer(invocation -> {
            Application mock = invocation.getArgument(0);
            mock.getSettings().getOauth().setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            return mock;
        }).when(applicationTemplateManager).apply(any());
        when(membershipService.addOrUpdate(eq(ORGANIZATION_ID), any())).thenReturn(Single.just(new Membership()));
        when(roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)).thenReturn(Maybe.just(new Role()));
        return newClient;
    }

    @Test
    public void shouldCreate_technicalException() {
        NewApplication newClient = Mockito.mock(NewApplication.class);
        when(newClient.getName()).thenReturn("my-client");
        doAnswer(invocation -> {
            Application mock = invocation.getArgument(0);
            mock.getSettings().getOauth().setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            mock.getSettings().getOauth().setClientId(CLIENT_ID);
            mock.getSettings().getOauth().setClientSecret("client_secret");
            return mock;
        }).when(applicationTemplateManager).apply(any());

        TestObserver<Application> testObserver = new TestObserver<>();
        applicationService.create(DOMAIN, newClient).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, never()).create(any(Application.class));
    }

    @Test
    public void shouldCreate2_technicalException() {
        NewApplication newClient = Mockito.mock(NewApplication.class);
        when(newClient.getName()).thenReturn("my-client");
        when(newClient.getRedirectUris()).thenReturn(null);
        when(newClient.getType()).thenReturn(ApplicationType.SERVICE);

        doAnswer(invocation -> {
            Application mock = invocation.getArgument(0);
            mock.getSettings().getOauth().setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            mock.getSettings().getOauth().setClientSecret(UUID.randomUUID().toString());
            return mock;
        }).when(applicationTemplateManager).apply(any());

        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        when(scopeService.validateScope(anyString(), any())).thenReturn(Single.just(true));
        when(certificateService.findByDomain(DOMAIN.getId())).thenReturn(Flowable.empty());
        when(applicationRepository.create(any(Application.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver<Application> testObserver = new TestObserver<>();
        applicationService.create(DOMAIN, newClient).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate_clientAlreadyExists() {
        NewApplication newClient = Mockito.mock(NewApplication.class);
        when(newClient.getName()).thenReturn("my-client");
        doAnswer(invocation -> {
            Application mock = invocation.getArgument(0);
            mock.getSettings().getOauth().setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            mock.getSettings().getOauth().setClientId(CLIENT_ID);
            mock.getSettings().getOauth().setClientSecret("client_secret");
            return mock;
        }).when(applicationTemplateManager).apply(any());
        when(oAuthClientUniquenessValidator.checkClientIdUniqueness(any(), any())).thenReturn(Completable.error(new ClientAlreadyExistsException("", "")));

        TestObserver<Application> testObserver = new TestObserver<>();
        applicationService.create(DOMAIN, newClient).subscribe(testObserver);

        testObserver.assertError(ClientAlreadyExistsException.class);
        testObserver.assertNotComplete();
        verify(applicationRepository, never()).create(any(Application.class));
    }

    @Test
    public void create_failWithNoDomain() {
        TestObserver testObserver = applicationService.create(DOMAIN, new Application()).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void create_implicit_invalidRedirectUri() {
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));

        Application toCreate = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("implicit"));
        oAuthSettings.setResponseTypes(Arrays.asList("token"));
        oAuthSettings.setClientType(ClientType.PUBLIC);
        settings.setOauth(oAuthSettings);
        toCreate.setSettings(settings);

        TestObserver testObserver = applicationService.create(DOMAIN, toCreate).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRedirectUriException.class);
    }

    @Test
    public void shouldNot_create_with_client_secret_jwt_when_bcrypt_used_to_hash_client_secret() {
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.just(true));

        Application toCreate = emptyAppWithDomain();
        toCreate.setType(ApplicationType.SERVICE);
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(List.of("implicit"));
        oAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT);
        settings.setOauth(oAuthSettings);
        toCreate.setSettings(settings);

        TestObserver testObserver = applicationService.create(DOMAIN,toCreate).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void create_generateUuidAsClientId() {
        NewApplication newClient = Mockito.mock(NewApplication.class);
        Application createClient = Mockito.mock(Application.class);
        when(createClient.getDomain()).thenReturn(DOMAIN.getId());
        when(newClient.getName()).thenReturn("my-client");
        when(newClient.getType()).thenReturn(ApplicationType.SERVICE);
        when(applicationRepository.create(any(Application.class))).thenReturn(Single.just(createClient));
        when(domainService.findById(anyString())).thenReturn(Maybe.just(new Domain()));
        when(scopeService.validateScope(anyString(), any())).thenReturn(Single.just(true));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        doAnswer(invocation -> {
            Application mock = invocation.getArgument(0);
            mock.getSettings().getOauth().setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            mock.getSettings().getOauth().setClientId(CLIENT_ID);
            mock.getSettings().getOauth().setClientSecret("client_secret");
            return mock;
        }).when(applicationTemplateManager).apply(any());
        when(certificateService.findByDomain(DOMAIN.getId())).thenReturn(Flowable.empty());

        TestObserver testObserver = applicationService.create(DOMAIN, newClient).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository, times(1)).create(captor.capture());
        assertNotNull("client_id must be generated", captor.getValue().getSettings().getOauth().getClientId());
        assertEquals("client_secret must be generated", 1, captor.getValue().getSecrets().size());
        assertNull("client_secret must not be in clear text into OAuth Settings", captor.getValue().getSettings().getOauth().getClientSecret());
    }

    @Test
    public void shouldPatch_keepingClientRedirectUris() {
        PatchApplication patchClient = new PatchApplication();
        patchClient.setIdentityProviders(getApplicationIdentityProviders());
        PatchApplicationSettings patchApplicationSettings = new PatchApplicationSettings();
        PatchApplicationOAuthSettings patchApplicationOAuthSettings = new PatchApplicationOAuthSettings();
        patchApplicationOAuthSettings.setResponseTypes(Optional.of(List.of("token")));
        patchApplicationOAuthSettings.setGrantTypes(Optional.of(List.of("implicit")));
        patchApplicationSettings.setOauth(Optional.of(patchApplicationOAuthSettings));
        patchApplicationSettings.setPasswordSettings(Optional.empty());
        patchClient.setSettings(Optional.of(patchApplicationSettings));

        Application toPatch = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setRedirectUris(List.of("https://callback"));
        settings.setOauth(oAuthSettings);
        toPatch.setSettings(settings);

        IdentityProvider idp1 = new IdentityProvider();
        idp1.setId("idp1");
        IdentityProvider idp2 = new IdentityProvider();
        idp2.setId("idp2");

        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(toPatch));
        when(identityProviderService.findById("id1")).thenReturn(Maybe.just(idp1));
        when(identityProviderService.findById("id2")).thenReturn(Maybe.just(idp2));

        Application updated = emptyAppWithDomain();
        when(applicationRepository.update(any(Application.class))).thenReturn(Single.just(updated));

        doReturn(true).when(accountSettingsValidator).validate(any());
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN.getId(), new ArrayList<>())).thenReturn(Single.just(true));

        TestObserver<Application> testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(identityProviderService, times(2)).findById(anyString());
        verify(applicationRepository, times(1)).update(any(Application.class));
    }

    @Test
    public void shouldInvalidatePatch_custom_claim_gis() {
        PatchApplication patchClient = new PatchApplication();
        patchClient.setIdentityProviders(getApplicationIdentityProviders());
        PatchApplicationSettings patchApplicationSettings = new PatchApplicationSettings();
        PatchApplicationOAuthSettings patchApplicationOAuthSettings = new PatchApplicationOAuthSettings();
        patchApplicationOAuthSettings.setResponseTypes(Optional.of(List.of("token")));
        patchApplicationOAuthSettings.setGrantTypes(Optional.of(List.of("implicit")));
        patchApplicationSettings.setOauth(Optional.of(patchApplicationOAuthSettings));
        patchApplicationSettings.setPasswordSettings(Optional.empty());
        patchClient.setSettings(Optional.of(patchApplicationSettings));

        Application toPatch = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setTokenCustomClaims(List.of(TokenClaim.of(TokenTypeHint.ACCESS_TOKEN,"gis", "value")));
        settings.setOauth(oAuthSettings);
        toPatch.setSettings(settings);

        IdentityProvider idp1 = new IdentityProvider();
        idp1.setId("idp1");

        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(toPatch));
        TestObserver<Application> testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(err -> err instanceof InvalidParameterException);
    }

    private Optional<Set<PatchApplicationIdentityProvider>> getApplicationIdentityProviders() {
        var patchAppIdp = new PatchApplicationIdentityProvider();
        patchAppIdp.setPriority(1);
        patchAppIdp.setIdentity("id1");
        var patchAppIdp2 = new PatchApplicationIdentityProvider();
        patchAppIdp2.setPriority(2);
        patchAppIdp2.setIdentity("id2");
        var patchAppIdps = Optional.of(Set.of(patchAppIdp, patchAppIdp2));
        return patchAppIdps;
    }

    @Test
    public void shouldUpdate_implicit_invalidRedirectUri() {
        Application client = new Application();
        ApplicationSettings clientSettings = new ApplicationSettings();
        ApplicationOAuthSettings clientOAuthSettings = new ApplicationOAuthSettings();
        clientOAuthSettings.setClientType(ClientType.PUBLIC);
        clientSettings.setOauth(clientOAuthSettings);
        client.setDomain(DOMAIN.getId());
        client.setSettings(clientSettings);

        PatchApplication patchClient = new PatchApplication();
        PatchApplicationSettings patchApplicationSettings = new PatchApplicationSettings();
        PatchApplicationOAuthSettings patchApplicationOAuthSettings = new PatchApplicationOAuthSettings();
        patchApplicationOAuthSettings.setGrantTypes(Optional.of(Arrays.asList("implicit")));
        patchApplicationOAuthSettings.setResponseTypes(Optional.of(Arrays.asList("token")));
        patchApplicationSettings.setOauth(Optional.of(patchApplicationOAuthSettings));
        patchApplicationSettings.setPasswordSettings(Optional.empty());
        patchClient.setSettings(Optional.of(patchApplicationSettings));

        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(client));
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRedirectUriException.class);

        verify(applicationRepository, times(1)).findById(anyString());
    }

    @Test
    public void shouldUpdate_technicalException() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void shouldUpdate2_technicalException() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void shouldUpdate_clientNotFound() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.empty());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();

        testObserver.assertError(ApplicationNotFoundException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void update_failWithNoDomain() {
        TestObserver testObserver = applicationService.update(new Application()).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void update_implicitGrant_invalidRedirectUri() {

        Application app = emptyAppWithDomain();
        when(applicationRepository.findById(any())).thenReturn(Maybe.just(app));
        when(domainService.findById(any())).thenReturn(Maybe.just(new Domain()));

        Application toPatch = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("implicit"));
        oAuthSettings.setResponseTypes(Arrays.asList("token"));
        oAuthSettings.setClientType(ClientType.PUBLIC);
        settings.setOauth(oAuthSettings);
        toPatch.setSettings(settings);

        TestObserver testObserver = applicationService.update(toPatch).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRedirectUriException.class);

        verify(applicationRepository, times(1)).findById(any());
    }

    @Test
    public void update_defaultGrant_ok() {
        when(applicationRepository.findById(any())).thenReturn(Maybe.just(new Application()));
        when(applicationRepository.update(any(Application.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(domainService.findById(any())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.just(true));

        Application toPatch = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setRedirectUris(Arrays.asList("https://callback"));
        oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        settings.setOauth(oAuthSettings);
        toPatch.setSettings(settings);

        TestObserver testObserver = applicationService.update(toPatch).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).findById(any());
        verify(applicationRepository, times(1)).update(any(Application.class));
    }

    @Test
    public void update_clientCredentials_ok() {
        when(applicationRepository.findById(any())).thenReturn(Maybe.just(new Application()));
        when(applicationRepository.update(any(Application.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(domainService.findById(any())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.just(true));

        Application toPatch = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("client_credentials"));
        oAuthSettings.setResponseTypes(Arrays.asList());
        oAuthSettings.setRedirectUris(List.of("https://redirect"));
        settings.setOauth(oAuthSettings);
        toPatch.setSettings(settings);

        TestObserver testObserver = applicationService.update(toPatch).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).findById(any());
        verify(applicationRepository, times(1)).update(any(Application.class));
    }

    @Test
    public void update_clientCredentials_keep_clientSecret() {
        // if application has been created before 4.2, the clientSecret field must be preserved until
        final String APP_ID = "appId";
        Application existingApp = new Application();
        existingApp.setType(ApplicationType.SERVICE);
        existingApp.setSettings(new ApplicationSettings());
        existingApp.getSettings().setOauth(new ApplicationOAuthSettings());
        existingApp.setDomain(DOMAIN.getId());
        String clientSecret = "something";
        existingApp.getSettings().getOauth().setClientSecret(clientSecret);
        when(applicationRepository.findById(eq(APP_ID))).thenReturn(Maybe.just(existingApp));
        when(applicationRepository.update(any(Application.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(domainService.findById(any())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.just(true));
        when(accountSettingsValidator.validate(any())).thenReturn(true);

        PatchApplication toPatch = new PatchApplication();
        PatchApplicationSettings settings = new PatchApplicationSettings();
        PatchApplicationOAuthSettings oAuthSettings = new PatchApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Optional.of(Arrays.asList("client_credentials")));
        oAuthSettings.setResponseTypes(Optional.of(Arrays.asList()));
        settings.setOauth(Optional.of(oAuthSettings));
        toPatch.setSettings(Optional.of(settings));

        TestObserver testObserver = applicationService.patch(DOMAIN, APP_ID, toPatch, principal, revokeToken).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).findById(any());
        verify(applicationRepository, times(1)).update(argThat(app -> app.getSettings() != null &&
                app.getSettings().getOauth() != null &&
                clientSecret.equals(app.getSettings().getOauth().getClientSecret())));
    }

    @Test
    public void update_tokenEndpointAuthMethod_to_client_secret_jwt_if_app_with_none_hashed_secret() {
        Application existingApp = emptyAppWithDomain();
        existingApp.setType(ApplicationType.SERVICE);
        ApplicationSettings existingAppSettings = new ApplicationSettings();
        ApplicationOAuthSettings existingOAuthSettings = new ApplicationOAuthSettings();
        existingOAuthSettings.setGrantTypes(Arrays.asList("client_credentials"));
        existingOAuthSettings.setResponseTypes(Arrays.asList());
        existingOAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        existingAppSettings.setOauth(existingOAuthSettings);
        existingApp.setSettings(existingAppSettings);

        when(applicationRepository.findById(any())).thenReturn(Maybe.just(existingApp));
        when(applicationRepository.update(any(Application.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(domainService.findById(any())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.just(true));

        Application toPatch = emptyAppWithDomain();
        toPatch.setType(ApplicationType.SERVICE);
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("client_credentials"));
        oAuthSettings.setResponseTypes(Arrays.asList());
        oAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT);
        settings.setOauth(oAuthSettings);
        toPatch.setSecretSettings(List.of(ApplicationSecretConfig.buildNoneSecretSettings()));// None
        toPatch.setSettings(settings);

        TestObserver testObserver = applicationService.update(toPatch).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).findById(any());
        verify(applicationRepository, times(1)).update(any(Application.class));
    }

    @Test
    public void shoudNot_update_tokenEndpointAuthMethod_to_client_secret_jwt_if_app_with_bcrypt_hashed_secret() {
        Application existingApp = emptyAppWithDomain();
        existingApp.setType(ApplicationType.SERVICE);
        ApplicationSettings existingAppSettings = new ApplicationSettings();
        ApplicationOAuthSettings existingOAuthSettings = new ApplicationOAuthSettings();
        existingOAuthSettings.setGrantTypes(Arrays.asList("client_credentials"));
        existingOAuthSettings.setResponseTypes(Arrays.asList());
        existingOAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        existingAppSettings.setOauth(existingOAuthSettings);
        existingApp.setSettings(existingAppSettings);

        when(applicationRepository.findById(any())).thenReturn(Maybe.just(existingApp));
        when(domainService.findById(any())).thenReturn(Maybe.just(new Domain()));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.just(true));

        Application toPatch = emptyAppWithDomain();
        toPatch.setType(ApplicationType.SERVICE);
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("client_credentials"));
        oAuthSettings.setResponseTypes(Arrays.asList());
        oAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT);
        settings.setOauth(oAuthSettings);
        toPatch.setSecretSettings(List.of(applicationSecretConfig.toSecretSettings()));// BCrypt
        toPatch.setSettings(settings);

        TestObserver testObserver = applicationService.update(toPatch).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);

        verify(applicationRepository, times(1)).findById(any());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void shouldPatch() {
        Application client = new Application();
        client.setId("my-client");
        client.setDomain(DOMAIN.getId());

        IdentityProvider idp1 = new IdentityProvider();
        idp1.setId("idp1");
        IdentityProvider idp2 = new IdentityProvider();
        idp2.setId("idp2");

        PatchApplication patchClient = new PatchApplication();
        patchClient.setIdentityProviders(getApplicationIdentityProviders());
        PatchApplicationSettings patchApplicationSettings = new PatchApplicationSettings();
        PatchApplicationOAuthSettings patchApplicationOAuthSettings = new PatchApplicationOAuthSettings();
        patchApplicationOAuthSettings.setGrantTypes(Optional.of(List.of("authorization_code")));
        patchApplicationOAuthSettings.setRedirectUris(Optional.of(List.of("https://callback")));
        patchApplicationSettings.setOauth(Optional.of(patchApplicationOAuthSettings));
        patchApplicationSettings.setPasswordSettings(Optional.empty());
        patchClient.setSettings(Optional.of(patchApplicationSettings));

        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(client));
        when(identityProviderService.findById("id1")).thenReturn(Maybe.just(idp1));
        when(identityProviderService.findById("id2")).thenReturn(Maybe.just(idp2));
        when(applicationRepository.update(any(Application.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN.getId(), new ArrayList<>())).thenReturn(Single.just(true));
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(identityProviderService, times(2)).findById(anyString());
        verify(applicationRepository, times(1)).update(any(Application.class));
    }

    @Test
    public void shouldPatch_Application_ResetPassword_ValidField() {
        Application client = new Application();
        client.setId("my-client");
        client.setDomain(DOMAIN.getId());

        IdentityProvider idp1 = new IdentityProvider();
        idp1.setId("idp1");
        IdentityProvider idp2 = new IdentityProvider();
        idp2.setId("idp2");

        PatchApplication patchClient = new PatchApplication();
        patchClient.setIdentityProviders(getApplicationIdentityProviders());
        PatchApplicationSettings patchApplicationSettings = new PatchApplicationSettings();
        PatchApplicationOAuthSettings patchApplicationOAuthSettings = new PatchApplicationOAuthSettings();
        patchApplicationOAuthSettings.setGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        patchApplicationOAuthSettings.setRedirectUris(Optional.of(Arrays.asList("https://callback")));
        patchApplicationSettings.setOauth(Optional.of(patchApplicationOAuthSettings));
        patchApplicationSettings.setPasswordSettings(Optional.empty());
        final AccountSettings accountSettings = new AccountSettings();
        final FormField formField = new FormField();
        formField.setKey("username");
        accountSettings.setResetPasswordCustomFormFields(Arrays.asList(formField));
        accountSettings.setResetPasswordCustomForm(true);
        patchApplicationSettings.setAccount(Optional.of(accountSettings));
        patchClient.setSettings(Optional.of(patchApplicationSettings));

        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(client));
        when(identityProviderService.findById("id1")).thenReturn(Maybe.just(idp1));
        when(identityProviderService.findById("id2")).thenReturn(Maybe.just(idp2));
        when(applicationRepository.update(any(Application.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN.getId(), new ArrayList<>())).thenReturn(Single.just(true));
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(identityProviderService, times(2)).findById(anyString());
        verify(applicationRepository, times(1)).update(any(Application.class));
    }

    @Test
    public void shouldNoPatch_Application_ResetPassword_InvalidField() {
        Application client = new Application();
        client.setId("my-client");
        client.setDomain(DOMAIN.getId());

        IdentityProvider idp1 = new IdentityProvider();
        idp1.setId("idp1");
        IdentityProvider idp2 = new IdentityProvider();
        idp2.setId("idp2");

        PatchApplication patchClient = new PatchApplication();
        patchClient.setIdentityProviders(getApplicationIdentityProviders());
        PatchApplicationSettings patchApplicationSettings = new PatchApplicationSettings();
        PatchApplicationOAuthSettings patchApplicationOAuthSettings = new PatchApplicationOAuthSettings();
        patchApplicationOAuthSettings.setGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        patchApplicationOAuthSettings.setRedirectUris(Optional.of(Arrays.asList("https://callback")));
        patchApplicationSettings.setOauth(Optional.of(patchApplicationOAuthSettings));
        patchApplicationSettings.setPasswordSettings(Optional.empty());
        final AccountSettings accountSettings = new AccountSettings();
        final FormField formField = new FormField();
        formField.setKey("unknown");
        accountSettings.setResetPasswordCustomFormFields(Arrays.asList(formField));
        accountSettings.setResetPasswordCustomForm(true);
        patchApplicationSettings.setAccount(Optional.of(accountSettings));
        patchClient.setSettings(Optional.of(patchApplicationSettings));

        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(client));
        doReturn(false).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidParameterException.class);

        verify(applicationRepository, times(1)).findById(anyString());
        verify(identityProviderService, never()).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void shouldPatch_mobileApplication() {
        Application client = emptyAppWithDomain();

        PatchApplication patchClient = new PatchApplication();
        PatchApplicationSettings patchApplicationSettings = new PatchApplicationSettings();
        PatchApplicationOAuthSettings patchApplicationOAuthSettings = new PatchApplicationOAuthSettings();
        patchApplicationOAuthSettings.setGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        patchApplicationOAuthSettings.setRedirectUris(Optional.of(Arrays.asList("com.gravitee.app://callback")));
        patchApplicationSettings.setOauth(Optional.of(patchApplicationOAuthSettings));
        patchApplicationSettings.setPasswordSettings(Optional.empty());
        patchClient.setSettings(Optional.of(patchApplicationSettings));

        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(client));
        when(applicationRepository.update(any(Application.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN.getId(), new ArrayList<>())).thenReturn(Single.just(true));
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, times(1)).update(any(Application.class));
    }

    @Test
    public void shouldPatch_mobileApplication_googleCase() {
        Application client = emptyAppWithDomain();

        PatchApplication patchClient = new PatchApplication();
        PatchApplicationSettings patchApplicationSettings = new PatchApplicationSettings();
        PatchApplicationOAuthSettings patchApplicationOAuthSettings = new PatchApplicationOAuthSettings();
        patchApplicationOAuthSettings.setGrantTypes(Optional.of(Arrays.asList("authorization_code")));
        patchApplicationOAuthSettings.setRedirectUris(Optional.of(Arrays.asList("com.google.app:/callback")));
        patchApplicationSettings.setOauth(Optional.of(patchApplicationOAuthSettings));
        patchApplicationSettings.setPasswordSettings(Optional.empty());
        patchClient.setSettings(Optional.of(patchApplicationSettings));

        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(client));
        when(applicationRepository.update(any(Application.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(scopeService.validateScope(DOMAIN.getId(), new ArrayList<>())).thenReturn(Single.just(true));
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, times(1)).update(any(Application.class));
    }

    @Test
    public void shouldDelete() {
        Application existingClient = Mockito.mock(Application.class);
        when(existingClient.getId()).thenReturn("my-client");
        when(existingClient.getDomain()).thenReturn("my-domain");
        when(applicationRepository.findById(existingClient.getId())).thenReturn(Maybe.just(existingClient));
        when(applicationRepository.delete(existingClient.getId())).thenReturn(Completable.complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        Form form = new Form();
        form.setId("form-id");
        when(formService.findByDomainAndClient(existingClient.getDomain(), existingClient.getId())).thenReturn(Flowable.just(form));
        when(formService.delete(eq("my-domain"), eq(form.getId()))).thenReturn(Completable.complete());
        Email email = new Email();
        email.setId("email-id");
        when(emailTemplateService.findByClient(ReferenceType.DOMAIN, existingClient.getDomain(), existingClient.getId())).thenReturn(Flowable.just(email));
        when(emailTemplateService.delete(email.getId())).thenReturn(Completable.complete());
        Membership membership = new Membership();
        membership.setId("membership-id");
        when(membershipService.findByReference(existingClient.getId(), ReferenceType.APPLICATION)).thenReturn(Flowable.just(membership));
        when(membershipService.delete(any(), anyString())).thenReturn(Completable.complete());

        TestObserver testObserver = applicationService.delete(existingClient.getId(), DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).delete(existingClient.getId());
        verify(formService, times(1)).delete(eq("my-domain"), anyString());
        verify(emailTemplateService, times(1)).delete(anyString());
        verify(membershipService, times(1)).delete(any(), anyString());
    }

    @Test
    public void shouldDelete_withoutRelatedData() {
        Application existingClient = Mockito.mock(Application.class);
        when(existingClient.getDomain()).thenReturn("my-domain");
        when(existingClient.getId()).thenReturn("my-client");
        when(applicationRepository.findById(existingClient.getId())).thenReturn(Maybe.just(existingClient));
        when(applicationRepository.delete(existingClient.getId())).thenReturn(Completable.complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(formService.findByDomainAndClient(existingClient.getDomain(), existingClient.getId())).thenReturn(Flowable.empty());
        when(emailTemplateService.findByClient(ReferenceType.DOMAIN, existingClient.getDomain(), existingClient.getId())).thenReturn(Flowable.empty());
        when(membershipService.findByReference(existingClient.getId(), ReferenceType.APPLICATION)).thenReturn(Flowable.empty());

        TestObserver testObserver = applicationService.delete(existingClient.getId(), DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).delete(existingClient.getId());
        verify(formService, never()).delete(anyString(), anyString());
        verify(emailTemplateService, never()).delete(anyString());
        verify(membershipService, never()).delete(any(), anyString());
    }

    @Test
    public void shouldDelete_technicalException() {
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(new Application()));
        when(applicationRepository.delete(anyString())).thenReturn(Completable.error(TechnicalException::new));

        TestObserver testObserver = applicationService.delete("my-client", DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete2_technicalException() {
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = applicationService.delete("my-client", DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_clientNotFound() {
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.empty());

        TestObserver testObserver = applicationService.delete("my-client", DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(ApplicationNotFoundException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, never()).delete("my-client");
    }

    @Test
    public void validateClientMetadata_invalidRedirectUriException_noSchemeUri() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);

        Application client = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setRedirectUris(Arrays.asList("noscheme"));
        oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
        settings.setOauth(oAuthSettings);
        client.setSettings(settings);

        when(patchClient.patch(any())).thenReturn(client);
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        doReturn(true).when(accountSettingsValidator).validate(any());
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void validateClientMetadata_invalidRedirectUriException_malformedUri() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);

        Application client = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setRedirectUris(Arrays.asList("malformed:uri:exception"));
        oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        settings.setOauth(oAuthSettings);
        client.setSettings(settings);

        when(patchClient.patch(any())).thenReturn(client);
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void validateClientMetadata_invalidRedirectUriException_forbidLocalhost() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);

        Application client = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setRedirectUris(Arrays.asList("http://localhost/callback"));
        oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        settings.setOauth(oAuthSettings);
        client.setSettings(settings);

        when(patchClient.patch(any())).thenReturn(client);
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void validateClientMetadata_invalidRedirectUriException_forbidHttp() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);

        Application client = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setRedirectUris(Arrays.asList("http://gravitee.io/callback"));
        oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        settings.setOauth(oAuthSettings);
        client.setSettings(settings);

        when(patchClient.patch(any())).thenReturn(client);
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void validateClientMetadata_invalidRedirectUriException_forbidWildcard() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);

        Application client = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setRedirectUris(Arrays.asList("https://gravitee.io/*"));
        oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        settings.setOauth(oAuthSettings);
        client.setSettings(settings);

        when(patchClient.patch(any())).thenReturn(client);
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void validateClientMetadata_invalidClientMetadataException_unknownScope() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);

        Application client = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setRedirectUris(Arrays.asList("https://callback"));
        oAuthSettings.setScopes(Collections.emptyList());
        settings.setOauth(oAuthSettings);
        client.setSettings(settings);

        when(patchClient.patch(any())).thenReturn(client);
        doReturn(true).when(accountSettingsValidator).validate(any());
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void validateClientMetadata_invalidClientMetadataException_invalidTokenEndpointAuthMethod() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);

        Application client = new Application();
        client.setType(ApplicationType.SERVICE);
        client.setDomain(DOMAIN.getId());
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.NONE);
        oAuthSettings.setScopes(Collections.emptyList());
        settings.setOauth(oAuthSettings);
        client.setSettings(settings);

        when(patchClient.patch(any())).thenReturn(client);
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void validateClientMetadata_validMetadata() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);

        Application client = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setRedirectUris(Arrays.asList("https://gravitee.io/callback"));
        oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        oAuthSettings.setScopes(Collections.emptyList());
        settings.setOauth(oAuthSettings);
        client.setSettings(settings);

        when(patchClient.patch(any())).thenReturn(client);
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(new Application()));
        when(applicationRepository.update(any(Application.class))).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(scopeService.validateScope(DOMAIN.getId(), Collections.emptyList())).thenReturn(Single.just(true));
        doReturn(true).when(accountSettingsValidator).validate(any());

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, times(1)).update(any(Application.class));
    }

    @Test
    public void validateClientMetadata_invalidTargetUrlException_forbidWildcard() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);
        Application client = createClientWithPostLogoutRedirectUris("https://gravitee.io/*");

        when(patchClient.patch(any())).thenReturn(client);
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));
        doReturn(true).when(accountSettingsValidator).validate(any());
        when(scopeService.validateScope(anyString(), any())).thenReturn(Single.just(true));

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidTargetUrlException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    private static Application emptyAppWithDomain() {
        Application app = new Application();
        app.setDomain(DOMAIN.getId());
        return app;
    }

    @Test
    public void validateClientMetadata_invalidTargetUrlException_noSchemeUri() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);
        Application client = createClientWithPostLogoutRedirectUris("noscheme");

        when(patchClient.patch(any())).thenReturn(client);
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        doReturn(true).when(accountSettingsValidator).validate(any());
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));
        when(scopeService.validateScope(anyString(), any())).thenReturn(Single.just(true));

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidTargetUrlException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void validateClientMetadata_invalidTargetUrlException_forbidHttp() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);
        Application client = createClientWithPostLogoutRedirectUris("http://gravitee.io/callback");

        when(patchClient.patch(any())).thenReturn(client);
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));
        doReturn(true).when(accountSettingsValidator).validate(any());
        when(scopeService.validateScope(anyString(), any())).thenReturn(Single.just(true));

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidTargetUrlException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void validateClientMetadata_invalidTargetUrlException_forbidLocalhost() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);
        Application client = createClientWithPostLogoutRedirectUris("http://localhost/callback");

        when(patchClient.patch(any())).thenReturn(client);
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));
        doReturn(true).when(accountSettingsValidator).validate(any());
        when(scopeService.validateScope(anyString(), any())).thenReturn(Single.just(true));

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidTargetUrlException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    @Test
    public void validateClientMetadata_invalidTargetUrlException_malformedUri() {
        PatchApplication patchClient = Mockito.mock(PatchApplication.class);
        Application client =createClientWithPostLogoutRedirectUris("malformed:uri:exception");

        when(patchClient.patch(any())).thenReturn(client);
        when(domainService.findById(DOMAIN.getId())).thenReturn(Maybe.just(new Domain()));
        Application app = emptyAppWithDomain();
        when(applicationRepository.findById("my-client")).thenReturn(Maybe.just(app));
        doReturn(true).when(accountSettingsValidator).validate(any());
        when(scopeService.validateScope(anyString(), any())).thenReturn(Single.just(true));

        TestObserver testObserver = applicationService.patch(DOMAIN, "my-client", patchClient, principal, revokeToken).test();
        testObserver.assertError(InvalidTargetUrlException.class);
        testObserver.assertNotComplete();

        verify(applicationRepository, times(1)).findById(anyString());
        verify(applicationRepository, never()).update(any(Application.class));
    }

    /**
     * AM-3065
     * Observed: NPE when enabling MFA on existing app with legacy (?) MFE config
     * Wanted: no NPE, new MFA config applied
     */
    @Test
    public void shouldAddMfaToAppWithLegacyMfaConfiguration() {
        Application client = Application.builder()
                .domain(DOMAIN.getId())
                .settings(ApplicationSettings.builder()
                        .mfa(MFASettings.builder()
                                .factor(new FactorSettings(null, null)) // client's existing config
                                .build())
                        .build())
                .build();

        when(applicationRepository.findById(any())).thenReturn(Maybe.just(client));
        when(applicationRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));


        var factorId = UUID.randomUUID().toString();
        PatchApplication patch = mfaConfigurationPatch(factorId);
        applicationService.patch(new Domain(client.getDomain()), client.getId(), patch, principal, revokeToken)
                .test()
                .assertValue(app -> app.getSettings().getMfa().getFactor().getDefaultFactorId().equals(factorId))
                .assertNoErrors();
    }

    @Test
    public void shouldDisableApplicationAndRemoveTokens() {
        var client = Application.builder().domain(DOMAIN.getId()).enabled(true).settings(ApplicationSettings.builder().build()).build();

        when(applicationRepository.findById(any())).thenReturn(Maybe.just(client));
        when(applicationRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(revokeToken.apply(any(), any())).thenAnswer(invocation -> Completable.complete());

        var patch =  PatchApplication.builder().enabled(Optional.of(false)).build();
        applicationService.patch(new Domain(client.getDomain()), client.getId(), patch, principal, revokeToken)
                .test()
                .assertNoErrors();

        verify(revokeToken).apply(any(), any());
    }

    @Test
    public void shouldDisableApplicationEvenIfTokenRemoveThrowError() {
        var client = Application.builder().domain(DOMAIN.getId()).enabled(true).settings(ApplicationSettings.builder().build()).build();

        when(applicationRepository.findById(any())).thenReturn(Maybe.just(client));
        when(applicationRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(revokeToken.apply(any(), any())).thenAnswer(invocation -> Completable.error(new RuntimeException()));

        var patch =  PatchApplication.builder().enabled(Optional.of(false)).build();
        applicationService.patch(new Domain(client.getDomain()), client.getId(), patch, principal, revokeToken)
                .test()
                .assertNoErrors();

        verify(revokeToken).apply(any(), any());
    }

    @Test
    public void shouldNotDisableApplicationAndRemoveTokensWhenItIsAlreadyDisabled() {
        var client = Application.builder().domain(DOMAIN.getId()).enabled(false).settings(ApplicationSettings.builder().build()).build();

        when(applicationRepository.findById(any())).thenReturn(Maybe.just(client));
        when(applicationRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        var patch =  PatchApplication.builder().enabled(Optional.of(false)).build();
        applicationService.patch(new Domain(client.getDomain()), client.getId(), patch, principal, revokeToken)
                .test()
                .assertNoErrors();

        verify(revokeToken, never()).apply(any(), any());
    }

    private PatchApplication mfaConfigurationPatch(String factorId) {
        return PatchApplication.builder()
                .factors(Optional.of(Set.of(factorId)))
                .settings(Optional.of(PatchApplicationSettings.builder()
                                .mfa(Optional.of(PatchMFASettings.builder()
                                                .factor(Optional.of(PatchFactorSettings.builder()
                                                                .defaultFactorId(Optional.of(factorId))
                                                                .applicationFactors(Optional.of(List.of(PatchApplicationFactorSettings.builder()
                                                                                .id(Optional.of(factorId))
                                                                        .build())))
                                                        .build()))
                                                .stepUpAuthenticationRule(Optional.of(""))
                                        .build()))
                        .build()))
                .build();
    }

    private Application createClientWithPostLogoutRedirectUris(String uri){
        Application client = emptyAppWithDomain();
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setPostLogoutRedirectUris(Arrays.asList(uri));
        oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        oAuthSettings.setRedirectUris(List.of("https://redirect"));
        settings.setOauth(oAuthSettings);
        client.setSettings(settings);

        return client;
    }
}

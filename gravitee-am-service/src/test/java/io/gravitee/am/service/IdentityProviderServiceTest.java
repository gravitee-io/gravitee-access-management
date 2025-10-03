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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderWithApplicationsException;
import io.gravitee.am.service.exception.InvalidPluginConfigurationException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.IdentityProviderServiceImpl;
import io.gravitee.am.service.model.AssignPasswordPolicy;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.gravitee.am.service.validators.idp.DatasourceValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
<<<<<<< HEAD
import org.mockito.ArgumentCaptor;
=======
>>>>>>> c6a36be30 (feat: Add datasource support for mongo clients (#6553))
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class IdentityProviderServiceTest {

    private final IdentityProviderRepository identityProviderRepository = mock();
    private final EventService eventService = mock();
    private final ApplicationService applicationService = mock();
    private final DatasourceValidator datasourceValidator = mock();

<<<<<<< HEAD
    private IdentityProviderRepository identityProviderRepository = mock();

    private EventService eventService = mock();

    private ApplicationService applicationService = mock();

    private PluginConfigurationValidationService validationService = mock();

    private IdentityProviderService identityProviderService = new IdentityProviderServiceImpl(
            identityProviderRepository, applicationService, eventService, mock(), new ObjectMapper(), validationService
=======
    private final IdentityProviderService identityProviderService = new IdentityProviderServiceImpl(
            identityProviderRepository, applicationService, eventService, mock(), new ObjectMapper(), datasourceValidator
>>>>>>> c6a36be30 (feat: Add datasource support for mongo clients (#6553))
    );

    private final static String DOMAIN = "domain1";
    private final Clock testClock = Clock.fixed(Instant.parse("2024-07-15T10:00:00Z"), ZoneOffset.UTC);
    private final Random random = new Random(1337);
    private final RandomStringGenerator idGen = new RandomStringGenerator.Builder().usingRandom(random::nextInt)
            .withinRange(new char[]{'0', '9'}, new char[]{'a', 'z'})
            .build();

    @Test
    public void shouldFindById() {
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.just(new IdentityProvider()));
        TestObserver<IdentityProvider> testObserver = identityProviderService.findById("my-identity-provider").test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingIdentityProvider() {
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.empty());
        TestObserver<IdentityProvider> testObserver = identityProviderService.findById("my-identity-provider").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        identityProviderService.findById("my-identity-provider").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(identityProviderRepository.findAll(eq(ReferenceType.DOMAIN), eq(DOMAIN))).thenReturn(Flowable.just(new IdentityProvider()));
        TestSubscriber<IdentityProvider> testObserver = identityProviderService.findByDomain(DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(identityProviderRepository.findAll(eq(ReferenceType.DOMAIN), eq(DOMAIN))).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = identityProviderService.findByDomain(DOMAIN).test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldFindAllByType() {

        IdentityProvider identityProvider = new IdentityProvider();
        when(identityProviderRepository.findAll(ReferenceType.ORGANIZATION)).thenReturn(Flowable.just(identityProvider));

        TestSubscriber<IdentityProvider> obs = identityProviderService.findAll(ReferenceType.ORGANIZATION).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(identityProvider);
    }

    @Test
    public void shouldFindAllByType_noIdentityProvider() {

        when(identityProviderRepository.findAll(ReferenceType.ORGANIZATION)).thenReturn(Flowable.empty());

        TestSubscriber<IdentityProvider> obs = identityProviderService.findAll(ReferenceType.ORGANIZATION).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertComplete();
        obs.assertNoValues();
    }

    @Test
    public void shouldFindAllByType_TechnicalException() {

        when(identityProviderRepository.findAll(ReferenceType.ORGANIZATION)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber<IdentityProvider> obs = identityProviderService.findAll(ReferenceType.ORGANIZATION).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(TechnicalException.class);
    }

    @Test
    public void shouldCreate() {
        NewIdentityProvider newIdentityProvider = mock(NewIdentityProvider.class);
        IdentityProvider idp = new IdentityProvider();
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId("domain#1");
        when(identityProviderRepository.create(any(IdentityProvider.class))).thenReturn(Single.just(idp));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(datasourceValidator.validate(any())).thenReturn(Completable.complete());

        TestObserver testObserver = identityProviderService.create(new Domain(DOMAIN), newIdentityProvider, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(identityProviderRepository, times(1)).create(any(IdentityProvider.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldNotCreate_WhenDatasourceIsInvalid() {
        NewIdentityProvider newIdentityProvider = mock(NewIdentityProvider.class);
        IdentityProvider idp = new IdentityProvider();
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId("domain#1");
        when(identityProviderRepository.create(any(IdentityProvider.class))).thenReturn(Single.just(idp));
        when(datasourceValidator.validate(any())).thenReturn(Completable.error(new Exception("a failure")));

        TestObserver testObserver = identityProviderService.create(DOMAIN, newIdentityProvider).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate_technicalException() {
        NewIdentityProvider newIdentityProvider = mock(NewIdentityProvider.class);
        when(identityProviderRepository.create(any(IdentityProvider.class))).thenReturn(Single.error(TechnicalException::new));
        when(datasourceValidator.validate(any())).thenReturn(Completable.complete());

        TestObserver<IdentityProvider> testObserver = new TestObserver<>();
        identityProviderService.create(new Domain(DOMAIN), newIdentityProvider, null).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldNotUpdate_configValidation_fails() {
        UpdateIdentityProvider updateIdentityProvider = mock(UpdateIdentityProvider.class);
        IdentityProvider idp = new IdentityProvider();
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId("domain#1");

        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.just(new IdentityProvider()));
        doThrow(InvalidPluginConfigurationException.fromValidationError("test error")).when(validationService).validate(any(), any());

        TestObserver testObserver = identityProviderService.update(DOMAIN, "my-identity-provider", updateIdentityProvider, false).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidPluginConfigurationException.class);

        verify(identityProviderRepository, never()).update(any(IdentityProvider.class));
        verify(eventService, never()).create(any());
        verify(validationService).validate(any(), any());
    }

    @Test
    public void shouldUpdate() {
        UpdateIdentityProvider updateIdentityProvider = mock(UpdateIdentityProvider.class);
        IdentityProvider idp = new IdentityProvider();
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId("domain#1");

        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.just(new IdentityProvider()));
        when(identityProviderRepository.update(any(IdentityProvider.class))).thenReturn(Single.just(idp));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(datasourceValidator.validate(any())).thenReturn(Completable.complete());

        TestObserver testObserver = identityProviderService.update(DOMAIN, "my-identity-provider", updateIdentityProvider, false).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(identityProviderRepository, times(1)).update(any(IdentityProvider.class));
        verify(eventService, times(1)).create(any());
        verify(validationService).validate(any(), any());
    }

    @Test
    public void shouldNotUpdate_WhenDatasourceIsInvalid() {
        UpdateIdentityProvider updateIdentityProvider = mock(UpdateIdentityProvider.class);
        IdentityProvider idp = new IdentityProvider();
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId("domain#1");

        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.just(new IdentityProvider()));
        when(identityProviderRepository.update(any(IdentityProvider.class))).thenReturn(Single.just(idp));
        when(datasourceValidator.validate(any())).thenReturn(Completable.error(new Exception("a failure")));

        TestObserver testObserver = identityProviderService.update(DOMAIN, "my-identity-provider", updateIdentityProvider, false).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate_isUpgrader_isSystem() {
        UpdateIdentityProvider updateIdentityProvider = (UpdateIdentityProvider) createIdentityProviders(true, false);
        IdentityProvider identityProvider = (IdentityProvider) createIdentityProviders(false, true);

        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.just(identityProvider));
        when(identityProviderRepository.update(any(IdentityProvider.class))).thenReturn(Single.just(identityProvider));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(datasourceValidator.validate(any())).thenReturn(Completable.complete());

        TestObserver testObserver = identityProviderService.update(DOMAIN, "my-identity-provider", updateIdentityProvider, true).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(identityProviderRepository, times(1))
                .update(argThat(upIdp -> upIdp.getConfiguration().equals("new-configuration")));
        verify(identityProviderRepository, times(1)).update(any(IdentityProvider.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldUpdate_notUpgrader_notSystem() {
        UpdateIdentityProvider updateIdentityProvider = (UpdateIdentityProvider) createIdentityProviders(true, false);
        IdentityProvider identityProvider = (IdentityProvider) createIdentityProviders(false, false);

        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.just(identityProvider));
        when(identityProviderRepository.update(any(IdentityProvider.class))).thenReturn(Single.just(identityProvider));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(datasourceValidator.validate(any())).thenReturn(Completable.complete());

        TestObserver testObserver = identityProviderService.update(DOMAIN, "my-identity-provider", updateIdentityProvider, false).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(identityProviderRepository, times(1))
                .update(argThat(upIdp -> upIdp.getConfiguration().equals("new-configuration")));
        verify(identityProviderRepository, times(1)).update(any(IdentityProvider.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldNotUpdate_notUpgrader_isSystem() {

        UpdateIdentityProvider updateIdentityProvider = (UpdateIdentityProvider) createIdentityProviders(true, false);
        IdentityProvider identityProvider = (IdentityProvider) createIdentityProviders(false, true);

        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.just(identityProvider));
        when(identityProviderRepository.update(any(IdentityProvider.class))).thenReturn(Single.just(identityProvider));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(datasourceValidator.validate(any())).thenReturn(Completable.complete());

        TestObserver testObserver = identityProviderService.update(DOMAIN, "my-identity-provider", updateIdentityProvider, false).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(identityProviderRepository, times(1))
                .update(argThat(upIdp -> upIdp.getConfiguration().equals("initial-config")));
        verify(identityProviderRepository, times(1)).update(any(IdentityProvider.class));
        verify(eventService, times(1)).create(any());
    }

    private Object createIdentityProviders(boolean isUpdate, boolean isSystem) {
        if (isUpdate) {
            UpdateIdentityProvider updateIdentityProvider = new UpdateIdentityProvider();
            updateIdentityProvider.setName("IDP to test");
            updateIdentityProvider.setConfiguration("new-configuration");

            return updateIdentityProvider;
        } else {
            IdentityProvider identityProvider = new IdentityProvider();
            identityProvider.setId("idp-to-upgrade");
            identityProvider.setName("IDP to test");
            identityProvider.setConfiguration("initial-config");
            identityProvider.setReferenceType(ReferenceType.DOMAIN);
            identityProvider.setReferenceId("domain#1");
            identityProvider.setSystem(isSystem);

            return identityProvider;
        }
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateIdentityProvider updateIdentityProvider = mock(UpdateIdentityProvider.class);
        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver<IdentityProvider> testObserver = new TestObserver<>();
        identityProviderService.update(DOMAIN, "my-identity-provider", updateIdentityProvider, false).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_notExistingIdentityProvider() {
        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.empty());

        TestObserver testObserver = identityProviderService.delete(DOMAIN, "my-identity-provider").test();

        testObserver.assertError(IdentityProviderNotFoundException.class);
        testObserver.assertNotComplete();

        verify(applicationService, never()).findByIdentityProvider(anyString());
        verify(identityProviderRepository, never()).delete(anyString());
    }

    @Test
    public void shouldDelete_identitiesWithClients() {
        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.just(new IdentityProvider()));
        when(applicationService.findByIdentityProvider("my-identity-provider")).thenReturn(Flowable.just(new Application()));

        TestObserver testObserver = identityProviderService.delete(DOMAIN, "my-identity-provider").test();

        testObserver.assertError(IdentityProviderWithApplicationsException.class);
        testObserver.assertNotComplete();

        verify(identityProviderRepository, never()).delete(anyString());
    }


    @Test
    public void shouldDelete_technicalException() {
        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.just(new IdentityProvider()));
        when(applicationService.findByIdentityProvider(any())).thenReturn(Flowable.error(new TechnicalException()));

        TestObserver testObserver = identityProviderService.delete(DOMAIN, "my-identity-provider").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        IdentityProvider existingIdentityProvider = new IdentityProvider();
        existingIdentityProvider.setReferenceType(ReferenceType.DOMAIN);
        existingIdentityProvider.setReferenceId(DOMAIN);
        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.just(existingIdentityProvider));
        when(identityProviderRepository.delete("my-identity-provider")).thenReturn(Completable.complete());
        when(applicationService.findByIdentityProvider("my-identity-provider")).thenReturn(Flowable.empty());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = identityProviderService.delete(DOMAIN, "my-identity-provider").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(identityProviderRepository, times(1)).delete("my-identity-provider");
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldFindByPasswordPolicy() {
        final String passwordPolicy = "password-policy";
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setPasswordPolicy(passwordPolicy);
        when(identityProviderRepository.findAllByPasswordPolicy(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq(passwordPolicy))).thenReturn(Flowable.just(identityProvider));
        TestSubscriber<IdentityProvider> testObserver = identityProviderService.findWithPasswordPolicy(ReferenceType.DOMAIN, DOMAIN, passwordPolicy).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindByPasswordPolicy_empty() {
        when(identityProviderRepository.findAllByPasswordPolicy(eq(ReferenceType.DOMAIN), eq(DOMAIN), any())).thenReturn(Flowable.empty());
        TestSubscriber<IdentityProvider> testObserver = identityProviderService.findWithPasswordPolicy(ReferenceType.DOMAIN, DOMAIN, "password").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(0);
    }

    @Test
    public void shouldFindByPasswordPolicy_technicalException() {
        when(identityProviderRepository.findAllByPasswordPolicy(eq(ReferenceType.DOMAIN), eq(DOMAIN), any())).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber<IdentityProvider> testSubscriber = identityProviderService.findWithPasswordPolicy(ReferenceType.DOMAIN, DOMAIN, "password").test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldUpdatePasswordPolicy() {
        AssignPasswordPolicy assignPasswordPolicy = mock(AssignPasswordPolicy.class);
        assignPasswordPolicy.setPasswordPolicy("newPP");
        IdentityProvider idp = new IdentityProvider();
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId("domain#1");
        idp.setPasswordPolicy("pp");

        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.just(new IdentityProvider()));
        when(identityProviderRepository.update(any(IdentityProvider.class))).thenReturn(Single.just(idp));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = identityProviderService.updatePasswordPolicy(DOMAIN, "my-identity-provider", assignPasswordPolicy).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(identityProviderRepository, times(1)).update(any(IdentityProvider.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldUpdatePasswordPolicy_technicalException() {
        AssignPasswordPolicy assignPasswordPolicy = mock(AssignPasswordPolicy.class);
        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("my-identity-provider"))).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver<IdentityProvider> testObserver = new TestObserver<>();
        identityProviderService.updatePasswordPolicy(DOMAIN, "my-identity-provider", assignPasswordPolicy).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldAssignDataPlaneId() throws Exception {
        IdentityProvider idp = new IdentityProvider();
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId("domain#1");

        ArgumentCaptor<IdentityProvider> argumentCaptor = ArgumentCaptor.forClass(IdentityProvider.class);
        when(identityProviderRepository.update(argumentCaptor.capture())).thenReturn(Single.just(idp));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        final var dataPlaneId = UUID.randomUUID().toString();
        final var observable = this.identityProviderService.assignDataPlane(idp, dataPlaneId).test();

        observable.await(5, TimeUnit.SECONDS);

        observable.assertNoErrors();
        Assert.assertEquals(dataPlaneId, argumentCaptor.getValue().getDataPlaneId());

        verify(identityProviderRepository).update(any());
        verify(eventService).create(any());
    }

    @Test
    public void shouldFindByCertificate() {
        var domainRef = Reference.domain("test-domain");
        var certId = "some-cert-id";
        when(identityProviderRepository.findAll(domainRef.type(), domainRef.id()))
                .thenReturn(Flowable.fromIterable(List.of(
                        mtlsOauth2Idp(domainRef, certId),
                        minimalIdentityProvider(domainRef),
                        idpWithDeeplyNestedCertConfig_array(domainRef, certId))));
        identityProviderService.findByCertificate(domainRef, certId)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete()
                .assertValueCount(2);
    }

    private IdentityProvider idpWithDeeplyNestedCertConfig_array(Reference domainRef, String certId) {
        var idp = minimalIdentityProvider(domainRef);
        idp.setType("custom-test-idp");
        idp.setConfiguration("""
                {
                  "very": {
                    "deeply": {
                      "nested": {
                        "certsArray": [
                          "another-cert",
                          "%s",
                          "yet-another-cert"
                        ]
                      }
                    }
                  }
                }""".formatted(certId));
        return idp;
    }

    private IdentityProvider idpWithDeeplyNestedCertConfig_field(Reference domainRef, String certId) {
        var idp = minimalIdentityProvider(domainRef);
        idp.setType("custom-test-idp");
        idp.setConfiguration("""
                {
                  "very": {
                    "deeply": {
                      "nested": {
                        "certificateId":"%s"
                      }
                    }
                  }
                }""".formatted(certId));
        return idp;
    }

    private IdentityProvider mtlsOauth2Idp(Reference domainRef, String certId) {
        var idp = minimalIdentityProvider(domainRef);
        idp.setType("oauth2-generic-am-idp");
        idp.setConfiguration("""
                {
                  "clientId": "asdasdasdasd",
                  "clientSecret": "1234",
                  "clientAuthenticationMethod": "tls_client_auth",
                  "clientAuthenticationCertificate": "%s",
                  "wellKnownUri": "https://localhost/.well-known/openid-configuration",
                  "responseType": "code",
                  "encodeRedirectUri": false,
                  "useIdTokenForUserInfo": false,
                  "signature": "RSA_RS256",
                  "publicKeyResolver": "GIVEN_KEY",
                  "scopes": [
                    "openid"
                  ],
                  "connectTimeout": 10000,
                  "idleTimeout": 10000,
                  "maxPoolSize": 200,
                  "storeOriginalTokens": false
                }
                """.formatted(certId));
        return idp;
    }

    private IdentityProvider minimalIdentityProvider(Reference reference) {
        var idp = new IdentityProvider();
        idp.setId(idGen.generate(10));
        idp.setReferenceType(reference.type());
        idp.setReferenceId(reference.id());
        idp.setCreatedAt(Date.from(testClock.instant()));
        idp.setUpdatedAt(Date.from(testClock.instant()));
        idp.setConfiguration("{}");
        return idp;
    }
}

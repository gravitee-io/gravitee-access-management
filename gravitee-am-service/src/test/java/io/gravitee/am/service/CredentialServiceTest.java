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

import io.gravitee.am.common.factor.FactorSecurityType;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.CredentialRepository;
import io.gravitee.am.service.exception.CredentialCurrentlyUsedException;
import io.gravitee.am.service.exception.CredentialNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.CredentialServiceImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CredentialServiceTest {

    @InjectMocks
    private CredentialService credentialService = new CredentialServiceImpl();

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private UserService userService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.just(new Credential()));
        TestObserver testObserver = credentialService.findById("my-credential").test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingCredential() {
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.empty());
        TestObserver testObserver = credentialService.findById("my-credential").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        credentialService.findById("my-credential").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByUserId() {
        when(credentialRepository.findByUserId(ReferenceType.DOMAIN, DOMAIN, "user-id")).thenReturn(Flowable.just(new Credential()));
        TestSubscriber<Credential> testSubscriber = credentialService.findByUserId(ReferenceType.DOMAIN, DOMAIN, "user-id").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindByUserId_technicalException() {
        when(credentialRepository.findByUserId(ReferenceType.DOMAIN, DOMAIN, "user-id")).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = credentialService.findByUserId(ReferenceType.DOMAIN, DOMAIN, "user-id").test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldFindByUsername() {
        when(credentialRepository.findByUsername(ReferenceType.DOMAIN, DOMAIN, "username")).thenReturn(Flowable.just(new Credential()));
        TestSubscriber<Credential> testObserver = credentialService.findByUsername(ReferenceType.DOMAIN, DOMAIN, "username").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindLatestElementsByUsername() {
        when(credentialRepository.findByUsername(ReferenceType.DOMAIN, DOMAIN, "username", 15)).thenReturn(Flowable.just(new Credential()));
        TestSubscriber<Credential> testObserver = credentialService.findByUsername(ReferenceType.DOMAIN, DOMAIN, "username", 15).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindByUsername_technicalException() {
        when(credentialRepository.findByUsername(ReferenceType.DOMAIN, DOMAIN, "username")).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = credentialService.findByUsername(ReferenceType.DOMAIN, DOMAIN, "username").test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldFindByCredentialId() {
        when(credentialRepository.findByCredentialId(ReferenceType.DOMAIN, DOMAIN, "credentialId")).thenReturn(Flowable.just(new Credential()));
        TestSubscriber<Credential> testSubscriber = credentialService.findByCredentialId(ReferenceType.DOMAIN, DOMAIN, "credentialId").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindByCredentialId_technicalException() {
        when(credentialRepository.findByCredentialId(ReferenceType.DOMAIN, DOMAIN, "credentialId")).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = credentialService.findByCredentialId(ReferenceType.DOMAIN, DOMAIN, "credentialId").test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        Credential newCredential = Mockito.mock(Credential.class);
        when(credentialRepository.create(any(Credential.class))).thenReturn(Single.just(new Credential()));

        TestObserver testObserver = credentialService.create(newCredential).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialRepository, times(1)).create(any(Credential.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        Credential newCredential = Mockito.mock(Credential.class);
        when(credentialRepository.create(any(Credential.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver<Credential> testObserver = new TestObserver<>();
        credentialService.create(newCredential).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate() {
        Credential updateCredential = Mockito.mock(Credential.class);
        when(updateCredential.getId()).thenReturn("my-credential");
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.just(new Credential()));
        when(credentialRepository.update(any(Credential.class))).thenReturn(Single.just(new Credential()));

        TestObserver testObserver = credentialService.update(updateCredential).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialRepository, times(1)).findById(anyString());
        verify(credentialRepository, times(1)).update(any(Credential.class));
    }

    @Test
    public void shouldUpdate_technicalException() {
        Credential updateCredential = Mockito.mock(Credential.class);
        when(updateCredential.getId()).thenReturn("my-credential");
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = credentialService.update(updateCredential).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(credentialRepository, times(1)).findById(anyString());
        verify(credentialRepository, never()).update(any(Credential.class));
    }

    @Test
    public void shouldUpdate2_technicalException() {
        Credential updateCredential = Mockito.mock(Credential.class);
        when(updateCredential.getId()).thenReturn("my-credential");
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.just(new Credential()));
        when(credentialRepository.update(any(Credential.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = credentialService.update(updateCredential).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(credentialRepository, times(1)).findById(anyString());
        verify(credentialRepository, times(1)).update(any(Credential.class));
    }

    @Test
    public void shouldDelete_technicalException() {
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = credentialService.delete("my-credential").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_unknownFactor() {
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.empty());

        TestObserver testObserver = credentialService.delete("my-credential").test();
        testObserver.assertError(CredentialNotFoundException.class);
        testObserver.assertNotComplete();

        verify(credentialRepository, never()).delete(anyString());
    }

    @Test
    public void shouldDelete() {
        User user = new User();
        Credential credential = new Credential();
        credential.setCredentialId("credential-id");
        credential.setUserId("anyId");
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity security = new EnrolledFactorSecurity();
        security.setType(FactorSecurityType.WEBAUTHN_CREDENTIAL);
        security.setValue("another-credential-id");
        enrolledFactor.setSecurity(security);
        user.setFactors(List.of(enrolledFactor));
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.just(credential));
        when(credentialRepository.delete("my-credential")).thenReturn(Completable.complete());
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        TestObserver testObserver = credentialService.delete("my-credential").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(credentialRepository, times(1)).delete("my-credential");
    }

    @Test
    public void deleteShouldThrowException() {
        User user = new User();
        Credential credential = new Credential();
        credential.setCredentialId("credential-id");
        credential.setUserId("anyId");
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity security = new EnrolledFactorSecurity();
        security.setType(FactorSecurityType.WEBAUTHN_CREDENTIAL);
        security.setValue("credential-id");
        enrolledFactor.setSecurity(security);

        user.setFactors(List.of(enrolledFactor));
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.just(credential));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        TestObserver testObserver = credentialService.delete("my-credential").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(CredentialCurrentlyUsedException.class);
        verify(credentialRepository, never()).delete("my-credential");
    }
    @Test
    public void shouldUpdate_byCredentialId_idPresent() {
        Credential updateCredential = new Credential();
        updateCredential.setId("my-credential-internal-id");
        updateCredential.setCredentialId("my-credential-id");
        Credential existingCredential = new Credential();
        existingCredential.setCredentialId(updateCredential.getCredentialId());

        when(credentialRepository.findByCredentialId(any(), any(), any())).thenReturn(Flowable.just(existingCredential));
        when(credentialRepository.update(any(Credential.class))).thenReturn(Single.just(new Credential()));

        TestObserver testObserver = credentialService.update(ReferenceType.DOMAIN, DOMAIN, updateCredential.getCredentialId(), updateCredential).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialRepository, times(1)).findByCredentialId(any(), any(), any());
        verify(credentialRepository, times(1)).update(any(Credential.class));
    }

    @Test
    public void shouldUpdate_byCredentialId_idPresent_filterUserId() {
        Credential updateCredential = new Credential();
        updateCredential.setId("my-credential-internal-id");
        updateCredential.setCredentialId("my-credential-id");
        updateCredential.setUserId("user01");

        Credential existingCredential = new Credential();
        existingCredential.setCredentialId(updateCredential.getCredentialId());
        existingCredential.setUserId("user01");

        Credential existingCredential2 = new Credential();
        existingCredential2.setCredentialId(updateCredential.getCredentialId());
        existingCredential2.setUserId("user02");

        when(credentialRepository.findByCredentialId(any(), any(), any())).thenReturn(Flowable.just(existingCredential2, existingCredential));
        when(credentialRepository.update(any(Credential.class))).thenReturn(Single.just(new Credential()));

        TestObserver testObserver = credentialService.update(ReferenceType.DOMAIN, DOMAIN, updateCredential.getCredentialId(), updateCredential).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialRepository, times(1)).findByCredentialId(any(), any(), any());
        verify(credentialRepository, times(1)).update(argThat(cred -> cred.getUserId().equals(existingCredential.getUserId())));
    }

    @Test
    public void shouldUpdate_byCredentialId_idMissing() {
        Credential updateCredential = new Credential();
        updateCredential.setId("my-credential-internal-id");
        updateCredential.setCredentialId("my-credential-id");
        Credential existingCredential = new Credential();
        existingCredential.setCredentialId(null);

        when(credentialRepository.findByCredentialId(any(), any(), any())).thenReturn(Flowable.just(existingCredential));
        when(credentialRepository.update(any(Credential.class))).thenReturn(Single.just(new Credential()));

        TestObserver testObserver = credentialService.update(ReferenceType.DOMAIN, DOMAIN, updateCredential.getCredentialId(), updateCredential).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialRepository, times(1)).findByCredentialId(any(), any(), any());
        verify(credentialRepository, times(1)).update(any(Credential.class));
    }

    @Test
    public void shouldDeleteByUserId() {
        String userId = "user-id";
        when(credentialRepository.deleteByUserId(ReferenceType.DOMAIN, DOMAIN, userId)).thenReturn(Completable.complete());

        TestObserver testObserver = credentialService.deleteByUserId(domain, userId).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(credentialRepository, times(1)).deleteByUserId(ReferenceType.DOMAIN, DOMAIN, userId);
    }

    @Test
    public void shouldDeleteByUserId_technicalException() {
        String userId = "user-id";
        when(credentialRepository.deleteByUserId(ReferenceType.DOMAIN, DOMAIN, userId)).thenReturn(Completable.error(TechnicalException::new));

        TestObserver testObserver = credentialService.deleteByUserId(domain, userId).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
        verify(credentialRepository, times(1)).deleteByUserId(ReferenceType.DOMAIN, DOMAIN, userId);
    }

}

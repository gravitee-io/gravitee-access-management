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

import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.CredentialRepository;
import io.gravitee.am.service.exception.CredentialNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.CredentialServiceImpl;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

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

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.just(new Credential()));
        TestObserver testObserver = credentialService.findById("my-credential").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingCredential() {
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.empty());
        TestObserver testObserver = credentialService.findById("my-credential").test();
        testObserver.awaitTerminalEvent();

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
        when(credentialRepository.findByUserId(ReferenceType.DOMAIN, DOMAIN, "user-id")).thenReturn(Single.just(Collections.singletonList(new Credential())));
        TestObserver<List<Credential>> testObserver = credentialService.findByUserId(ReferenceType.DOMAIN, DOMAIN, "user-id").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(credentials -> credentials.size() == 1);
    }

    @Test
    public void shouldFindByUserId_technicalException() {
        when(credentialRepository.findByUserId(ReferenceType.DOMAIN, DOMAIN, "user-id")).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        credentialService.findByUserId(ReferenceType.DOMAIN, DOMAIN, "user-id").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByUsername() {
        when(credentialRepository.findByUsername(ReferenceType.DOMAIN, DOMAIN, "username")).thenReturn(Single.just(Collections.singletonList(new Credential())));
        TestObserver<List<Credential>> testObserver = credentialService.findByUsername(ReferenceType.DOMAIN, DOMAIN, "username").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(credentials -> credentials.size() == 1);
    }

    @Test
    public void shouldFindByUsername_technicalException() {
        when(credentialRepository.findByUsername(ReferenceType.DOMAIN, DOMAIN, "username")).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        credentialService.findByUsername(ReferenceType.DOMAIN, DOMAIN, "username").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByCredentialId() {
        when(credentialRepository.findByCredentialId(ReferenceType.DOMAIN, DOMAIN, "credentialId")).thenReturn(Single.just(Collections.singletonList(new Credential())));
        TestObserver<List<Credential>> testObserver = credentialService.findByCredentialId(ReferenceType.DOMAIN, DOMAIN, "credentialId").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(credentials -> credentials.size() == 1);
    }

    @Test
    public void shouldFindByCredentialId_technicalException() {
        when(credentialRepository.findByCredentialId(ReferenceType.DOMAIN, DOMAIN, "credentialId")).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        credentialService.findByCredentialId(ReferenceType.DOMAIN, DOMAIN, "credentialId").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        Credential newCredential = Mockito.mock(Credential.class);
        when(credentialRepository.create(any(Credential.class))).thenReturn(Single.just(new Credential()));

        TestObserver testObserver = credentialService.create(newCredential).test();
        testObserver.awaitTerminalEvent();

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
        testObserver.awaitTerminalEvent();

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
        when(credentialRepository.findById("my-credential")).thenReturn(Maybe.just(new Credential()));
        when(credentialRepository.delete("my-credential")).thenReturn(Completable.complete());
        TestObserver testObserver = credentialService.delete("my-credential").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialRepository, times(1)).delete("my-credential");
    }
}

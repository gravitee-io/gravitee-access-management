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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.Template;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.EmailRepository;
import io.gravitee.am.service.exception.EmailAlreadyExistsException;
import io.gravitee.am.service.exception.EmailNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.EmailTemplateServiceImpl;
import io.gravitee.am.service.model.NewEmail;
import io.gravitee.am.service.model.UpdateEmail;
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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class EmailTemplateServiceTest {

    @InjectMocks
    private EmailTemplateService emailTemplateService = new EmailTemplateServiceImpl();

    @Mock
    private DomainService domainService;

    @Mock
    private EmailRepository emailRepository;

    @Mock
    private AuditService auditService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindAll() {
        when(emailRepository.findAll()).thenReturn(Single.just(Collections.singletonList(new Email())));
        TestObserver testObserver = emailTemplateService.findAll().test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindByDomainAndTemplate() {
        when(emailRepository.findByDomainAndTemplate(DOMAIN, Template.LOGIN.template())).thenReturn(Maybe.just(new Email()));
        TestObserver testObserver = emailTemplateService.findByDomainAndTemplate(DOMAIN, Template.LOGIN.template()).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindByDomainAndTemplate_notExistingEmail() {
        when(emailRepository.findByDomainAndTemplate(DOMAIN, Template.LOGIN.template())).thenReturn(Maybe.empty());
        TestObserver testObserver = emailTemplateService.findByDomainAndTemplate(DOMAIN, Template.LOGIN.template()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindByDomainAndTemplate_technicalException() {
        when(emailRepository.findByDomainAndTemplate(DOMAIN, Template.LOGIN.template())).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        emailTemplateService.findByDomainAndTemplate(DOMAIN, Template.LOGIN.template()).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewEmail newEmail = Mockito.mock(NewEmail.class);
        when(newEmail.getTemplate()).thenReturn(Template.REGISTRATION);
        when(emailRepository.findByDomainAndTemplate(anyString(), anyString())).thenReturn(Maybe.empty());
        when(emailTemplateService.findByDomainAndTemplate(anyString(), anyString())).thenReturn(Maybe.empty());
        when(emailRepository.create(any(Email.class))).thenReturn(Single.just(new Email()));
        when(domainService.reload(anyString(), any())).thenReturn(Single.just(new Domain()));

        TestObserver testObserver = emailTemplateService.create(DOMAIN, newEmail).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(emailRepository, times(1)).findByDomainAndTemplate(anyString(), anyString());
        verify(emailRepository, times(1)).create(any(Email.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewEmail newEmail = Mockito.mock(NewEmail.class);
        when(newEmail.getTemplate()).thenReturn(Template.REGISTRATION);
        when(emailRepository.findByDomainAndTemplate(anyString(), anyString())).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        emailTemplateService.create(DOMAIN, newEmail).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(emailRepository, never()).create(any(Email.class));
    }

    @Test
    public void shouldCreate_uniquenessException() {
        NewEmail newEmail = Mockito.mock(NewEmail.class);
        when(newEmail.getTemplate()).thenReturn(Template.REGISTRATION);
        when(emailRepository.findByDomainAndTemplate(anyString(), any())).thenReturn(Maybe.just(new Email()));

        TestObserver testObserver = new TestObserver();
        emailTemplateService.create(DOMAIN, newEmail).subscribe(testObserver);

        testObserver.assertError(EmailAlreadyExistsException.class);
        testObserver.assertNotComplete();

        verify(emailRepository, never()).create(any(Email.class));
    }

    @Test
    public void shouldUpdate() {
        UpdateEmail updateEmail = Mockito.mock(UpdateEmail.class);
        when(emailRepository.findById("my-email")).thenReturn(Maybe.just(new Email()));
        when(emailRepository.update(any(Email.class))).thenReturn(Single.just(new Email()));
        when(domainService.reload(anyString(), any())).thenReturn(Single.just(new Domain()));

        TestObserver testObserver = emailTemplateService.update(DOMAIN,"my-email", updateEmail).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(emailRepository, times(1)).findById("my-email");
        verify(emailRepository, times(1)).update(any(Email.class));
        verify(domainService, times(1)).reload(anyString(), any());
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateEmail updateEmail = Mockito.mock(UpdateEmail.class);
        when(emailRepository.findById("my-email")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        emailTemplateService.update(DOMAIN,"my-email", updateEmail).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(emailRepository, never()).findByDomainAndTemplate(anyString(), any());
        verify(emailRepository, never()).update(any(Email.class));
    }

    @Test
    public void shouldUpdate_emailNotFound() {
        when(emailRepository.findById("my-email")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        emailTemplateService.update(DOMAIN, "my-email", new UpdateEmail()).subscribe(testObserver);

        testObserver.assertError(EmailNotFoundException.class);
        testObserver.assertNotComplete();

        verify(emailRepository, never()).update(any(Email.class));
    }

    @Test
    public void shouldDelete_notExistingEmail() {
        when(emailRepository.findById("my-email")).thenReturn(Maybe.empty());

        TestObserver testObserver = emailTemplateService.delete("my-email").test();

        testObserver.assertError(EmailNotFoundException.class);
        testObserver.assertNotComplete();

        verify(emailRepository, never()).delete(anyString());
    }

    @Test
    public void shouldDelete_technicalException() {
        when(emailRepository.findById("my-email")).thenReturn(Maybe.just(new Email()));
        when(emailRepository.delete(anyString())).thenReturn(Completable.error(TechnicalException::new));

        TestObserver testObserver = emailTemplateService.delete("my-email").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        when(emailRepository.findById("my-email")).thenReturn(Maybe.just(new Email()));
        when(emailRepository.delete("my-email")).thenReturn(Completable.complete());
        when(domainService.reload(anyString(), any())).thenReturn(Single.just(new Domain()));

        TestObserver testObserver = emailTemplateService.delete( "my-email").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(emailRepository, times(1)).delete("my-email");
        verify(domainService, times(1)).reload(anyString(), any());
    }
}

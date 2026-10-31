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

import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.FormRepository;
import io.gravitee.am.service.exception.FormAlreadyExistsException;
import io.gravitee.am.service.exception.FormNotFoundException;
import io.gravitee.am.service.model.UpdateForm;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.FormServiceImpl;
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
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class FormServiceTest {

    @InjectMocks
    private FormService formService = new FormServiceImpl();

    @Mock
    private FormRepository formRepository;

    @Mock
    private EventService eventService;

    @Mock
    private AuditService auditService;

    private static final String DOMAIN = "domain";

    @Test
    public void copyFromClient() {

        final String sourceUid = "sourceUid";
        final String targetUid = "targetUid";

        Form formOne = new Form();
        formOne.setId("templateId");
        formOne.setEnabled(true);
        formOne.setReferenceType(ReferenceType.DOMAIN);
        formOne.setReferenceId(DOMAIN);
        formOne.setClient(sourceUid);
        formOne.setTemplate("login");
        formOne.setContent("formContent");
        formOne.setAssets("formAsset");

        Form formTwo = new Form(formOne);
        formTwo.setTemplate("error");

        when(formRepository.findByClientAndTemplate(ReferenceType.DOMAIN, DOMAIN, targetUid, "login")).thenReturn(Maybe.empty());
        when(formRepository.findByClientAndTemplate(ReferenceType.DOMAIN, DOMAIN, targetUid, "error")).thenReturn(Maybe.empty());
        when(formRepository.create(any())).thenAnswer(i -> Single.just(i.getArgument(0)));
        when(formRepository.findByClient(ReferenceType.DOMAIN, DOMAIN, sourceUid)).thenReturn(Flowable.just(formOne, formTwo));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<List<Form>> testObserver = formService.copyFromClient(DOMAIN, sourceUid, targetUid).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(forms -> forms.size() == 2 && forms.stream().filter(
                form -> form.getReferenceId().equals(DOMAIN) && form.getClient().equals(targetUid) && !form.getId().equals("templateId") && Arrays.asList("login", "error").contains(form.getTemplate()) && form.getContent().equals("formContent") && form.getAssets().equals("formAsset")
                ).count() == 2
        );
    }

    @Test
    public void copyFromClient_duplicateFound() {

        final String sourceUid = "sourceUid";
        final String targetUid = "targetUid";

        Form formOne = new Form();
        formOne.setId("templateId");
        formOne.setEnabled(true);
        formOne.setReferenceType(ReferenceType.DOMAIN);
        formOne.setReferenceId(DOMAIN);
        formOne.setClient(sourceUid);
        formOne.setTemplate("login");
        formOne.setContent("formContent");
        formOne.setAssets("formAsset");

        when(formRepository.findByClientAndTemplate(ReferenceType.DOMAIN, DOMAIN, targetUid, "login")).thenReturn(Maybe.just(new Form()));
        when(formRepository.findByClient(ReferenceType.DOMAIN, DOMAIN, sourceUid)).thenReturn(Flowable.just(formOne));

        TestObserver<List<Form>> testObserver = formService.copyFromClient(DOMAIN, sourceUid, targetUid).test();
        testObserver.assertNotComplete();
        testObserver.assertError(FormAlreadyExistsException.class);
    }

    @Test
    public void shouldFindAll() {

        Form form = new Form();
        when(formRepository.findAll(ReferenceType.ORGANIZATION)).thenReturn(Flowable.just(form));

        TestSubscriber<Form> obs = formService.findAll(ReferenceType.ORGANIZATION).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(form);
    }

    @Test
    public void shouldFindAll_noForm() {

        when(formRepository.findAll(ReferenceType.ORGANIZATION)).thenReturn(Flowable.empty());

        TestSubscriber<Form> obs = formService.findAll(ReferenceType.ORGANIZATION).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertComplete();
        obs.assertNoValues();
    }

    @Test
    public void shouldFindAll_TechnicalException() {

        when(formRepository.findAll(ReferenceType.ORGANIZATION)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber<Form> obs = formService.findAll(ReferenceType.ORGANIZATION).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(TechnicalException.class);
    }

    @Test
    public void shouldGetDefaultByDomainAndTemplate_Form() {
        TestObserver<Form> obs = formService.getDefaultByDomainAndTemplate("test", "valid").test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(form -> form.getContent().contains("test content"));
    }

    @Test
    public void shouldGetDefaultByDomainAndTemplate_TechnicalManagementException() {
        TestObserver<Form> obs = formService.getDefaultByDomainAndTemplate("test", "invalid").test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(TechnicalManagementException.class);
    }

    private Form formOf(String client) {
        Form form = new Form();
        form.setId("my-form");
        form.setReferenceType(ReferenceType.DOMAIN);
        form.setReferenceId(DOMAIN);
        form.setClient(client);
        return form;
    }

    @Test
    public void shouldUpdate_applicationForm() {
        when(formRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-form")).thenReturn(Maybe.just(formOf("app-x")));
        when(formRepository.update(any(Form.class))).thenAnswer(f -> Single.just(f.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        formService.update(DOMAIN, "app-x", "my-form", new UpdateForm()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(formRepository, times(1)).update(any(Form.class));
    }

    @Test
    public void shouldNotUpdate_domainLevelFormThroughApplication() {
        when(formRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-form")).thenReturn(Maybe.just(formOf(null)));

        formService.update(DOMAIN, "app-x", "my-form", new UpdateForm()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(FormNotFoundException.class);

        verify(formRepository, never()).update(any(Form.class));
    }

    @Test
    public void shouldNotUpdate_formOfAnotherApplication() {
        when(formRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-form")).thenReturn(Maybe.just(formOf("app-y")));

        formService.update(DOMAIN, "app-x", "my-form", new UpdateForm()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(FormNotFoundException.class);

        verify(formRepository, never()).update(any(Form.class));
    }

    @Test
    public void shouldDelete_applicationForm() {
        when(formRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-form")).thenReturn(Maybe.just(formOf("app-x")));
        when(formRepository.delete("my-form")).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        formService.delete(DOMAIN, "app-x", "my-form", null).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(formRepository, times(1)).delete("my-form");
    }

    @Test
    public void shouldNotDelete_domainLevelFormThroughApplication() {
        when(formRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-form")).thenReturn(Maybe.just(formOf(null)));

        formService.delete(DOMAIN, "app-x", "my-form", null).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(FormNotFoundException.class);

        verify(formRepository, never()).delete(anyString());
    }

    @Test
    public void shouldNotDelete_formOfAnotherApplication() {
        when(formRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-form")).thenReturn(Maybe.just(formOf("app-y")));

        formService.delete(DOMAIN, "app-x", "my-form", null).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(FormNotFoundException.class);

        verify(formRepository, never()).delete(anyString());
    }
}

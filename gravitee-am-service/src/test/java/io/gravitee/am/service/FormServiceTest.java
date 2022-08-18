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
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.FormServiceImpl;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.any;
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

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(form);
    }

    @Test
    public void shouldFindAll_noForm() {

        when(formRepository.findAll(ReferenceType.ORGANIZATION)).thenReturn(Flowable.empty());

        TestSubscriber<Form> obs = formService.findAll(ReferenceType.ORGANIZATION).test();

        obs.awaitTerminalEvent();
        obs.assertNoErrors();
        obs.assertComplete();
        obs.assertNoValues();
    }

    @Test
    public void shouldFindAll_TechnicalException() {

        when(formRepository.findAll(ReferenceType.ORGANIZATION)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber<Form> obs = formService.findAll(ReferenceType.ORGANIZATION).test();

        obs.awaitTerminalEvent();
        obs.assertError(TechnicalException.class);
    }

    @Test
    public void shouldGetDefaultByDomainAndTemplate_Form() {
        TestObserver<Form> obs = formService.getDefaultByDomainAndTemplate("test", "valid").test();
        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(form -> form.getContent().contains("test content"));
    }

    @Test
    public void shouldGetDefaultByDomainAndTemplate_TechnicalManagementException() {
        TestObserver<Form> obs = formService.getDefaultByDomainAndTemplate("test", "invalid").test();
        obs.awaitTerminalEvent();
        obs.assertError(TechnicalManagementException.class);
    }
}

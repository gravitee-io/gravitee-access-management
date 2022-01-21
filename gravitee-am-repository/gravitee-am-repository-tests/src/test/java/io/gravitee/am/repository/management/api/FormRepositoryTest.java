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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FormRepositoryTest extends AbstractManagementTest {

    public static final String FIXED_REF_ID = "fixedRefId";
    public static final String FIXED_CLI_ID = "fixedClientId";

    @Autowired
    protected FormRepository repository;

    protected Form buildForm() {
        Form form = new Form();
        String randomString = UUID.randomUUID().toString();
        form.setClient("client"+randomString);
        form.setContent("content"+randomString);
        form.setReferenceId("ref"+randomString);
        form.setReferenceType(ReferenceType.DOMAIN);
        form.setTemplate("tpl"+randomString);
        form.setAssets("assets"+randomString);
        form.setEnabled(true);
        form.setCreatedAt(new Date());
        form.setUpdatedAt(new Date());
        return form;
    }

    @Test
    public void shouldNotFindById() {
        final TestObserver<Form> testObserver = repository.findById("unknownId").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById() {
        Form form = buildForm();
        Form createdForm = repository.create(form).blockingGet();

        TestObserver<Form> testObserver = repository.findById(createdForm.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(form, createdForm.getId(), testObserver);
    }

    @Test
    public void shouldFindById_withRef() {
        Form form = buildForm();
        Form createdForm = repository.create(form).blockingGet();

        TestObserver<Form> testObserver = repository.findById(createdForm.getReferenceType(), createdForm.getReferenceId(), createdForm.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(form, createdForm.getId(), testObserver);
    }

    @Test
    public void shouldUpdateEmail() {
        Form form = buildForm();
        Form createdForm = repository.create(form).blockingGet();

        TestObserver<Form> testObserver = repository.findById(createdForm.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(form, createdForm.getId(), testObserver);

        Form updatableForm = buildForm();
        updatableForm.setId(createdForm.getId());
        TestObserver<Form> updatedForm = repository.update(updatableForm).test();
        updatedForm.awaitTerminalEvent();
        assertEqualsTo(updatableForm, createdForm.getId(), updatedForm);
    }

    private void assertEqualsTo(Form expectedForm, String expectedId, TestObserver<Form> testObserver) {
        testObserver.assertValue(e -> e.getId().equals(expectedId));
        testObserver.assertValue(e -> (e.getClient() == null && expectedForm.getClient() == null ) || e.getClient().equals(expectedForm.getClient()));
        testObserver.assertValue(e -> e.getContent().equals(expectedForm.getContent()));
        testObserver.assertValue(e -> e.getReferenceId().equals(expectedForm.getReferenceId()));
        testObserver.assertValue(e -> e.getReferenceType().equals(expectedForm.getReferenceType()));
        testObserver.assertValue(e -> e.getAssets().equals(expectedForm.getAssets()));
        testObserver.assertValue(e -> e.getTemplate().equals(expectedForm.getTemplate()));
        testObserver.assertValue(e -> e.isEnabled() == expectedForm.isEnabled());
    }

    @Test
    public void shouldDeleteById() {
        Form form = buildForm();
        Form createdForm = repository.create(form).blockingGet();

        TestObserver<Form> testObserver = repository.findById(createdForm.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(form, createdForm.getId(), testObserver);

        repository.delete(createdForm.getId()).blockingGet();

        testObserver = repository.findById(createdForm.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindAllByReference() {
        final int loop = 10;
        for (int i = 0; i < loop; i++) {
            final Form form = buildForm();
            form.setReferenceId(FIXED_REF_ID);
            repository.create(form).blockingGet();
        }

        for (int i = 0; i < loop; i++) {
            // random ref id
            repository.create(buildForm()).blockingGet();
        }

        TestObserver<List<Form>> testObserver = repository.findAll(ReferenceType.DOMAIN, FIXED_REF_ID).toList().test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(l -> l.size() == loop);
        testObserver.assertValue(l -> l.stream().map(Form::getId).distinct().count() == loop);
    }

    @Test
    public void shouldDeleteByReference() {
        final int loop = 10;
        for (int i = 0; i < loop; i++) {
            final Form form = buildForm();
            form.setReferenceId(FIXED_REF_ID);
            repository.create(form).blockingGet();
        }

        for (int i = 0; i < loop; i++) {
            // random ref id
            repository.create(buildForm()).blockingGet();
        }

        TestObserver<List<Form>> testObserver = repository.findAll(ReferenceType.DOMAIN, FIXED_REF_ID).toList().test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(l -> l.size() == loop);
        testObserver.assertValue(l -> l.stream().map(Form::getId).distinct().count() == loop);

        final TestObserver<Void> test = repository.deleteByReference(ReferenceType.DOMAIN, FIXED_REF_ID).test();
        test.awaitTerminalEvent();
        test.assertNoErrors();

        testObserver = repository.findAll(ReferenceType.DOMAIN, FIXED_REF_ID).toList().test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(l -> l.size() == 0);
    }


    @Test
    public void shouldFindByClient() {
        final int loop = 10;
        for (int i = 0; i < loop; i++) {
            final Form form = buildForm();
            form.setReferenceId(FIXED_REF_ID);
            form.setClient(FIXED_CLI_ID);
            repository.create(form).blockingGet();
        }

        for (int i = 0; i < loop; i++) {
            final Form form = buildForm();
            form.setReferenceId(FIXED_REF_ID);
            repository.create(form).blockingGet();
        }

        TestObserver<List<Form>> testObserver = repository.findByClient(ReferenceType.DOMAIN, FIXED_REF_ID, FIXED_CLI_ID).toList().test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(l -> l.size() == loop);
        testObserver.assertValue(l -> l.stream().map(Form::getId).distinct().count() == loop);
    }

    @Test
    public void shouldFindByTemplate() {
        final int loop = 10;
        for (int i = 0; i < loop; i++) {
            final Form form = buildForm();
            form.setReferenceId(FIXED_REF_ID);
            repository.create(form).blockingGet();
        }

        final Form form = buildForm();
        form.setReferenceId(FIXED_REF_ID);
        form.setTemplate("MyTemplateId");
        form.setClient(null);
        Form templateForm = repository.create(form).blockingGet();

        TestObserver<Form> testMaybe = repository.findByTemplate(ReferenceType.DOMAIN, FIXED_REF_ID, "MyTemplateId").test();
        testMaybe.awaitTerminalEvent();
        testMaybe.assertNoErrors();
        assertEqualsTo(templateForm, templateForm.getId(), testMaybe);
    }

    @Test
    public void shouldFindByClientAndTemplate() {
        final int loop = 10;
        for (int i = 0; i < loop; i++) {
            final Form form = buildForm();
            form.setReferenceId(FIXED_REF_ID);
            form.setClient(FIXED_CLI_ID);
            repository.create(form).blockingGet();
        }

        final Form form = buildForm();
        form.setReferenceId(FIXED_REF_ID);
        form.setClient(FIXED_CLI_ID);
        form.setTemplate("MyTemplateId");
        Form templateForm = repository.create(form).blockingGet();

        TestObserver<Form> testMaybe = repository.findByClientAndTemplate(ReferenceType.DOMAIN, FIXED_REF_ID, FIXED_CLI_ID, "MyTemplateId").test();
        testMaybe.awaitTerminalEvent();
        testMaybe.assertNoErrors();
        assertEqualsTo(templateForm, templateForm.getId(), testMaybe);
    }

}

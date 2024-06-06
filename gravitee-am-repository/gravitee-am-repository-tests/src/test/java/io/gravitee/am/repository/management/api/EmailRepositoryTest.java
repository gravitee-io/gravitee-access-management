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

import io.gravitee.am.model.Email;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailRepositoryTest extends AbstractManagementTest {

    public static final String FIXED_REF_ID = "fixedRefId";
    public static final String FIXED_CLI_ID = "fixedClientId";

    @Autowired
    protected EmailRepository repository;

    protected Email buildEmail() {
        Email email = new Email();
        String randomString = UUID.randomUUID().toString();
        email.setClient("client" + randomString);
        email.setContent("content" + randomString);
        email.setFrom("from" + randomString);
        email.setFromName("fromName" + randomString);
        email.setReferenceId("ref" + randomString);
        email.setReferenceType(ReferenceType.DOMAIN);
        email.setSubject("subject" + randomString);
        email.setTemplate("tpl" + randomString);
        email.setCreatedAt(new Date());
        email.setUpdatedAt(new Date());
        email.setExpiresAfter(120000);
        return email;
    }

    @Test
    public void shouldNotFindById() {
        final TestObserver<Email> testObserver = repository.findById("unknownId").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById() {
        Email email = buildEmail();
        Email createdEmail = repository.create(email).blockingGet();

        TestObserver<Email> testObserver = repository.findById(createdEmail.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(email, createdEmail.getId(), testObserver);
    }

    @Test
    public void shouldFindById_withRef() {
        Email email = buildEmail();
        Email createdEmail = repository.create(email).blockingGet();

        TestObserver<Email> testObserver = repository.findById(createdEmail.getReferenceType(), createdEmail.getReferenceId(), createdEmail.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(email, createdEmail.getId(), testObserver);
    }

    @Test
    public void shouldUpdateEmail() {
        Email email = buildEmail();
        Email createdEmail = repository.create(email).blockingGet();

        TestObserver<Email> testObserver = repository.findById(createdEmail.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(email, createdEmail.getId(), testObserver);

        Email updatableEmail = buildEmail();
        updatableEmail.setId(createdEmail.getId());
        TestObserver<Email> updatedEmail = repository.update(updatableEmail).test();
        updatedEmail.awaitDone(10, TimeUnit.SECONDS);
        assertEqualsTo(updatableEmail, createdEmail.getId(), updatedEmail);
    }

    private void assertEqualsTo(Email expectedEmail, String expectedId, TestObserver<Email> testObserver) {
        testObserver.assertValue(e -> e.getId().equals(expectedId));
        testObserver.assertValue(e -> (e.getClient() == null && expectedEmail.getClient() == null) || e.getClient().equals(expectedEmail.getClient()));
        testObserver.assertValue(e -> e.getContent().equals(expectedEmail.getContent()));
        testObserver.assertValue(e -> e.getExpiresAfter() == expectedEmail.getExpiresAfter());
        testObserver.assertValue(e -> e.getFrom().equals(expectedEmail.getFrom()));
        testObserver.assertValue(e -> e.getFromName().equals(expectedEmail.getFromName()));
        testObserver.assertValue(e -> e.getReferenceId().equals(expectedEmail.getReferenceId()));
        testObserver.assertValue(e -> e.getReferenceType().equals(expectedEmail.getReferenceType()));
        testObserver.assertValue(e -> e.getSubject().equals(expectedEmail.getSubject()));
        testObserver.assertValue(e -> e.getTemplate().equals(expectedEmail.getTemplate()));
    }

    @Test
    public void shouldDeleteById() {
        Email email = buildEmail();
        Email createdEmail = repository.create(email).blockingGet();

        TestObserver<Email> testObserver = repository.findById(createdEmail.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(email, createdEmail.getId(), testObserver);

        repository.delete(createdEmail.getId()).blockingAwait();

        testObserver = repository.findById(createdEmail.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindAllEmails() {
        TestSubscriber<Email> testSubscriber = repository.findAll().test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertNoErrors();
        testSubscriber.assertNoValues();

        final int loop = 10;
        List<Email> emails = range(0, loop).mapToObj(__ -> buildEmail())
                .map(email -> repository.create(email).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors().values().get(0))
                .collect(toList());

        var testIdSubscriber = repository.findAll().map(Email::getId).toList().test();

        testIdSubscriber.awaitDone(10, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertValueCount(1)
                .assertValue(result -> {
                    assertThat(result)
                            .hasSize(emails.size())
                            .containsExactlyInAnyOrderElementsOf(emails.stream().map(Email::getId).toList());
                    return true;
                });
    }

    @Test
    public void shouldFindAllByReference() {
        TestSubscriber<Email> testSubscriber = repository.findAll().test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertNoErrors();
        testSubscriber.assertNoValues();

        final int loop = 10;
        List<Email> emails = range(0, loop).mapToObj(__ -> {
            final Email email = buildEmail();
            email.setReferenceId(FIXED_REF_ID);
            return email;
        }).map(email -> repository.create(email).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors().values().get(0))
                .collect(toList());

        // random refId
        range(0, loop).forEach(email -> repository.create(buildEmail()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors());

        var testIdSubscriber = repository.findAll(ReferenceType.DOMAIN, FIXED_REF_ID).map(Email::getId).toList().test();

        testIdSubscriber.awaitDone(10, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertValueCount(1)
                .assertValue(result -> {
                    assertThat(result)
                            .hasSize(emails.size())
                            .containsExactlyInAnyOrderElementsOf(emails.stream().map(Email::getId).toList());
                    return true;
                });
    }

    @Test
    public void shouldFindByClient() {
        TestSubscriber<Email> testSubscriber = repository.findAll().test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertNoErrors();
        testSubscriber.assertNoValues();

        final int loop = 10;
        List<Email> emails = range(0, loop).mapToObj(__ -> {
            final Email email = buildEmail();
            email.setReferenceId(FIXED_REF_ID);
            email.setClient(FIXED_CLI_ID);
            return email;
        }).map(email -> repository.create(email).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors().values().get(0)
        ).collect(toList());

        for (int i = 0; i < loop; i++) {
            final Email email = buildEmail();
            email.setReferenceId(FIXED_REF_ID);
            repository.create(email).blockingGet();
        }

        var testIdSubscriber = repository.findByClient(ReferenceType.DOMAIN, FIXED_REF_ID, FIXED_CLI_ID).map(Email::getId).toList().test();

        testIdSubscriber.awaitDone(10, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertValueCount(1)
                .assertValue(result -> {
                    assertThat(result)
                            .hasSize(emails.size())
                            .containsExactlyInAnyOrderElementsOf(emails.stream().map(Email::getId).toList());
                    return true;
                });
    }

    @Test
    public void shouldFindByTemplate() {
        TestSubscriber<Email> testSubscriber = repository.findAll().test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertNoErrors();
        testSubscriber.assertNoValues();

        final int loop = 10;
        for (int i = 0; i < loop; i++) {
            final Email email = buildEmail();
            email.setReferenceId(FIXED_REF_ID);
            repository.create(email).blockingGet();
        }

        final Email email = buildEmail();
        email.setReferenceId(FIXED_REF_ID);
        email.setTemplate("MyTemplateId");
        email.setClient(null);
        Email templateEmail = repository.create(email).blockingGet();

        TestObserver<Email> testMaybe = repository.findByTemplate(ReferenceType.DOMAIN, FIXED_REF_ID, "MyTemplateId").test();
        testMaybe.awaitDone(10, TimeUnit.SECONDS);
        testMaybe.assertNoErrors();
        assertEqualsTo(templateEmail, templateEmail.getId(), testMaybe);
    }

    @Test
    public void shouldFindByClientAndTemplate() {
        TestSubscriber<Email> testSubscriber = repository.findAll().test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertNoErrors();
        testSubscriber.assertNoValues();

        final int loop = 10;
        for (int i = 0; i < loop; i++) {
            final Email email = buildEmail();
            email.setReferenceId(FIXED_REF_ID);
            email.setClient(FIXED_CLI_ID);
            repository.create(email).blockingGet();
        }

        final Email email = buildEmail();
        email.setReferenceId(FIXED_REF_ID);
        email.setClient(FIXED_CLI_ID);
        email.setTemplate("MyTemplateId");
        Email templateEmail = repository.create(email).blockingGet();

        TestObserver<Email> testMaybe = repository.findByClientAndTemplate(ReferenceType.DOMAIN, FIXED_REF_ID, FIXED_CLI_ID, "MyTemplateId").test();
        testMaybe.awaitDone(10, TimeUnit.SECONDS);
        testMaybe.assertNoErrors();
        assertEqualsTo(templateEmail, templateEmail.getId(), testMaybe);
    }

}

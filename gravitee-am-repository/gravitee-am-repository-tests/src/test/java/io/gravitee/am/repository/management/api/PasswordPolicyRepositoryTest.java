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

import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.util.UUID.randomUUID;
import static junit.framework.TestCase.assertNotNull;

@SuppressWarnings("ALL")
public class PasswordPolicyRepositoryTest extends AbstractManagementTest {
    public static final String REF_ID = randomUUID().toString();

    @Autowired
    private PasswordPolicyRepository repository;

    @Test
    public void shouldCreatePasswordPolicy() {
        var passwordPolicy = buildPasswordPolicy();
        var testObserver = repository.create(passwordPolicy).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(passwordPolicy, testObserver);
    }

    @Test
    public void shouldDeletePasswordPolicy() {
        var created = repository.create(buildPasswordPolicy()).blockingGet();
        assertNotNull(created.getId());
        var testObserver = repository.delete(created.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldUpdatePasswordPolicy() {
        var created = repository.create(buildPasswordPolicy()).blockingGet();
        var toUpdate = buildPasswordPolicy();
        toUpdate.setId(created.getId());
        var updated = repository.update(created).blockingGet();
        var testObserver = repository.findById(created.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(updated, testObserver);
    }

    @Test
    public void shouldFindPasswordPolicyForDomain() {
        int expectedHistoriesForDomain = 3;
        for (int i = 0; i < expectedHistoriesForDomain; i++) {
            PasswordPolicy policy = buildPasswordPolicy();
            repository.create(policy).blockingGet();
        }
        PasswordPolicy otherDomainPolicy = buildPasswordPolicy();
        otherDomainPolicy.setReferenceId(UUID.randomUUID().toString());
        repository.create(otherDomainPolicy).blockingGet();

        var testSubscriber = repository.findByReference(ReferenceType.DOMAIN, REF_ID).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(expectedHistoriesForDomain);

        testSubscriber = repository.findByReference(ReferenceType.DOMAIN, otherDomainPolicy.getReferenceId()).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldDeletePasswordPolicyForDomain() {
        int numberOfPolicies = 5;
        for (int i = 0; i < numberOfPolicies; i++) {
            PasswordPolicy policy = buildPasswordPolicy();
            repository.create(policy).blockingGet();
        }

        var testSubscriber = repository.findByReference(ReferenceType.DOMAIN, REF_ID).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(numberOfPolicies);

        var testObserver = repository.deleteByReference(ReferenceType.DOMAIN, REF_ID).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        testSubscriber = repository.findByReference(ReferenceType.DOMAIN, REF_ID).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(0);
    }

    @Test
    public void shouldFindById() {
        var PasswordPolicy = repository.create(buildPasswordPolicy()).blockingGet();
        var testObserver = repository.findById(PasswordPolicy.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(PasswordPolicy, testObserver);
    }

    private PasswordPolicy buildPasswordPolicy() {
        var passwordPolicy = new PasswordPolicy();
        passwordPolicy.setReferenceId(REF_ID);
        passwordPolicy.setReferenceType(ReferenceType.DOMAIN);
        passwordPolicy.setName("a name");
        passwordPolicy.setCreatedAt(new Date());
        passwordPolicy.setUpdatedAt(new Date());
        passwordPolicy.setOldPasswords((short) (Math.random() * 100));
        passwordPolicy.setExcludePasswordsInDictionary(Boolean.TRUE);
        passwordPolicy.setExcludeUserProfileInfoInPassword(Boolean.FALSE);
        passwordPolicy.setPasswordHistoryEnabled(Boolean.TRUE);
        passwordPolicy.setExpiryDuration(Integer.MAX_VALUE);
        passwordPolicy.setIncludeNumbers(Boolean.FALSE);
        passwordPolicy.setIncludeSpecialCharacters(Boolean.FALSE);
        passwordPolicy.setLettersInMixedCase(Boolean.FALSE);
        passwordPolicy.setMaxConsecutiveLetters((int) (Math.random() * 100));
        passwordPolicy.setMinLength((int) (Math.random() * 100));
        passwordPolicy.setMaxLength((int) (Math.random() * 100));
        passwordPolicy.setDefaultIdpId("idp_id1");
        passwordPolicy.setIdpIds(List.of("idp_id1", "idp_id2"));
        return passwordPolicy;
    }

    private void assertEqualsTo(PasswordPolicy expected, TestObserver<PasswordPolicy> testObserver) {
        testObserver.assertValue(g -> g.getReferenceId().equals(expected.getReferenceId()));
        testObserver.assertValue(g -> g.getReferenceType().equals(expected.getReferenceType()));
        testObserver.assertValue(g -> g.getName().equals(expected.getName()));
        testObserver.assertValue(g -> g.getOldPasswords().equals(expected.getOldPasswords()));
        testObserver.assertValue(g -> g.getExpiryDuration().equals(expected.getExpiryDuration()));
        testObserver.assertValue(g -> g.getExcludePasswordsInDictionary().equals(expected.getExcludePasswordsInDictionary()));
        testObserver.assertValue(g -> g.getExcludeUserProfileInfoInPassword().equals(expected.getExcludeUserProfileInfoInPassword()));
        testObserver.assertValue(g -> g.getMaxLength().equals(expected.getMaxLength()));
        testObserver.assertValue(g -> g.getMinLength().equals(expected.getMinLength()));
        testObserver.assertValue(g -> g.getIncludeNumbers().equals(expected.getIncludeNumbers()));
        testObserver.assertValue(g -> g.getLettersInMixedCase().equals(expected.getLettersInMixedCase()));
        testObserver.assertValue(g -> g.getIncludeSpecialCharacters().equals(expected.getIncludeSpecialCharacters()));
        testObserver.assertValue(g -> g.getMaxConsecutiveLetters().equals(expected.getMaxConsecutiveLetters()));
        testObserver.assertValue(g -> g.getDefaultIdpId().equals(expected.getDefaultIdpId()));
        testObserver.assertValue(g -> g.getIdpIds().equals(expected.getIdpIds()));
    }
}

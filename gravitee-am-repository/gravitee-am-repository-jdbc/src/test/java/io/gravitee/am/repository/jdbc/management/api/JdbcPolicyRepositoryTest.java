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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.model.Policy;
import io.gravitee.am.repository.jdbc.management.AbstractManagementJdbcTest;
import io.gravitee.am.repository.management.api.PolicyRepository;
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
public class JdbcPolicyRepositoryTest extends AbstractManagementJdbcTest {
    @Autowired
    private PolicyRepository repository;

    @Test
    public void shouldCreate() {
        Policy policy = buildPolicy();

        TestObserver<Policy> testObserver = repository.create(policy).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.getId() != null);
        assertEqualsTo(policy, testObserver);
    }

    @Test
    public void shouldFindById() {
        Policy policy = buildPolicy();
        Policy createdPolicy = repository.create(policy).blockingGet();

        TestObserver<Policy> testObserver = repository.findById(createdPolicy.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.getId().equals(createdPolicy.getId()));
        assertEqualsTo(policy, testObserver);
    }

    @Test
    public void shouldUpdate() {
        Policy policy = buildPolicy();
        Policy createdPolicy = repository.create(policy).blockingGet();

        TestObserver<Policy> testObserver = repository.findById(createdPolicy.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.getId().equals(createdPolicy.getId()));
        assertEqualsTo(policy, testObserver);

        Policy updatablePolicy = buildPolicy();
        updatablePolicy.setId(createdPolicy.getId());
        Policy updatedPolicy = repository.update(updatablePolicy).blockingGet();

        testObserver = repository.findById(createdPolicy.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.getId().equals(createdPolicy.getId()));
        assertEqualsTo(updatablePolicy, testObserver);
    }

    @Test
    public void shouldDelete() {
        Policy policy = buildPolicy();
        Policy createdPolicy = repository.create(policy).blockingGet();

        TestObserver<Policy> testObserver = repository.findById(createdPolicy.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.getId().equals(createdPolicy.getId()));

        TestObserver<Void> deleteObserver = repository.delete(createdPolicy.getId()).test();
        deleteObserver.awaitTerminalEvent();
        deleteObserver.assertNoErrors();

        testObserver = repository.findById(createdPolicy.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindAll() {
        final int loop = 10;
        for (int i =0; i < loop; ++i) {
            Policy policy = buildPolicy();
            repository.create(policy).blockingGet();
        }

        TestObserver<List<Policy>> testObserver = repository.findAll().test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.size() == loop);
        testObserver.assertValue( p -> p.stream().map(Policy::getId).distinct().count() == loop);
    }

    @Test
    public void shouldFindByDomain() {
        final int loop = 10;
        final String domain = "fixedDomainId";
        for (int i =0; i < loop; ++i) {
            Policy policy = buildPolicy();
            if (i % 2 == 0) policy.setDomain(domain);
            repository.create(policy).blockingGet();
        }

        TestObserver<List<Policy>> testObserver = repository.findByDomain(domain).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.size() == loop/2);
        testObserver.assertValue( p -> p.stream().map(Policy::getId).distinct().count() == loop/2);
    }

    private void assertEqualsTo(Policy policy, TestObserver<Policy> testObserver) {
        testObserver.assertValue(p -> p.getOrder() == policy.getOrder());
        testObserver.assertValue(p -> p.getName().equals(policy.getName()));
        testObserver.assertValue(p -> p.getType().equals(policy.getType()));
        testObserver.assertValue(p -> p.getClient().equals(policy.getClient()));
        testObserver.assertValue(p -> p.getDomain().equals(policy.getDomain()));
        testObserver.assertValue(p -> p.getConfiguration().equals(policy.getConfiguration()));
        testObserver.assertValue(p -> p.getExtensionPoint().equals(policy.getExtensionPoint()));
        testObserver.assertValue(p -> p.isEnabled() == policy.isEnabled());
    }

    private Policy buildPolicy() {
        Policy policy = new Policy();
        String random = UUID.randomUUID().toString();
        policy.setClient("client"+random);
        policy.setConfiguration("config"+random);
        policy.setDomain("domain"+random);
        policy.setEnabled(true);
        policy.setExtensionPoint(ExtensionPoint.PRE_CONSENT);
        policy.setName("name"+random);
        policy.setOrder(2);
        policy.setType("type"+random);
        policy.setCreatedAt(new Date());
        policy.setUpdatedAt(new Date());
        return policy;
    }
}

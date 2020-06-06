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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.AccessPolicyRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoAccessPolicyRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private AccessPolicyRepository repository;

    private static final String DOMAIN_ID = "domainId";
    private static final String RESOURCE_ID = "resourceId";

    @Override
    public String collectionName() {
        return "access_policies";
    }

    @Test
    public void testFindById() throws TechnicalException {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setName("accessPolicyName");
        AccessPolicy apCreated = repository.create(accessPolicy).blockingGet();

        TestObserver<AccessPolicy> testObserver = repository.findById(apCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(ap -> ap.getName().equals("accessPolicyName"));
    }

    @Test
    public void update() throws TechnicalException {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setName("accessPolicyName");
        AccessPolicy apCreated = repository.create(accessPolicy).blockingGet();

        AccessPolicy toUpdate = new AccessPolicy();
        toUpdate.setId(apCreated.getId());
        toUpdate.setName("accessPolicyUpdatedName");

        TestObserver<AccessPolicy> testObserver = repository.update(toUpdate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(ap -> ap.getName().equals("accessPolicyUpdatedName"));
    }

    @Test
    public void delete() throws TechnicalException {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setName("accessPolicyName");
        AccessPolicy apCreated = repository.create(accessPolicy).blockingGet();

        // fetch resource_set
        TestObserver<Void> testObserver = repository.delete(apCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    @Test
    public void findByDomain() throws TechnicalException {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setName("accessPolicyName");
        accessPolicy.setDomain(DOMAIN_ID);
        repository.create(accessPolicy).blockingGet();

        TestObserver<Page<AccessPolicy>> testObserver = repository.findByDomain(DOMAIN_ID, 0, 10).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(p -> p.getTotalCount() == 1);
    }

    @Test
    public void testFindByDomainAndResource() throws TechnicalException {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setName("accessPolicyName");
        accessPolicy.setDomain(DOMAIN_ID);
        accessPolicy.setResource(RESOURCE_ID);
        repository.create(accessPolicy).blockingGet();

        AccessPolicy accessPolicy2 = new AccessPolicy();
        accessPolicy2.setName("accessPolicyName");
        accessPolicy2.setDomain(DOMAIN_ID);
        accessPolicy2.setResource(RESOURCE_ID);
        repository.create(accessPolicy2).blockingGet();

        TestObserver<List<AccessPolicy>> testObserver = repository.findByDomainAndResource(DOMAIN_ID, RESOURCE_ID).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessPolicies -> accessPolicies.size() == 2);
    }
}

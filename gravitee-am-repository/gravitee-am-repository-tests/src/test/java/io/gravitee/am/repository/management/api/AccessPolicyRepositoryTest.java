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

import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.model.uma.policy.AccessPolicyType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccessPolicyRepositoryTest extends AbstractManagementTest {

    @Autowired
    private AccessPolicyRepository repository;

    private static final String DOMAIN_ID = "domainId";
    private static final String RESOURCE_ID = "resourceId";

    @Test
    public void testFindById() throws TechnicalException {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setName("accessPolicyName");
        accessPolicy.setCondition("Condition");
        accessPolicy.setCreatedAt(new Date());
        accessPolicy.setUpdatedAt(new Date());
        accessPolicy.setDescription("description");
        accessPolicy.setDomain(DOMAIN_ID);
        accessPolicy.setEnabled(true);
        accessPolicy.setOrder(Integer.MAX_VALUE);
        accessPolicy.setResource("resource");
        accessPolicy.setType(AccessPolicyType.GROOVY);
        AccessPolicy apCreated = repository.create(accessPolicy).blockingGet();

        TestObserver<AccessPolicy> testObserver = repository.findById(apCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(ap -> ap.getName().equals(accessPolicy.getName()));
        testObserver.assertValue(ap -> ap.getCondition().equals(accessPolicy.getCondition()));
        testObserver.assertValue(ap -> ap.getDescription().equals(accessPolicy.getDescription()));
        testObserver.assertValue(ap -> ap.getDomain().equals(accessPolicy.getDomain()));
        testObserver.assertValue(ap -> ap.getId().equals(apCreated.getId()));
        testObserver.assertValue(ap -> ap.getResource().equals(accessPolicy.getResource()));
        testObserver.assertValue(ap -> ap.getType().equals(accessPolicy.getType()));
        testObserver.assertValue(ap -> ap.getOrder() == accessPolicy.getOrder());
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
        final String DOMAIN_SINGLE = DOMAIN_ID + "single";
        accessPolicy.setDomain(DOMAIN_SINGLE);
        repository.create(accessPolicy).blockingGet();

        AccessPolicy accessPolicyOtherDomain = new AccessPolicy();
        accessPolicyOtherDomain.setName("accessPolicyName");
        accessPolicyOtherDomain.setDomain(DOMAIN_ID+"-other");
        repository.create(accessPolicyOtherDomain).blockingGet();

        TestObserver<Page<AccessPolicy>> testObserver = repository.findByDomain(DOMAIN_SINGLE, 0, 20).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(p -> p.getTotalCount() == 1);
    }

    @Test
    public void findByDomain_Paging() throws Exception {
        final int totalCount = 10;
        final String DOMAIN10 = DOMAIN_ID + "-10";
        for (int i = 0; i < totalCount; i++) {
            AccessPolicy accessPolicy = new AccessPolicy();
            accessPolicy.setName("accessPolicyName"+i);
            accessPolicy.setDomain(DOMAIN10);
            accessPolicy.setCreatedAt(new Date());
            accessPolicy.setUpdatedAt(new Date());

            repository.create(accessPolicy).blockingGet();
            Thread.sleep(100l);
        }

        AccessPolicy accessPolicyOtherDomain = new AccessPolicy();
        accessPolicyOtherDomain.setName("accessPolicyName");
        accessPolicyOtherDomain.setDomain(DOMAIN_ID+"-other");
        repository.create(accessPolicyOtherDomain).blockingGet();

        // list all in one page
        TestObserver<Page<AccessPolicy>> testObserver = repository.findByDomain(DOMAIN10, 0, totalCount+1).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(p -> p.getTotalCount() == totalCount);
        testObserver.assertValue(p -> p.getCurrentPage() == 0);
        testObserver.assertValue(p -> p.getData().size() == totalCount);

        testObserver = repository.findByDomain(DOMAIN10, 0, totalCount/2).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(p -> p.getTotalCount() == totalCount);
        testObserver.assertValue(p -> p.getCurrentPage() == 0);
        testObserver.assertValue(p -> p.getData().size() == totalCount/2);
        // ordered by updated_at desc so last are returned first
        testObserver.assertValue(p -> p.getData().stream().map(AccessPolicy::getName).filter(name -> name.matches("accessPolicyName[56789]")).count() == totalCount/2);

        testObserver = repository.findByDomain(DOMAIN10, 1, totalCount/2).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(p -> p.getTotalCount() == totalCount);
        testObserver.assertValue(p -> p.getCurrentPage() == 1);
        testObserver.assertValue(p -> p.getData().size() == totalCount/2);
        // ordered by updated_at desc so first are returned last
        testObserver.assertValue(p -> p.getData().stream().map(AccessPolicy::getName).filter(name -> name.matches("accessPolicyName[01234]")).count() == totalCount/2);
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

        AccessPolicy accessPolicy3 = new AccessPolicy();
        accessPolicy3.setName("accessPolicyName");
        accessPolicy3.setDomain(DOMAIN_ID+"-other");
        accessPolicy3.setResource(RESOURCE_ID+"-other");
        repository.create(accessPolicy3).blockingGet();

        TestObserver<List<AccessPolicy>> testObserver = repository.findByDomainAndResource(DOMAIN_ID, RESOURCE_ID).toList().test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessPolicies -> accessPolicies.size() == 2);
    }


    @Test
    public void testFindByResources() throws TechnicalException {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setName("accessPolicyName");
        accessPolicy.setDomain(DOMAIN_ID);
        accessPolicy.setResource(RESOURCE_ID);
        repository.create(accessPolicy).blockingGet();

        AccessPolicy accessPolicy2 = new AccessPolicy();
        accessPolicy2.setName("accessPolicyName");
        accessPolicy2.setDomain(DOMAIN_ID);
        accessPolicy2.setResource(RESOURCE_ID+"2");
        repository.create(accessPolicy2).blockingGet();

        AccessPolicy accessPolicy3 = new AccessPolicy();
        accessPolicy3.setName("accessPolicyName");
        accessPolicy3.setDomain(DOMAIN_ID+"-other");
        accessPolicy3.setResource(RESOURCE_ID+"-other");
        repository.create(accessPolicy3).blockingGet();

        TestObserver<List<AccessPolicy>> testObserver = repository.findByResources(Arrays.asList(RESOURCE_ID, RESOURCE_ID+"2")).toList().test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessPolicies -> accessPolicies.size() == 2);
    }
}

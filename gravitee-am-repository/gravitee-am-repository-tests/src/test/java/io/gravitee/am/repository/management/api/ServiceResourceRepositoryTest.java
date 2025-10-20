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

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ServiceResourceRepositoryTest extends AbstractManagementTest {

    @Autowired
    private ServiceResourceRepository serviceResourceRepository;

    @Test
    public void testFindByDomain() {
        // create res
        ServiceResource resource = buildResource();
        resource.setReferenceId("testDomain");
        ServiceResource resourceCreated = serviceResourceRepository.create(resource).blockingGet();

        // fetch factors
        TestSubscriber<ServiceResource> testDomain = serviceResourceRepository.findByReference(ReferenceType.DOMAIN, "testDomain").test();
        testDomain.awaitDone(10, TimeUnit.SECONDS);

        testDomain.assertComplete();
        testDomain.assertNoErrors();
        testDomain.assertValue(f -> f.getId().equals(resourceCreated.getId()));
        testDomain.assertValue(f -> f.getName().equals(resourceCreated.getName()));
        testDomain.assertValue(f -> f.getConfiguration().equals(resourceCreated.getConfiguration()));
        testDomain.assertValue(f -> f.getReferenceId().equals("testDomain"));
        testDomain.assertValue(f -> f.getReferenceType().equals(resourceCreated.getReferenceType()));
        testDomain.assertValue(f -> f.getType().equals(resourceCreated.getType()));
    }

    @Test
    public void testDeleteByDomain() {
        // create res
        ServiceResource resource = buildResource();
        final String DOMAIN_1 = UUID.randomUUID().toString();
        resource.setReferenceId(DOMAIN_1);
        serviceResourceRepository.create(resource).blockingGet();
        ServiceResource resource2 = buildResource();
        resource2.setReferenceId(DOMAIN_1);
        serviceResourceRepository.create(resource2).blockingGet();
        ServiceResource resourceOtherDomain = buildResource();
        final String DOMAIN_2 = UUID.randomUUID().toString();
        resourceOtherDomain.setReferenceId(DOMAIN_2);
        serviceResourceRepository.create(resourceOtherDomain).blockingGet();

        TestSubscriber<ServiceResource> testSubscriber = serviceResourceRepository.findByReference(ReferenceType.DOMAIN, DOMAIN_1).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(2);

        testSubscriber = serviceResourceRepository.findByReference(ReferenceType.DOMAIN, DOMAIN_2).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(f -> f.getReferenceId().equals(DOMAIN_2));

        // exec delete by domain
        TestObserver<Void> deleteCompletable = serviceResourceRepository.deleteByReference(Reference.domain(DOMAIN_1)).test();
        deleteCompletable.awaitDone(10, TimeUnit.SECONDS);

        deleteCompletable.assertComplete();
        deleteCompletable.assertNoErrors();

        // check that only testDomain entries have been removed
        testSubscriber = serviceResourceRepository.findByReference(ReferenceType.DOMAIN, DOMAIN_1).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertNoValues();

        testSubscriber = serviceResourceRepository.findByReference(ReferenceType.DOMAIN, DOMAIN_2).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(f -> f.getReferenceId().equals(DOMAIN_2));
    }

    private ServiceResource buildResource() {
        ServiceResource resource = new ServiceResource();
        String random = UUID.randomUUID().toString();
        resource.setName("name"+random);
        resource.setConfiguration("{\"config\": \"" + random +"\"}");
        resource.setType("type"+random);
        resource.setReferenceId("ref"+random);
        resource.setReferenceType(ReferenceType.DOMAIN);
        resource.setCreatedAt(new Date());
        resource.setUpdatedAt(new Date());
        return resource;
    }

    @Test
    public void testFindById() {
        // create resource
        ServiceResource resource = buildResource();
        ServiceResource resourceCreated = serviceResourceRepository.create(resource).blockingGet();

        // fetch resource
        TestObserver<ServiceResource> testObserver = serviceResourceRepository.findById(resourceCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(f -> f.getId().equals(resourceCreated.getId()));
        testObserver.assertValue(f -> f.getName().equals(resourceCreated.getName()));
        testObserver.assertValue(f -> f.getConfiguration().equals(resourceCreated.getConfiguration()));
        testObserver.assertValue(f -> f.getReferenceId().equals(resourceCreated.getReferenceId()));
        testObserver.assertValue(f -> f.getReferenceType().equals(resourceCreated.getReferenceType()));
        testObserver.assertValue(f -> f.getType().equals(resourceCreated.getType()));
    }

    @Test
    public void testNotFoundById() {
        var observer = serviceResourceRepository.findById("test").test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void testCreate() {
        ServiceResource resource = buildResource();

        TestObserver<ServiceResource> testObserver = serviceResourceRepository.create(resource).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(f -> f.getId() != null);
        testObserver.assertValue(f -> f.getName().equals(resource.getName()));
        testObserver.assertValue(f -> f.getConfiguration().equals(resource.getConfiguration()));
        testObserver.assertValue(f -> f.getReferenceId().equals(resource.getReferenceId()));
        testObserver.assertValue(f -> f.getReferenceType().equals(resource.getReferenceType()));
        testObserver.assertValue(f -> f.getType().equals(resource.getType()));
    }

    @Test
    public void testUpdate() {
        ServiceResource resource = buildResource();
        ServiceResource resourceCreated = serviceResourceRepository.create(resource).blockingGet();

        ServiceResource updateResource = buildResource();
        updateResource.setId(resourceCreated.getId());
        updateResource.setName("testUpdatedName");

        TestObserver<ServiceResource> testObserver = serviceResourceRepository.update(updateResource).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(f -> f.getId().equals(resourceCreated.getId()));
        testObserver.assertValue(f -> f.getName().equals(updateResource.getName()));
        testObserver.assertValue(f -> f.getConfiguration().equals(updateResource.getConfiguration()));
        testObserver.assertValue(f -> f.getReferenceType().equals(updateResource.getReferenceType()));
        testObserver.assertValue(f -> f.getReferenceId().equals(updateResource.getReferenceId()));
        testObserver.assertValue(f -> f.getType().equals(updateResource.getType()));
    }

    @Test
    public void testDelete() {
        ServiceResource resource = buildResource();
        ServiceResource resourceCreated = serviceResourceRepository.create(resource).blockingGet();

        TestObserver<ServiceResource> testObserver = serviceResourceRepository.findById(resourceCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(f -> f.getName().equals(resourceCreated.getName()));

        TestObserver testObserver1 = serviceResourceRepository.delete(resourceCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        testObserver = serviceResourceRepository.findById(resourceCreated.getId()).test();

        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues();
        testObserver.assertNoErrors();
    }

}

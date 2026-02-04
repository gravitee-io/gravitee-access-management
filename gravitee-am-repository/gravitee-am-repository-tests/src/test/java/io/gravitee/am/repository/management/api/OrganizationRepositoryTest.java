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

import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OrganizationRepositoryTest extends AbstractManagementTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    public void testFindById() {
        Organization organization = new Organization();
        organization.setName("testName");
        organization.setDescription("testDescription");
        organization.setCreatedAt(new Date());
        organization.setUpdatedAt(organization.getUpdatedAt());
        organization.setIdentities(Arrays.asList("ValueIdp1", "ValueIdp2"));
        organization.setDomainRestrictions(Arrays.asList("ValueDom1", "ValueDom2"));
        organization.setHrids(Arrays.asList("Hrid1", "Hrid2"));

        // TODO: find another way to inject data in DB. Avoid to rely on class under test for that.
        Organization organizationCreated = organizationRepository.create(organization).blockingGet();

        TestObserver<Organization> obs = organizationRepository.findById(organizationCreated.getId()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getId().equals(organizationCreated.getId()));
        obs.assertValue(o -> o.getName().equals(organization.getName()));
        obs.assertValue(o -> o.getDescription().equals(organization.getDescription()));
        obs.assertValue(o -> o.getIdentities().containsAll(organization.getIdentities()));
        obs.assertValue(o -> o.getDomainRestrictions().containsAll(organization.getDomainRestrictions()));
        obs.assertValue(o -> o.getHrids().containsAll(organization.getHrids()));
    }

    @Test
    public void testNotFoundById() {
        TestObserver<Organization> testObserver = organizationRepository.findById("unknown").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    @Test
    public void testCreate() {

        Organization organization = new Organization();
        organization.setName("testName");

        TestObserver<Organization> obs = organizationRepository.create(organization).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getName().equals(organization.getName()) && o.getId() != null);
    }

    @Test
    public void testUpdate() {
        Organization organization = new Organization();
        organization.setName("testName");
        organization.setDescription("testDescription");
        organization.setCreatedAt(new Date());
        organization.setUpdatedAt(organization.getUpdatedAt());
        organization.setIdentities(Arrays.asList("ValueIdp1", "ValueIdp2"));
        organization.setDomainRestrictions(Arrays.asList("ValueDom1", "ValueDom2"));
        organization.setHrids(Arrays.asList("Hrid1", "Hrid2"));

        Organization organizationCreated = organizationRepository.create(organization).blockingGet();

        Organization organizationUpdated = new Organization();
        organizationUpdated.setId(organizationCreated.getId());
        organizationUpdated.setName("testNameUpdated");
        organizationUpdated.setIdentities(Arrays.asList("ValueIdp3", "ValueIdp4"));
        organizationUpdated.setDomainRestrictions(Arrays.asList("ValueDom2", "ValueDom3", "ValueDom4"));
        organizationUpdated.setHrids(Arrays.asList("Hrid2", "Hrid3", "Hrid4"));

        TestObserver<Organization> obs = organizationRepository.update(organizationUpdated).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getName().equals(organizationUpdated.getName()) && o.getId().equals(organizationCreated.getId()));
        obs.assertValue(o -> o.getIdentities().containsAll(organizationUpdated.getIdentities()));
        obs.assertValue(o -> o.getDomainRestrictions().containsAll(organizationUpdated.getDomainRestrictions()));
        obs.assertValue(o -> o.getHrids().containsAll(organizationUpdated.getHrids()));
    }

    @Test
    public void testDelete() {
        Organization organization = new Organization();
        organization.setName("testName");
        organization.setDescription("testDescription");
        organization.setCreatedAt(new Date());
        organization.setUpdatedAt(organization.getUpdatedAt());
        organization.setIdentities(Arrays.asList("ValueIdp1", "ValueIdp2"));
        organization.setDomainRestrictions(Arrays.asList("ValueDom1", "ValueDom2"));
        organization.setHrids(Arrays.asList("Hrid1", "Hrid2"));

        Organization organizationCreated = organizationRepository.create(organization).blockingGet();

        assertNotNull(organizationRepository.findById(organizationCreated.getId()).blockingGet());

        TestObserver<Void> obs = organizationRepository.delete(organizationCreated.getId()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoValues();

        assertNull(organizationRepository.findById(organizationCreated.getId()).blockingGet());
    }

    @Test
    public void testFindByHrids() {
        Organization organization = new Organization();
        organization.setName("testName");
        organization.setDescription("testDescription");
        organization.setCreatedAt(new Date());
        organization.setUpdatedAt(organization.getUpdatedAt());
        organization.setIdentities(Arrays.asList("ValueIdp1", "ValueIdp2"));
        organization.setDomainRestrictions(Arrays.asList("ValueDom1", "ValueDom2"));
        organization.setHrids(Arrays.asList("Hrid1", "Hrid2"));

        Organization organization2 = new Organization();
        organization2.setName("testName2");
        organization2.setDescription("testDescription2");
        organization2.setCreatedAt(new Date());
        organization2.setUpdatedAt(organization.getUpdatedAt());
        organization2.setIdentities(Arrays.asList("ValueIdp3", "ValueIdp4"));
        organization2.setDomainRestrictions(Arrays.asList("ValueDom3", "ValueDom4"));
        organization2.setHrids(Arrays.asList("Hrid3", "Hrid4"));

        Organization organizationCreated = organizationRepository.create(organization).blockingGet();
        Organization organizationCreated2 = organizationRepository.create(organization2).blockingGet();

        TestObserver<List<Organization>> obs = organizationRepository.findByHrids(Collections.singletonList("Hrid1")).toList().test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.size() == 1);
        obs.assertValue(o -> o.get(0).getName().equals(organizationCreated.getName()));
    }

    @Test
    public void testFindAllEmpty() {
        TestObserver<List<Organization>> obs = organizationRepository.findAll().toList().test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(List::isEmpty);
    }

    @Test
    public void testFindAll() {
        Organization organization = new Organization();
        organization.setName("testName");

        Organization organization2 = new Organization();
        organization2.setName("testName2");

        Organization organizationCreated = organizationRepository.create(organization).blockingGet();
        Organization organizationCreated2 = organizationRepository.create(organization2).blockingGet();

        TestObserver<List<Organization>> obs = organizationRepository.findAll().toList().test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(items -> items.stream().map(Organization::getId)
                .collect(Collectors.toList())
                .containsAll(Arrays.asList(organizationCreated.getId(), organizationCreated2.getId())));
        obs.assertValue(items -> items.size() == 2);
    }
}
